package edu.northeastern.cs7580.cicd.pipelinelib.internal.service;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser.Position;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.JobValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.NeedValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.StageValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.YamlValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.PipelineMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@code PipelineValidationService} class provides comprehensive validation
 * and parsing services for CI/CD pipeline configuration files. All pipeline
 * validation operations are coordinated through instances of this service.
 *
 * <p>This service orchestrates multiple specialized validators to ensure pipeline
 * configurations meet all structural and semantic requirements. It handles YAML
 * parsing with position tracking, model construction, error collection, and
 * multi-level validation including basic structure, stage rules, job requirements,
 * and dependency constraints.
 *
 * <p>Here are some examples of how this service can be used:
 * <blockquote><pre>
 *     PipelineValidationService service = new PipelineValidationService(
 *         yamlValidator, stageValidator, jobValidator, needValidator
 *     );
 *
 *     Path configFile = Path.of(".pipelines/default.yaml");
 *     Pipeline pipeline = service.validateAndParse(configFile);
 *
 *     Path pipelinesDir = Path.of(".pipelines");
 *     service.validateDirectory(pipelinesDir);
 * </pre></blockquote>
 *
 * <p>When validating a directory, all YAML files are processed.
 * Validation does not fail fast; instead, errors across multiple files
 * (including duplicate pipeline names) are aggregated and reported
 * together in a single {@link ValidationException}.
 *
 * <p>The class {@code PipelineValidationService} coordinates validation through
 * four specialized validators: {@link YamlValidator} for basic YAML structure,
 * {@link StageValidator} for stage rules, {@link JobValidator} for job requirements,
 * and {@link NeedValidator} for dependency constraints. Validation proceeds in
 * order, with each validator collecting errors into a {@link ValidationErrorCollector}
 * before the final exception is thrown.
 *
 * <p>Validation failures are reported using IDE-friendly error messages that
 * include file path, line number, column number, and descriptive text. Multiple
 * errors can be collected and reported together, improving the developer experience
 * by showing all issues at once rather than requiring iterative fixes.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to methods in
 * this class will cause a {@link NullPointerException} to be thrown.
 *
 * @implNote This service is stateless and thread-safe. All validation operations
 *     are performed on the provided input without maintaining any internal state
 *     between invocations. The service uses dependency injection to receive
 *     validator instances, promoting modularity and testability. Position-aware
 *     parsing enables precise error reporting even for deeply nested YAML structures.
 * @see YamlValidator
 * @see StageValidator
 * @see JobValidator
 * @see NeedValidator
 * @see Pipeline
 * @see ValidationException
 * @see ValidationErrorCollector
 * @see PositionAwareYamlParser
 */
@SuppressWarnings("unchecked")
@Slf4j
@RequiredArgsConstructor
public class PipelineValidationService {


  /**
   * Validator for basic YAML structure and required fields.
   *
   * <p>Responsible for validating the fundamental structure of pipeline
   * configuration files, including the presence of the {@code pipeline}
   * section and the correct types for {@code pipeline.name} and
   * {@code pipeline.description} fields. This validator executes first
   * in the validation chain.
   *
   * @see YamlValidator
   * @see #validateFile(Path, boolean)
   */
  private final YamlValidator yamlValidator;

  /**
   * Validator for stage-related rules and constraints.
   *
   * <p>Responsible for validating that at least one stage exists, that
   * stage names are unique, and that no stages are empty. This validator
   * executes after {@link #yamlValidator} in the validation chain.
   *
   * @see StageValidator
   * @see #validateFile(Path, boolean)
   */
  private final StageValidator stageValidator;

  /**
   * Validator for job-related rules and constraints.
   *
   * <p>Responsible for validating that all required fields are present,
   * that jobs reference valid stages, and
   * that script types are correct. This validator executes after
   * {@link #stageValidator} in the validation chain.
   *
   * @see JobValidator
   * @see #validateFile(Path, boolean)
   */
  private final JobValidator jobValidator;

  /**
   * Validator for job dependency rules and constraints.
   *
   * <p>Responsible for validating that {@code needs} lists are non-empty,
   * that referenced jobs exist, and that dependencies are within the same
   * stage. This validator executes last in the validation chain.
   *
   * @see NeedValidator
   * @see #validateFile(Path, boolean)
   */
  private final NeedValidator needValidator;

  /**
   * Validates a pipeline file with uniqueness checking enabled.
   *
   * <p>This is a convenience method that calls {@link #validateFile(Path, boolean)}
   * with checkUniqueness set to true.
   *
   * @param filePath the path to the pipeline configuration file
   * @return a validated Pipeline object
   * @throws ValidationException if validation fails
   */
  public Pipeline validateAndParse(Path filePath) {
    return validateFile(filePath, true);  // Default to true
  }

  /**
   * Validates a single pipeline configuration file with optional uniqueness checking.
   *
   * <p>This method performs comprehensive validation of a pipeline configuration
   * file through multiple stages: YAML parsing with position tracking, basic
   * structure validation, model construction, stage validation, job validation,
   * and dependency validation. Errors are collected throughout the process and
   * reported together at the end.
   *
   * <p>The validation process executes in the following order:
   * <ol>
   *   <li>Parse YAML file with position tracking using {@link PositionAwareYamlParser}</li>
   *   <li>Validate basic YAML structure (pipeline section, name, description)</li>
   *   <li>Build internal Pipeline model from YAML data with error collection</li>
   *   <li>Validate stage rules (existence, uniqueness, non-empty)</li>
   *   <li>Validate job rules (required fields, unique names)</li>
   *   <li>Validate dependency rules (needs references, same-stage constraint, cycles)</li>
   *   <li>Throw aggregated errors if any were collected</li>
   * </ol>
   *
   * <p>Unlike traditional fail-fast validation, this method collects multiple
   * errors before throwing, allowing developers to see all issues at once.
   * Each error includes precise line and column information from the position-aware
   * parser, formatted as: {@code filename:line:column: ERROR, message}
   *
   * <p>Example usage:
   * <blockquote><pre>
   *     Path file = Path.of(".pipelines/release.yaml");
   *     Pipeline pipeline = service.validateFile(file, false);
   * </pre></blockquote>
   *
   * @param filePath the path to the pipeline configuration file to validate
   * @param checkUniqueness check pipeline name uniqueness checking within single-file validation
   * @return a fully validated {@code Pipeline} object representing the configuration
   * @throws ValidationException if validation fails at any stage, containing all
   *     collected errors as a multi-line message. May include YAML parsing errors,
   *     structural violations, or semantic constraint violations.
   * @see #validateAndParse(Path)
   * @see #validateDirectory(Path)
   * @see #buildPipelineModel(Map, PositionAwareYamlParser, ValidationErrorCollector)
   * @see PositionAwareYamlParser
   * @see ValidationErrorCollector
   */
  public Pipeline validateFile(Path filePath, boolean checkUniqueness) {
    log.info("Validating pipeline file: {}", filePath);

    ValidationErrorCollector errorCollector = new ValidationErrorCollector(filePath);
    PositionAwareYamlParser parser = new PositionAwareYamlParser();


    try {
      Map<String, Object> yamlData = parser.parse(filePath);

      if (yamlData.isEmpty()) {
        errorCollector.addError(1, 0, "YAML file is empty or invalid");
        errorCollector.throwIfHasErrors();
      }

      for (PositionAwareYamlParser.DuplicateError error : parser.getDuplicateErrors()) {
        errorCollector.addError(error.getPosition(), error.getMessage());
      }

      yamlValidator.validateBasicStructure(yamlData, parser, errorCollector);

      Pipeline pipeline = buildPipelineModel(yamlData, parser, errorCollector);

      stageValidator.validateStages(pipeline, parser, errorCollector);

      jobValidator.validateJobs(pipeline, parser, errorCollector);

      needValidator.validateNeeds(pipeline, parser, errorCollector);

      if (checkUniqueness) {
        String pipelineName = pipeline.getPipeline().getName();
        if (pipelineName != null && !pipelineName.isEmpty()) {
          checkPipelineNameUniqueness(filePath, pipelineName, errorCollector);
        }
      }

      errorCollector.throwIfHasErrors();

      log.info("Pipeline '{}' validated successfully", pipeline.getPipeline().getName());
      return pipeline;

    } catch (ValidationException e) {
      throw e;
    } catch (org.yaml.snakeyaml.parser.ParserException e) {
      String cleanMessage = extractYamlParserError(e, filePath);
      throw new ValidationException(cleanMessage);
    } catch (org.yaml.snakeyaml.scanner.ScannerException e) {
      String cleanMessage = extractYamlScannerError(e, filePath);
      throw new ValidationException(cleanMessage);
    } catch (ClassCastException e) {
      String cleanMessage = String.format("%s:0:0: ERROR, Invalid YAML structure "
          + "- unexpected type in configuration", filePath);
      throw new ValidationException(cleanMessage);
    } catch (Exception e) {
      // Generic fallback for other exceptions
      String cleanMessage = String.format("%s:0:0: ERROR, %s", filePath, e.getMessage());
      throw new ValidationException(cleanMessage);
    }
  }

  /**
   * Validates all pipeline configuration files in a directory.
   *
   * <p>This method scans the specified directory for YAML files and validates
   * each one, ensuring that all pipelines are structurally valid and that
   * pipeline names are unique within the directory. This is typically used
   * to validate the {@code .pipelines} directory containing all pipeline
   * configurations for a repository.
   *
   * <p>The method performs the following operations:
   * <ol>
   *   <li>Scans the directory for YAML files (.yaml and .yml extensions)</li>
   *   <li>Validates each file using {@link #validateFile(Path, boolean)}</li>
   *   <li>Collects pipeline names and detects duplicates</li>
   *   <li>Aggregates all validation errors across files</li>
   * </ol>
   *
   * <p>Unlike single-file validation, this method does not fail fast.
   * Instead, it validates all YAML files and collects any validation
   * failures or duplicate pipeline name errors before throwing a single
   * {@link ValidationException} containing all detected issues.
   *
   * <p>Only files directly in the specified directory are checked;
   * subdirectories are not recursively scanned.
   *
   * @param directory the directory containing pipeline configuration files
   * @throws ValidationException if no YAML files are found, or if one or more
   *     validation errors or duplicate pipeline names are detected
   * @see #validateFile(Path, boolean)
   * @see #getYamlFilesInDirectory(Path)
   */
  public void validateDirectory(Path directory) {
    List<Path> yamlFiles = new ArrayList<>(getYamlFilesInDirectory(directory));

    if (yamlFiles.isEmpty()) {
      throw new ValidationException("ERROR: No YAML files found in " + directory);
    }

    yamlFiles.sort(Path::compareTo);

    List<String> errors = new ArrayList<>();
    Set<String> pipelineNames = new HashSet<>();

    for (Path yamlFile : yamlFiles) {
      try {
        Pipeline pipeline = validateFile(yamlFile, false);
        String name = pipeline.getPipeline().getName();

        if (!pipelineNames.add(name)) {
          errors.add(yamlFile + ":0:0: ERROR, Duplicate pipeline name '" + name + "'");
        }
      } catch (ValidationException ex) {
        errors.add(ex.getMessage());
      }
    }

    if (!errors.isEmpty()) {
      throw new ValidationException(String.join(System.lineSeparator(), errors));
    }
  }

  /**
   * Constructs a Pipeline model from parsed YAML data with error collection.
   *
   * <p>This method transforms the raw YAML data structure into a strongly-typed
   * {@link Pipeline} object, collecting structural errors during construction
   * rather than throwing immediately. This allows subsequent validators to
   * examine the partially-constructed pipeline and report additional errors.
   *
   * <p>The construction process:
   * <ol>
   *   <li>Validates pipeline section exists and is a map/object</li>
   *   <li>Extracts pipeline metadata (name and description)</li>
   *   <li>Validates and extracts stages list with type checking</li>
   *   <li>Iterates through remaining keys to extract job definitions</li>
   *   <li>Validates job format (must be list of maps)</li>
   *   <li>Validates each job item is a map before processing</li>
   *   <li>Merges job property maps into cohesive configurations</li>
   *   <li>Constructs Job objects using the builder pattern</li>
   *   <li>Assembles complete Pipeline object</li>
   * </ol>
   *
   * <p>Type checking for the pipeline section ensures it is a map:
   * <ul>
   *   <li>{@code pipeline: {name: test}} - Valid map</li>
   *   <li>{@code pipeline:} (null) - Error collected, returns dummy pipeline</li>
   *   <li>{@code pipeline: "test"} - Error collected, returns dummy pipeline</li>
   *   <li>{@code pipeline: [test]} - Error collected, returns dummy pipeline</li>
   *   <li>pipeline omitted - Error collected, returns dummy pipeline</li>
   * </ul>
   *
   * <p>Type checking for the stages field ensures it is a list if present:
   * <ul>
   *   <li>{@code stages: [build, test]} - Valid list</li>
   *   <li>{@code stages:} (null) - Error collected, uses null</li>
   *   <li>{@code stages: "build"} - Error collected, uses null</li>
   *   <li>stages omitted - No error, uses null</li>
   * </ul>
   *
   * <p>Type checking for job definitions ensures proper structure:
   * <ul>
   *   <li>{@code job: [{stage: build}]} - Valid list of maps</li>
   *   <li>{@code job: "value"} - Error collected, job skipped</li>
   *   <li>{@code job: [stage, build]} - Error collected, invalid items skipped</li>
   * </ul>
   *
   * <p>Each job in the YAML is represented as a list of maps, which are merged
   * to create the final job configuration. For example:
   * <blockquote><pre>
   * compile:
   *   - stage: build
   *   - image: gradle:jdk21
   *   - script: gradle build
   * </pre></blockquote>
   * is merged into: {@code {stage: "build", image: "gradle:jdk21", script: "gradle build"}}.
   *
   * <p>If the pipeline section is missing or invalid, a dummy pipeline with empty values
   * is returned to allow validators to continue and report additional errors
   * rather than failing immediately. Jobs with invalid structure are skipped but
   * do not cause the entire build process to fail prematurely.
   *
   * @param yamlData the parsed YAML data structure containing pipeline, stages,
   *     and job definitions
   * @param parser the position-aware parser for tracking error locations
   * @param errorCollector the collector for accumulating validation errors
   * @return a {@code Pipeline} object representing the configuration, which may
   *     contain empty or placeholder values if critical sections are missing or invalid
   * @see Pipeline
   * @see Job
   * @see PipelineMetadata
   * @see #createDummyPipeline()
   */
  private Pipeline buildPipelineModel(
      Map<String, Object> yamlData,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Object pipelineObj = yamlData.get("pipeline");

    if (pipelineObj == null) {
      Position pos = parser.getKeyPosition("pipeline");
      errorCollector.addError(pos != null ? pos : new PositionAwareYamlParser.Position(1, 0),
          "Missing 'pipeline' section");
      return createDummyPipeline();
    }

    if (!(pipelineObj instanceof Map)) {
      Position pos = parser.getValuePosition("pipeline");
      errorCollector.addError(pos != null ? pos : new PositionAwareYamlParser.Position(1, 0),
          "'pipeline' must be a map/object");
      return createDummyPipeline();
    }

    Map<String, Object> pipelineData = (Map<String, Object>) pipelineObj;

    final PipelineMetadata metadata = PipelineMetadata.builder()
        .name((String) pipelineData.get("name"))
        .description((String) pipelineData.getOrDefault("description", ""))
        .build();

    List<String> stages = null;

    if (yamlData.containsKey("stages")) {
      Object stagesObj = yamlData.get("stages");

      if (stagesObj == null) {
        Position pos = parser.getKeyPosition("stages");
        errorCollector.addError(pos, "'stages' has no value");
      } else if (stagesObj instanceof List) {
        stages = (List<String>) stagesObj;
      } else {
        Position pos = parser.getValuePosition("stages");
        errorCollector.addError(pos,
            "'stages' must be a list, got " + stagesObj.getClass().getSimpleName());
      }
    }

    Map<String, Job> jobs = new HashMap<>();
    for (Map.Entry<String, Object> entry : yamlData.entrySet()) {
      String key = entry.getKey();

      if (key.equals("pipeline") || key.equals("stages")) {
        continue;
      }

      if (!(entry.getValue() instanceof List)) {
        Position pos = parser.getKeyPosition(key);
        errorCollector.addError(pos, "Job '" + key + "' must be a list");
        continue;
      }

      List<?> jobList = (List<?>) entry.getValue();
      Map<String, Object> jobData = new HashMap<>();

      for (Object item : jobList) {
        if (!(item instanceof Map)) {
          Position pos = parser.getKeyPosition(key);
          errorCollector.addError(pos, "Job '" + key + "' contains invalid item");
          continue;
        }
        Map<String, Object> itemMap = (Map<String, Object>) item;
        jobData.putAll(itemMap);
      }

      if (!jobData.isEmpty()) {
        boolean failures = Boolean.TRUE.equals(jobData.get("failures"))
            || "true".equals(jobData.get("failures"));
        Job job = Job.builder()
            .name(key)
            .stage((String) jobData.get("stage"))
            .image((String) jobData.get("image"))
            .script(jobData.get("script"))
            .needs((List<String>) jobData.get("needs"))
            .failures(failures)
            .build();

        jobs.put(key, job);
      }
    }

    return Pipeline.builder()
        .pipeline(metadata)
        .stages(stages)
        .jobs(jobs)
        .build();
  }

  /**
   * Creates a dummy Pipeline object with empty values for error recovery.
   *
   * <p>This method returns a minimal, empty Pipeline object that allows validation
   * to continue even when critical sections of the YAML are missing or malformed.
   * By returning a valid object structure rather than failing immediately, subsequent
   * validators can run and report additional errors, giving users a comprehensive
   * view of all issues in a single validation run.
   *
   * <p>The dummy pipeline contains:
   * <ul>
   *   <li>Empty pipeline metadata (empty name and description)</li>
   *   <li>Empty stages list</li>
   *   <li>Empty jobs map</li>
   * </ul>
   *
   * <p>This dummy object is used when:
   * <ul>
   *   <li>The {@code pipeline} section is missing from the YAML</li>
   *   <li>The {@code pipeline} section is not a map/object type</li>
   *   <li>The {@code pipeline} value is null</li>
   * </ul>
   *
   * <p>Example usage within validation:
   * <blockquote><pre>
   *     if (pipelineObj == null) {
   *         errorCollector.addError(pos, "Missing 'pipeline' section");
   *         return createDummyPipeline();  // Allow validation to continue
   *     }
   * </pre></blockquote>
   *
   * @return a Pipeline object with all fields initialized to empty values
   * @see #buildPipelineModel(Map, PositionAwareYamlParser, ValidationErrorCollector)
   */
  private Pipeline createDummyPipeline() {
    return Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("").description("").build())
        .stages(new ArrayList<>())
        .jobs(new HashMap<>())
        .build();
  }

  /**
   * Retrieves all YAML files in the specified directory.
   *
   * <p>This method scans the directory for files with {@code .yaml} or {@code .yml}
   * extensions, returning a list of paths to these files. Only regular files
   * directly in the specified directory are included; subdirectories are not
   * recursively scanned.
   *
   * <p>The scanning depth is limited to 1 level, meaning only immediate children
   * of the directory are examined. This aligns with the requirement that all
   * pipeline configurations reside directly in the {@code .pipelines} folder
   * without nested subdirectories.
   *
   * <p>File extension matching is case-insensitive, so both {@code .YAML} and
   * {@code .yaml} are recognized. Hidden files (those starting with a dot) are
   * included if they have the correct extension.
   *
   * <p>Example usage:
   * <blockquote><pre>
   *     Path dir = Path.of(".pipelines");
   *     List&lt;Path&gt; files = getYamlFilesInDirectory(dir);
   *     // returns [.pipelines/default.yaml, .pipelines/release.yaml, ...]
   * </pre></blockquote>
   *
   * @param directory the directory to scan for YAML files
   * @return a list of paths to YAML files in the directory. Returns an empty
   *     list if no YAML files are found.
   * @throws ValidationException if the directory cannot be accessed or if an
   *     I/O error occurs during scanning
   * @see #validateDirectory(Path)
   */
  private List<Path> getYamlFilesInDirectory(Path directory) {
    try {
      return Files.walk(directory, 1)
          .filter(p -> Files.isRegularFile(p))
          .filter(p -> {
            String name = p.getFileName().toString().toLowerCase();
            return name.endsWith(".yaml") || name.endsWith(".yml");
          })
          .toList();
    } catch (Exception e) {
      throw new ValidationException("ERROR: Failed to list files in directory: " + directory);
    }
  }

  /**
   * Extracts a clean error message from SnakeYAML ParserException.
   *
   * @param e the parser exception
   * @param filePath the file being parsed
   * @return formatted error message with position
   */
  private String extractYamlParserError(org.yaml.snakeyaml.parser.ParserException e,
      Path filePath) {
    if (e.getProblemMark() != null) {
      int line = e.getProblemMark().getLine() + 1;
      int column = e.getProblemMark().getColumn() + 1;
      String problem = e.getProblem() != null ? e.getProblem() : "Invalid YAML syntax";
      return String.format("%s:%d:%d: ERROR, %s", filePath, line, column, problem);
    }
    return String.format("%s:0:0: ERROR, Invalid YAML syntax", filePath);
  }

  /**
   * Extracts a clean error message from SnakeYAML ScannerException.
   *
   * @param e the scanner exception
   * @param filePath the file being scanned
   * @return formatted error message with position
   */
  private String extractYamlScannerError(org.yaml.snakeyaml.scanner.ScannerException e,
      Path filePath) {
    if (e.getProblemMark() != null) {
      int line = e.getProblemMark().getLine() + 1;
      int column = e.getProblemMark().getColumn() + 1;
      String problem = e.getProblem() != null ? e.getProblem() : "Invalid YAML format";
      return String.format("%s:%d:%d: ERROR, %s", filePath, line, column, problem);
    }
    return String.format("%s:0:0: ERROR, Invalid YAML format", filePath);
  }

  /**
   * Checks if a pipeline name is unique within a directory.
   *
   * <p>This method scans the directory for other YAML files and checks if any
   * other pipeline has the same name as the given pipeline. This ensures
   * pipeline name uniqueness within a directory without validating all files.
   *
   * @param filePath the current file being validated
   * @param pipelineName the pipeline name to check for uniqueness
   * @param errorCollector the collector for accumulating validation errors
   */
  private void checkPipelineNameUniqueness(
      Path filePath,
      String pipelineName,
      ValidationErrorCollector errorCollector) {

    Path directory = filePath.getParent();
    if (directory == null) {
      return; // No parent directory, skip check
    }

    List<Path> yamlFiles = getYamlFilesInDirectory(directory);

    for (Path yamlFile : yamlFiles) {
      // Skip the current file
      if (yamlFile.equals(filePath)) {
        continue;
      }

      try {
        PositionAwareYamlParser parser = new PositionAwareYamlParser();
        Map<String, Object> yamlData = parser.parse(yamlFile);

        Map<String, Object> pipelineData = (Map<String, Object>) yamlData.get("pipeline");
        if (pipelineData != null) {
          String otherName = (String) pipelineData.get("name");

          if (pipelineName.equals(otherName)) {
            errorCollector.addError(
                1, 0,
                "Duplicate pipeline name '"
                    + pipelineName
                    + "' (also found in "
                    + yamlFile.getFileName()
                    + ")"
            );
            return;
          }
        }
      } catch (Exception e) {
        // Ignore errors in other files
      }
    }
  }
}
