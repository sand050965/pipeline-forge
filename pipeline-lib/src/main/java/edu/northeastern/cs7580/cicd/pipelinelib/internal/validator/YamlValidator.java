package edu.northeastern.cs7580.cicd.pipelinelib.internal.validator;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser.Position;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationErrorCollector;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code YamlValidator} class validates basic YAML structure and required
 * fields in CI/CD pipeline configuration files. All fundamental structure
 * validation operations are performed through instances of this validator.
 *
 * <p>This validator ensures that pipeline configuration files contain the
 * minimum required structure before detailed validation proceeds. It verifies
 * the presence of essential sections and validates data types for critical
 * fields using position-aware parsing to provide precise error locations.
 *
 * <p>The class {@code YamlValidator} enforces the following rules:
 * <ul>
 *   <li>The {@code pipeline} section must be present</li>
 *   <li>The {@code pipeline} section must be a map/object</li>
 *   <li>The {@code pipeline.name} field must be present and be a String</li>
 *   <li>The {@code pipeline.description} field, if present, must be a String</li>
 * </ul>
 *
 * <p>Here are some examples of validation scenarios:
 * <blockquote><pre>
 *     YamlValidator validator = new YamlValidator();
 *     Map&lt;String, Object&gt; yamlData = ...;
 *     Path configFile = Path.of(".pipelines/default.yaml");
 *     PositionAwareYamlParser parser = new PositionAwareYamlParser();
 *     ValidationErrorCollector errorCollector = new ValidationErrorCollector(configFile);
 *
 *     validator.validateBasicStructure(configFile, yamlData, parser, errorCollector);
 *     errorCollector.throwIfHasErrors();
 * </pre></blockquote>
 *
 * <p>Example valid YAML structure:
 * <blockquote><pre>
 * pipeline:
 *   name: default
 *   description: Default build pipeline
 *
 * stages:
 *   - build
 *   - test
 * </pre></blockquote>
 *
 * <p>Example invalid structure (wrong type for name):
 * <blockquote><pre>
 * pipeline:
 *   name: 123
 *   description: Pipeline description
 * </pre></blockquote>
 * Would collect error: {@code "test.yaml:2:9: ERROR, Wrong type for 'pipeline.name'.
 * Expected String, got integer"}
 *
 * <p>Validation errors are collected rather than thrown immediately, allowing
 * multiple structural issues to be reported in a single validation pass. Errors
 * are added to the provided {@link ValidationErrorCollector} with precise line
 * and column information from the {@link PositionAwareYamlParser}.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to methods in
 * this class will cause a {@link NullPointerException} to be thrown.
 *
 * @implNote This validator is stateless and can be safely used concurrently.
 *     It executes first in the validation chain, providing early detection of
 *     fundamental structural errors. Type checking uses both YAML node tags
 *     (for real file parsing) and Java instanceof checks (for unit tests) to
 *     provide accurate type validation in both contexts.
 * @see StageValidator
 * @see JobValidator
 * @see NeedValidator
 * @see Pipeline
 * @see ValidationException
 * @see ValidationErrorCollector
 * @see PositionAwareYamlParser
 */

@SuppressWarnings("unchecked")
public class YamlValidator {

  /**
   * Validates the basic structure of parsed YAML data.
   *
   * <p>This method performs fundamental structural validation on the parsed
   * YAML configuration, ensuring that required sections exist and have correct
   * types. Validation focuses on the {@code pipeline} section and its required
   * {@code name} field, along with optional {@code description} field type checking.
   *
   * <p>The validation process checks the YAML structure in the following order:
   * <ol>
   *   <li>Verify {@code pipeline} section exists</li>
   *   <li>Verify {@code pipeline} section is a map/object</li>
   *   <li>Verify {@code pipeline.name} field exists</li>
   *   <li>Verify {@code pipeline.name} is not null</li>
   *   <li>Verify {@code pipeline.name} is a String</li>
   *   <li>Verify {@code pipeline.description} is a String (if present)</li>
   *   <li>Verify {@code stages} is a List (if present)</li>
   *   <li>Verify each stage item is a String</li>
   *   <li>Verify job fields have correct types</li>
   * </ol>
   *
   * @param yamlData the parsed YAML data structure to validate
   * @param parser the position-aware parser for error location tracking
   * @param errorCollector the collector for validation errors
   */
  public void validateBasicStructure(
      Map<String, Object> yamlData,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    // Check for pipeline section
    if (!yamlData.containsKey("pipeline")) {
      errorCollector.addError(1, 0, "Missing required 'pipeline' section");
      return;
    }

    Object pipelineObj = yamlData.get("pipeline");
    if (!(pipelineObj instanceof Map)) {
      errorCollector.addError(1, 0, "'pipeline' must be a map/object");
      return;
    }

    Map<String, Object> pipeline = (Map<String, Object>) pipelineObj;

    // Validate pipeline.name
    validatePipelineName(pipeline, parser, errorCollector);

    // Validate pipeline.description (optional)
    validatePipelineDescription(pipeline, parser, errorCollector);

    // Validate stages list (optional)
    validateStages(yamlData, parser, errorCollector);

    // Validate job types
    validateJobTypes(yamlData, parser, errorCollector);
  }

  /**
   * Validates the pipeline.name field.
   *
   * @param pipeline the pipeline map
   * @param parser the position-aware parser
   * @param errorCollector the error collector
   */
  private void validatePipelineName(
      Map<String, Object> pipeline,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    if (!pipeline.containsKey("name")) {
      Position pos = parser.getKeyPosition("pipeline");
      errorCollector.addError(
          pos != null ? pos.getLine() + 1 : 2,
          2,
          "Missing required 'pipeline.name' field"
      );
      return;
    }

    Object nameObj = pipeline.get("name");
    String actualType = parser.getValueType("pipeline.name");

    if (actualType == null) {
      // Unit test path
      if (nameObj == null) {
        Position pos = parser.getValuePosition("pipeline.name");
        errorCollector.addError(pos, "'pipeline.name' cannot be null");
      } else if (!(nameObj instanceof String)) {
        Position pos = parser.getValuePosition("pipeline.name");
        errorCollector.addError(pos,
            "Wrong type of value given for 'pipeline.name' key."
            + " Expected value of type String, given "
                + nameObj.getClass().getSimpleName());
      } else if (((String) nameObj).trim().isEmpty()) {
        Position pos = parser.getValuePosition("pipeline.name");
        errorCollector.addError(pos, "'pipeline.name' cannot be empty");
      }
    } else {
      // Real parsing path
      if ("null".equals(actualType)) {
        Position pos = parser.getValuePosition("pipeline.name");
        errorCollector.addError(pos, "'pipeline.name' cannot be null");
      } else if (!"string".equals(actualType)) {
        Position pos = parser.getValuePosition("pipeline.name");
        errorCollector.addError(pos,
            "Wrong type of value given for 'pipeline.name' key."
            + " Expected value of type String, given "
                + actualType);
      } else if (nameObj instanceof String && ((String) nameObj).trim().isEmpty()) {
        Position pos = parser.getValuePosition("pipeline.name");
        errorCollector.addError(pos, "'pipeline.name' cannot be empty");
      }
    }
  }

  /**
   * Validates the pipeline.description field (optional).
   *
   * @param pipeline the pipeline map
   * @param parser the position-aware parser
   * @param errorCollector the error collector
   */
  private void validatePipelineDescription(
      Map<String, Object> pipeline,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    if (!pipeline.containsKey("description")) {
      return;
    }

    Object descObj = pipeline.get("description");
    String actualType = parser.getValueType("pipeline.description");

    if (actualType == null) {
      // Unit test path
      if (descObj != null && !(descObj instanceof String)) {
        Position pos = parser.getValuePosition("pipeline.description");
        errorCollector.addError(pos,
            "Wrong type of value given for 'pipeline.description' key."
            + " Expected value of type String, given "
                + descObj.getClass().getSimpleName());
      }
    } else {
      // Real parsing path
      if (descObj != null && !"string".equals(actualType) && !"null".equals(actualType)) {
        Position pos = parser.getValuePosition("pipeline.description");
        errorCollector.addError(pos,
            "Wrong type of value given for 'pipeline.description' key."
            + " Expected value of type String, given "
                + actualType);
      }
    }
  }

  /**
   * Validates the stages list (optional).
   *
   * @param yamlData the parsed YAML data
   * @param parser the position-aware parser
   * @param errorCollector the error collector
   */
  private void validateStages(
      Map<String, Object> yamlData,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    if (!yamlData.containsKey("stages")) {
      return;
    }

    String stagesType = parser.getValueType("stages");

    if (stagesType == null) {
      // Unit test path
      Object stagesObj = yamlData.get("stages");
      if (!(stagesObj instanceof List)) {
        Position pos = parser.getValuePosition("stages");
        errorCollector.addError(pos,
            "Wrong type of value given for 'stages' key."
            + " Expected value of type List, given "
                + stagesObj.getClass().getSimpleName());
        return;
      }
    } else {
      // Real parsing path
      if (!"list".equals(stagesType)) {
        Position pos = parser.getValuePosition("stages");
        errorCollector.addError(pos,
            "Wrong type of value given for 'stages' key."
            + " Expected value of type List, given "
                + stagesType);
        return;
      }
    }

    // Validate each stage item is a string
    validateStageItems(parser, errorCollector);
  }

  /**
   * Validates that each stage in the stages list is a string.
   *
   * @param parser the position-aware parser
   * @param errorCollector the error collector
   */
  private void validateStageItems(
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Map<String, String> valueTypes = parser.getValueTypes();

    for (Map.Entry<String, String> entry : valueTypes.entrySet()) {
      String path = entry.getKey();
      String type = entry.getValue();

      // Looking for paths like "stages[0]", "stages[1]", etc.
      if (path.startsWith("stages[") && !path.contains(".")) {
        if (!"string".equals(type)) {
          Position pos = parser.getValuePosition(path);
          errorCollector.addError(pos,
              "Wrong type of value given for stage item."
              + " Expected value of type String, given "
                  + type);
        }
      }
    }
  }

  /**
   * Validates types for all job fields.
   *
   * @param yamlData the parsed YAML data
   * @param parser the position-aware parser
   * @param errorCollector the error collector
   */
  private void validateJobTypes(
      Map<String, Object> yamlData,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    // Find all job names (keys at root level that are not pipeline/stages)
    Set<String> jobNames = new HashSet<>();
    for (String key : yamlData.keySet()) {
      if (!"pipeline".equals(key) && !"stages".equals(key)) {
        jobNames.add(key);
      }
    }

    // Validate job names are strings (not integers)
    validateJobNames(jobNames, parser, errorCollector);

    // Validate each job's fields
    for (String jobName : jobNames) {
      validateJobField(jobName, "stage", "string", parser,
          errorCollector, true);
      validateJobField(jobName, "image", "string", parser,
          errorCollector, true);
      validateJobScriptField(jobName, parser, errorCollector);
      validateJobNeedsField(jobName, parser, errorCollector);
      validateJobField(jobName, "failures", "boolean", parser, errorCollector, false);
    }
  }

  /**
      * Validates that job names are strings, not integers or other types.
      *
      * @param jobNames the set of job names
   * @param parser the position-aware parser
   * @param errorCollector the error collector
   */
  private void validateJobNames(
      Set<String> jobNames,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Map<String, Position> keyPositions = parser.getKeyPositions();

    for (String jobName : jobNames) {
      Position pos = keyPositions.get(jobName);
      if (pos != null) {
        // Check if this job name key was originally an integer in YAML
        // We need to check the original YAML type before SnakeYAML converted it
        // Unfortunately, SnakeYAML converts integer keys to strings during parsing
        // So we need a different approach

        // Check if the job name looks like it was an integer
        if (jobName.matches("^-?\\d+$")) {
          errorCollector.addError(pos,
              "Job name '" + jobName + "' must be a String, not an integer");
        }
      }
    }
  }

  /**
   * Validates a specific job field type.
   *
   * @param jobName the job name
   * @param fieldName the field name (e.g., "stage", "image")
   * @param expectedType the expected type
   * @param parser the position-aware parser
   * @param errorCollector the error collector
   * @param required whether the field is required
   */
  private void validateJobField(
      String jobName,
      String fieldName,
      String expectedType,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector,
      boolean required) {

    Map<String, String> valueTypes = parser.getValueTypes();

    // Search for the field in the job's array structure
    boolean found = false;
    for (Map.Entry<String, String> entry : valueTypes.entrySet()) {
      String path = entry.getKey();
      String type = entry.getValue();

      if (path.startsWith(jobName + "[") && path.endsWith("." + fieldName)) {
        found = true;
        if (!expectedType.equals(type)) {
          Position pos = parser.getValuePosition(path);
          errorCollector.addError(pos,
              "Wrong type of value given for '" + fieldName + "' key."
              + " Expected value of type "
                  + capitalize(expectedType) + ", given " + type);
        }
        break;
      }
    }

    if (!found && required) {
      Position pos = parser.getKeyPosition(jobName);
      errorCollector.addError(pos,
          "Job '" + jobName + "' missing required '" + fieldName + "' field");
    }
  }

  /**
   * Validates job script field (can be string or list).
   *
   * @param jobName the job name
   * @param parser the position-aware parser
   * @param errorCollector the error collector
   */
  private void validateJobScriptField(
      String jobName,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Map<String, String> valueTypes = parser.getValueTypes();

    // Search for script field
    for (Map.Entry<String, String> entry : valueTypes.entrySet()) {
      String path = entry.getKey();
      String type = entry.getValue();

      // Looking for paths like "compile[3].script"
      if (path.startsWith(jobName + "[") && path.endsWith(".script")) {
        // Script can be either string or list
        if (!"string".equals(type) && !"list".equals(type)) {
          Position pos = parser.getValuePosition(path);
          errorCollector.addError(pos,
              "Wrong type of value given for 'script' key."
              + " Expected value of type String or List, given "
                  + type);
        }
        return;
      }
    }

    // Script is required
    Position pos = parser.getKeyPosition(jobName);
    errorCollector.addError(pos,
        "Job '" + jobName + "' missing required 'script' field");
  }

  /**
   * Validates job needs field (must be list if present).
   *
   * @param jobName the job name
   * @param parser the position-aware parser
   * @param errorCollector the error collector
   */
  private void validateJobNeedsField(
      String jobName,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Map<String, String> valueTypes = parser.getValueTypes();

    // Search for needs field (optional)
    for (Map.Entry<String, String> entry : valueTypes.entrySet()) {
      String path = entry.getKey();
      String type = entry.getValue();

      // Looking for paths like "compile[2].needs"
      if (path.startsWith(jobName + "[") && path.endsWith(".needs")) {
        if (!"list".equals(type)) {
          Position pos = parser.getValuePosition(path);
          errorCollector.addError(pos,
              "Wrong type of value given for 'needs' key."
              + " Expected value of type List, given "
                  + type);
        }

        // Also validate each item in the needs list is a string
        validateNeedsListItems(jobName, parser, errorCollector);
        return;
      }
    }
  }

  /**
   * Validates that each item in needs list is a string.
   *
   * @param jobName the job name
   * @param parser the position-aware parser
   * @param errorCollector the error collector
   */
  private void validateNeedsListItems(
      String jobName,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Map<String, String> valueTypes = parser.getValueTypes();

    // Search for items in the needs list
    for (Map.Entry<String, String> entry : valueTypes.entrySet()) {
      String path = entry.getKey();
      String type = entry.getValue();

      // Looking for paths like "compile[2].needs[0]", "compile[2].needs[1]", etc.
      if (path.startsWith(jobName + "[") && path.contains(".needs[")) {
        if (!"string".equals(type)) {
          Position pos = parser.getValuePosition(path);
          errorCollector.addError(pos,
              "Wrong type of value given for needs item."
              + " Expected value of type String, given "
                  + type);
        }
      }
    }
  }

  /**
   * Helper to capitalize first letter.
   */
  private String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }
}
