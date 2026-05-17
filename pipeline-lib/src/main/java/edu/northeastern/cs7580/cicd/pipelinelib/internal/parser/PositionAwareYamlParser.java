package edu.northeastern.cs7580.cicd.pipelinelib.internal.parser;

import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.PipelineValidationService;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationErrorCollector;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.Getter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.composer.Composer;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.parser.Parser;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.resolver.Resolver;
import org.yaml.snakeyaml.scanner.Scanner;
import org.yaml.snakeyaml.scanner.ScannerImpl;

/**
 * Parses YAML files while preserving line and column position information
 * for each element in the document structure.
 *
 * <p>This parser uses SnakeYAML's low-level Composer API to extract position
 * data for each YAML node, enabling precise error reporting during validation.
 * Position information is tracked separately for both keys and values, allowing
 * validators to report errors at the exact location where issues occur.
 *
 * <p>The parser maintains three maps during parsing:
 * <ul>
 *   <li>Key positions: Maps dot-notation paths to the positions of keys</li>
 *   <li>Value positions: Maps dot-notation paths to the positions of values</li>
 *   <li>Value types: Maps dot-notation paths to YAML type tags</li>
 * </ul>
 *
 * <p>Path notation uses dots for nesting and brackets for array indices:
 * <blockquote><pre>
 * pipeline:           # path: "pipeline"
 *   name: test        # path: "pipeline.name"
 * stages:             # path: "stages"
 *   - build           # path: "stages[0]"
 *   - test            # path: "stages[1]"
 * compile:            # path: "compile"
 *   - stage: build    # path: "compile[0].stage"
 * </pre></blockquote>
 *
 * <p>Example usage:
 * <blockquote><pre>
 *     PositionAwareYamlParser parser = new PositionAwareYamlParser();
 *     Map&lt;String, Object&gt; data = parser.parse(Path.of("config.yaml"));
 *
 *     Position namePos = parser.getValuePosition("pipeline.name");
 *     String nameType = parser.getValueType("pipeline.name");
 *
 *     System.out.println("Name at line " + namePos.getLine()
 *         + ", column " + namePos.getColumn());
 *     System.out.println("Name type: " + nameType);
 * </pre></blockquote>
 *
 * <p>The parser handles all YAML node types including scalars, sequences (lists),
 * and mappings (objects). Type information is derived from YAML tags, distinguishing
 * between strings, integers, booleans, and other types as they appear in the
 * source document.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to methods in
 * this class will cause a {@link NullPointerException} to be thrown.
 *
 * @implNote This parser is not thread-safe. Each parsing operation creates new
 *     position and type maps, so parser instances should not be reused across
 *     multiple files. The parser uses SnakeYAML's Composer API with the chain:
 *     FileInputStream → InputStreamReader → StreamReader → Scanner → Parser → Composer.
 *     Line and column numbers are 1-indexed to match standard editor conventions.
 * @see ValidationErrorCollector
 * @see PipelineValidationService
 */
public class PositionAwareYamlParser {

  /**
   * Maps YAML element paths to their key positions in the source file.
   *
   * <p>Keys in this map use dot notation for nested structures
   * (e.g., "pipeline.name", "compile.stage"). Values are Position objects
   * containing 1-indexed line and column numbers where each key appears.
   *
   * <p>This map is populated during parsing and queried by validators to
   * report errors at the exact location of problematic keys.
   */
  @Getter
  private final Map<String, Position> keyPositions = new HashMap<>();

  /**
   * Maps YAML element paths to their value positions in the source file.
   *
   * <p>Keys in this map use dot notation with bracket indices for arrays
   * (e.g., "pipeline.name", "stages[0]"). Values are Position objects
   * containing 1-indexed line and column numbers where each value begins.
   *
   * <p>This map is populated during parsing and queried by validators to
   * report errors at the exact location of problematic values.
   */
  @Getter
  private final Map<String, Position> valuePositions = new HashMap<>();

  /**
   * Maps YAML element paths to their semantic types.
   *
   * <p>Keys in this map use the same notation as position maps. Values are
   * lowercase strings indicating YAML types: "string", "integer", "float",
   * "boolean", "null", "list", or "map".
   *
   * <p>Type information is extracted from YAML tags during parsing, allowing
   * validators to detect type mismatches (e.g., integer where string expected)
   * even when SnakeYAML might auto-convert the value.
   */
  @Getter
  private final Map<String, String> valueTypes = new HashMap<>();

  /**
   * Tracks all job names encountered across the entire pipeline during parsing.
   *
   * <p>This set accumulates job names from all stages to enforce the uniqueness
   * constraint that no two jobs in a pipeline can share the same name, regardless
   * of which stage they belong to. The set is populated as the parser encounters
   * each job definition and is cleared before each new parse operation.
   *
   * <p>When a duplicate job name is detected, an error is added to
   * {@link #duplicateErrors} with the position of the duplicate occurrence.
   *
   * @see #duplicateErrors
   * @see #clearErrors()
   */
  @Getter
  private Set<String> allJobNames = new HashSet<>();

  /**
   * Collects duplicate job name errors discovered during parsing.
   *
   * <p>Each error contains the position in the source file where a duplicate
   * job name was found, along with a descriptive error message. These errors
   * are detected during sequence parsing when processing job lists within stages.
   *
   * <p>This list is populated during parsing and should be checked by validators
   * after parsing completes. The list is cleared before each new parse operation.
   *
   * @see DuplicateError
   * @see #getDuplicateErrors()
   * @see #clearErrors()
   */
  @Getter
  private List<DuplicateError> duplicateErrors = new ArrayList<>();

  /**
   * Represents a duplicate job name error found during YAML parsing.
   *
   * <p>This class encapsulates the position where a duplicate job name was
   * encountered and a human-readable error message describing the problem.
   * Instances are created when the parser detects multiple jobs with the
   * same name within a pipeline.
   *
   * <p>Example error message: "Duplicate job name 'compile'"
   *
   * @see PositionAwareYamlParser#duplicateErrors
   */
  @Data
  public static class DuplicateError {
    /**
     * The position in the source file where the duplicate job name was found.
     * Line and column numbers are 1-indexed to match editor conventions.
     */
    private final Position position;

    /**
     * A descriptive error message indicating which job name is duplicated.
     * Format: "Duplicate job name '<job-name>'"
     */
    private final String message;
  }

  /**
   * Clears all duplicate tracking state before a new parse operation.
   *
   * <p>This method resets both the duplicate error list and the job name set,
   * ensuring that state from previous parse operations does not affect subsequent
   * parses. This is essential when reusing a parser instance for multiple files.
   *
   * <p>This method should be called at the beginning of each parse operation
   * to ensure clean state. If a new parser instance is created for each file,
   * calling this method is not strictly necessary but is good practice.
   *
   * @see #allJobNames
   * @see #duplicateErrors
   */
  public void clearErrors() {
    duplicateErrors.clear();
    allJobNames.clear();
  }

  /**
   * Parses a YAML file and returns the data with position tracking.
   *
   * <p>This method reads and parses a YAML configuration file using SnakeYAML's
   * low-level Composer API, which preserves position information for each element.
   * As the YAML is parsed, this method builds three tracking maps: key positions,
   * value positions, and value types.
   *
   * <p>The parsing chain uses the following SnakeYAML components:
   * <ol>
   *   <li>FileInputStream reads the file bytes</li>
   *   <li>InputStreamReader converts bytes to characters (UTF-8)</li>
   *   <li>StreamReader provides character stream to SnakeYAML</li>
   *   <li>Scanner tokenizes the YAML content</li>
   *   <li>Parser converts tokens to events</li>
   *   <li>Composer builds the node tree with position marks</li>
   * </ol>
   *
   * <p>Position information is extracted from each node's start mark and converted
   * to 1-indexed line and column numbers to match standard editor conventions.
   *
   * <p>If the file is empty or contains only whitespace, an empty map is returned.
   * If the root element is not a mapping (object), an empty map is returned rather
   * than throwing an exception, allowing validators to report the structural issue.
   *
   * <p>Example usage:
   * <blockquote><pre>
   *     PositionAwareYamlParser parser = new PositionAwareYamlParser();
   *     Map&lt;String, Object&gt; data = parser.parse(Path.of("config.yaml"));
   *
   *     Position pos = parser.getValuePosition("pipeline.name");
   *     String type = parser.getValueType("pipeline.name");
   * </pre></blockquote>
   *
   * @param filePath the path to the YAML file to parse
   * @return a map containing the parsed YAML data, with top-level keys
   *     corresponding to YAML sections. Returns an empty map if the file
   *     is empty or the root is not a mapping.
   * @throws IOException if the file cannot be read or a low-level I/O error occurs
   * @see #parseMapping(MappingNode, String)
   * @see #getKeyPosition(String)
   * @see #getValuePosition(String)
   * @see #getValueType(String)
   */
  public Map<String, Object> parse(Path filePath) throws IOException {
    try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
      StreamReader reader = new StreamReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
      LoaderOptions loaderOptions = new LoaderOptions();
      Scanner scanner = new ScannerImpl(reader, loaderOptions);
      Parser parser = new ParserImpl(scanner);
      Composer composer = new Composer(parser, new Resolver(), loaderOptions);


      Node rootNode = composer.getSingleNode();

      if (rootNode == null) {
        return new HashMap<>();
      }

      if (!(rootNode instanceof MappingNode)) {
        return new HashMap<>();
      }

      return parseMapping((MappingNode) rootNode, "");
    }
  }

  /**
   * Recursively parses a YAML mapping node and tracks positions.
   *
   * <p>This method processes each key-value pair in a YAML mapping (object),
   * storing position information and type data for both keys and values.
   * The method builds a hierarchical path using dot notation to uniquely
   * identify each element in the YAML structure.
   *
   * <p>At the root level, this method also performs duplicate job name detection.
   * Job definitions are identified as top-level keys (excluding "pipeline" and
   * "stages") whose values are sequences. When a duplicate job name is found,
   * an error is recorded with the position of the duplicate occurrence.
   *
   * <p>For each mapping entry, this method:
   * <ol>
   *   <li>Extracts the key name from the key node</li>
   *   <li>Builds the full path (e.g., "pipeline.name")</li>
   *   <li>Records the key's position (line and column)</li>
   *   <li>Records the value's position (line and column)</li>
   *   <li>Determines and records the value's YAML type</li>
   *   <li>For root-level job definitions, checks for duplicate names</li>
   *   <li>Recursively parses the value node</li>
   * </ol>
   *
   * <p>Path construction uses dot notation for nested maps. For example:
   * <blockquote><pre>
   * pipeline:           # path: "pipeline"
   *   name: test        # path: "pipeline.name"
   *   config:           # path: "pipeline.config"
   *     timeout: 30     # path: "pipeline.config.timeout"
   * </pre></blockquote>
   *
   * <p>Job name duplicate detection example:
   * <blockquote><pre>
   * compile:            # First occurrence - OK
   *   - stage: build
   * compile:            # Duplicate - error recorded
   *   - stage: test
   * </pre></blockquote>
   *
   * <p>Non-scalar keys (complex objects as keys) are skipped as they are not
   * supported in standard pipeline configurations.
   *
   * @param mappingNode the YAML mapping node to parse
   * @param prefix the parent path prefix for building hierarchical paths,
   *     empty string for root-level mappings
   * @return a LinkedHashMap preserving the order of keys as they appear in
   *     the YAML file, containing the parsed mapping data
   * @see #parseNode(Node, String)
   * @see #getYamlType(Node)
   * @see #duplicateErrors
   * @see #allJobNames
   */
  private Map<String, Object> parseMapping(MappingNode mappingNode, String prefix) {
    Map<String, Object> result = new LinkedHashMap<>();

    for (NodeTuple tuple : mappingNode.getValue()) {
      Node keyNode = tuple.getKeyNode();
      Node valueNode = tuple.getValueNode();

      if (!(keyNode instanceof ScalarNode)) {
        continue;
      }

      String key = ((ScalarNode) keyNode).getValue();
      String fullPath = prefix.isEmpty() ? key : prefix + "." + key;

      // Store key position
      keyPositions.put(fullPath, new Position(
          keyNode.getStartMark().getLine() + 1,
          keyNode.getStartMark().getColumn() + 1
      ));

      // Store value position
      valuePositions.put(fullPath, new Position(
          valueNode.getStartMark().getLine() + 1,
          valueNode.getStartMark().getColumn() + 1
      ));

      // Store the actual YAML type
      String yamlType = getYamlType(valueNode);
      valueTypes.put(fullPath, yamlType);

      // Check for duplicate job names at the root level
      if (prefix.isEmpty() && !key.equals("pipeline") && !key.equals("stages")
          && valueNode instanceof SequenceNode) {
        if (allJobNames.contains(key)) {
          Position pos = new Position(
              keyNode.getStartMark().getLine() + 1,
              keyNode.getStartMark().getColumn() + 1
          );
          duplicateErrors.add(new DuplicateError(
              pos,
              "Duplicate job name '" + key + "'"
          ));
        }
        allJobNames.add(key);
      }

      // Parse the value
      Object value = parseNode(valueNode, fullPath);
      result.put(key, value);
    }

    return result;
  }

  /**
   * Parses a YAML node and returns its value.
   *
   * <p>This method dispatches to specialized parsing methods based on the node
   * type. YAML supports three fundamental node types: scalar (single values),
   * sequence (lists), and mapping (objects).
   *
   * <p>Node type handling:
   * <ul>
   *   <li>ScalarNode - Returns the string value directly</li>
   *   <li>SequenceNode - Delegates to {@link #parseSequence(SequenceNode, String)}</li>
   *   <li>MappingNode - Delegates to {@link #parseMapping(MappingNode, String)}</li>
   *   <li>Unknown types - Returns {@code null}</li>
   * </ul>
   *
   * <p>All scalar values are returned as strings, preserving the YAML representation.
   * Type information is tracked separately in the {@code valueTypes} map using the
   * node's tag to distinguish between strings, integers, booleans, etc.
   *
   * @param node the YAML node to parse
   * @param path the current path in dot notation for position tracking
   * @return the parsed value: String for scalars, List for sequences,
   *     Map for mappings, or {@code null} for unknown node types
   * @see #parseMapping(MappingNode, String)
   * @see #parseSequence(SequenceNode, String)
   */
  private Object parseNode(Node node, String path) {
    if (node instanceof ScalarNode) {
      return ((ScalarNode) node).getValue();
    } else if (node instanceof SequenceNode) {
      return parseSequence((SequenceNode) node, path);
    } else if (node instanceof MappingNode) {
      return parseMapping((MappingNode) node, path);
    }
    return null;
  }

  /**
   * Parses a YAML sequence (list) node and tracks item positions.
   *
   * <p>This method processes each item in a YAML sequence, storing position
   * and type information for array elements. Array indices are appended to
   * the path using bracket notation to create unique identifiers for each item.
   *
   * <p>For each sequence item, this method:
   * <ol>
   *   <li>Constructs the indexed path (e.g., "stages[0]")</li>
   *   <li>Records the item's position</li>
   *   <li>Determines and records the item's YAML type</li>
   *   <li>Recursively parses the item node</li>
   * </ol>
   *
   * <p>Path construction for array items uses bracket notation. For example:
   * <blockquote><pre>
   * stages:             # path: "stages"
   *   - build           # path: "stages[0]"
   *   - test            # path: "stages[1]"
   *   - deploy          # path: "stages[2]"
   * </pre></blockquote>
   *
   * <p>This allows validators to report errors at specific array indices,
   * helping developers identify which list item contains the problem.
   *
   * @param sequenceNode the YAML sequence node to parse
   * @param path the current path in dot notation for building indexed paths
   * @return a list containing the parsed sequence items in order
   * @see #parseNode(Node, String)
   * @see #getYamlType(Node)
   */
  private List<Object> parseSequence(SequenceNode sequenceNode, String path) {
    List<Object> result = new ArrayList<>();
    int index = 0;

    for (Node itemNode : sequenceNode.getValue()) {
      String indexPath = path + "[" + index + "]";

      // Store position for list items
      valuePositions.put(indexPath, new Position(
          itemNode.getStartMark().getLine() + 1,
          itemNode.getStartMark().getColumn() + 1
      ));

      String yamlType = getYamlType(itemNode);
      valueTypes.put(indexPath, yamlType);

      result.add(parseNode(itemNode, indexPath));
      index++;
    }

    return result;
  }

  /**
   * Gets the position of a key in the YAML file.
   *
   * <p>This method retrieves the line and column position where a specific
   * key appears in the YAML source. The position refers to the start of the
   * key name, not the colon or value.
   *
   * <p>Path format uses dot notation for nested structures:
   * <blockquote><pre>
   * pipeline:           # key path: "pipeline" → line 1
   *   name: test        # key path: "pipeline.name" → line 2
   * </pre></blockquote>
   *
   * <p>Returns {@code null} if the path was not encountered during parsing,
   * which typically occurs when using manually constructed test data.
   *
   * @param path the dot-separated path to the key (e.g., "pipeline.name")
   * @return the Position containing 1-indexed line and column numbers,
   *     or {@code null} if the path was not found
   * @see Position
   * @see #getValuePosition(String)
   */
  public Position getKeyPosition(String path) {
    return keyPositions.get(path);
  }

  /**
   * Gets the position of a value in the YAML file.
   *
   * <p>This method retrieves the line and column position where a specific
   * value begins in the YAML source. For scalar values, this is the start
   * of the value text. For sequences and mappings, this is the start of
   * the opening bracket or first element.
   *
   * <p>Path format uses dot notation for nested structures and brackets for
   * array indices:
   * <blockquote><pre>
   * pipeline:
   *   name: test        # value path: "pipeline.name" → line 2, col 9
   * stages:
   *   - build           # value path: "stages[0]" → line 4, col 5
   * </pre></blockquote>
   *
   * <p>Returns {@code null} if the path was not encountered during parsing.
   *
   * @param path the dot-separated path to the value, with array indices
   *     in brackets (e.g., "stages[0]" or "job.needs[1]")
   * @return the Position containing 1-indexed line and column numbers,
   *     or {@code null} if the path was not found
   * @see Position
   * @see #getKeyPosition(String)
   */
  public Position getValuePosition(String path) {
    return valuePositions.get(path);
  }

  /**
   * Determines the actual YAML type from a node's tag.
   *
   * <p>This method examines the YAML tag associated with a node to determine
   * its semantic type as defined in the YAML specification. This allows
   * validators to distinguish between different scalar representations (e.g.,
   * the string {@code "123"} versus the integer {@code 123}).
   *
   * <p>Supported type mappings:
   * <ul>
   *   <li>Tag.STR → "string"</li>
   *   <li>Tag.INT → "integer"</li>
   *   <li>Tag.FLOAT → "float"</li>
   *   <li>Tag.BOOL → "boolean"</li>
   *   <li>Tag.NULL → "null"</li>
   *   <li>SequenceNode → "list"</li>
   *   <li>MappingNode → "map"</li>
   * </ul>
   *
   * <p>For unknown scalar tags, the method defaults to "string" to provide
   * graceful handling of custom or unusual YAML constructs.
   *
   * <p>Example type detection:
   * <blockquote><pre>
   * name: test      # Tag.STR → "string"
   * count: 123      # Tag.INT → "integer"
   * enabled: true   # Tag.BOOL → "boolean"
   * ratio: 3.14     # Tag.FLOAT → "float"
   * </pre></blockquote>
   *
   * @param node the YAML node whose type should be determined
   * @return a lowercase string indicating the YAML type: "string", "integer",
   *     "float", "boolean", "null", "list", "map", or "unknown"
   */
  private String getYamlType(Node node) {
    if (node instanceof ScalarNode) {
      ScalarNode scalarNode = (ScalarNode) node;
      Tag tag = scalarNode.getTag();

      // Check the tag to determine actual type
      if (tag.equals(Tag.STR)) {
        return "string";
      } else if (tag.equals(Tag.INT)) {
        return "integer";
      } else if (tag.equals(Tag.FLOAT)) {
        return "float";
      } else if (tag.equals(Tag.BOOL)) {
        return "boolean";
      } else if (tag.equals(Tag.NULL)) {
        return "null";
      } else {
        return "string"; // Default to string for unknown scalar types
      }
    } else if (node instanceof SequenceNode) {
      return "list";
    } else if (node instanceof MappingNode) {
      return "map";
    }
    return "unknown";
  }

  /**
   * Gets the YAML type of a value at the given path.
   *
   * <p>This method returns the actual YAML type tag for a value as it appears
   * in the source file, allowing validators to distinguish between different
   * scalar representations. For example, {@code 123} (unquoted) has type
   * "integer", while {@code "123"} (quoted) has type "string".
   *
   * <p>Type information is determined during parsing by examining each node's
   * YAML tag, which indicates the semantic type according to the YAML specification.
   *
   * <p>Possible return values include:
   * <ul>
   *   <li>{@code "string"} - For string values</li>
   *   <li>{@code "integer"} - For integer values</li>
   *   <li>{@code "float"} - For floating-point values</li>
   *   <li>{@code "boolean"} - For boolean values (true/false)</li>
   *   <li>{@code "null"} - For null/empty values</li>
   *   <li>{@code "list"} - For sequence/array values</li>
   *   <li>{@code "map"} - For mapping/object values</li>
   * </ul>
   *
   * <p>Returns {@code null} if the path was not encountered during parsing,
   * which typically occurs when using manually constructed test data rather
   * than parsing actual YAML files. Validators should handle {@code null}
   * by falling back to Java {@code instanceof} checks.
   *
   * @param path the dot-separated path to the value, with array indices
   *     in brackets (e.g., "pipeline.name" or "stages[0]")
   * @return the YAML type as a lowercase string, or {@code null} if the
   *     path was not found during parsing
   * @see #getYamlType(Node)
   */
  public String getValueType(String path) {
    return valueTypes.get(path);
  }

  /**
   * Represents a position in a YAML file.
   *
   * <p>Position objects contain 1-indexed line and column numbers that
   * correspond to standard editor and IDE conventions. These positions
   * are extracted from SnakeYAML's Mark objects during parsing.
   *
   * <p>Line and column numbering starts at 1, with column 1 being the
   * first character on a line. A value of 0 for either field indicates
   * the position is unknown or not applicable.
   */
  @Getter
  public static class Position {
    /**
     * The 1-indexed line number in the YAML file.
     */
    private final int line;

    /**
     * The 1-indexed column number in the YAML file.
     */
    private final int column;

    /**
     * Constructs a Position with the specified line and column.
     *
     * @param line   the 1-indexed line number
     * @param column the 1-indexed column number
     */
    public Position(int line, int column) {
      this.line = line;
      this.column = column;
    }
  }
}