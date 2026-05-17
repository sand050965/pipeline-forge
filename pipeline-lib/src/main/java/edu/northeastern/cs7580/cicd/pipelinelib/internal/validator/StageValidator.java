package edu.northeastern.cs7580.cicd.pipelinelib.internal.validator;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser.Position;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationErrorCollector;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@code StageValidator} class validates stage-related rules and constraints
 * in CI/CD pipeline configurations. All stage validation operations are performed
 * through instances of this validator.
 *
 * <p>This validator ensures that pipeline stages meet all structural requirements
 * including existence, uniqueness, and non-emptiness. Stage validation occurs
 * after basic YAML structure validation and before job validation in the
 * validation pipeline.
 *
 * <p>The class {@code StageValidator} enforces the following rules:
 * <ul>
 *   <li>At least one stage must be defined (explicit or default)</li>
 *   <li>Stage names must be unique within the pipeline</li>
 *   <li>Every defined stage must have at least one job assigned to it</li>
 *   <li>All jobs must reference valid stages from the pipeline definition</li>
 * </ul>
 *
 * <p>Here are some examples of validation scenarios:
 * <blockquote><pre>
 *     StageValidator validator = new StageValidator();
 *     Pipeline pipeline = ...;
 *     Path configFile = Path.of(".pipelines/default.yaml");
 *     PositionAwareYamlParser parser = new PositionAwareYamlParser();
 *     ValidationErrorCollector errorCollector = new ValidationErrorCollector(configFile);
 *
 *     validator.validateStages(configFile, pipeline, parser, errorCollector);
 *     errorCollector.throwIfHasErrors();
 * </pre></blockquote>
 *
 * <p>Example valid stage configuration:
 * <blockquote><pre>
 * stages:
 *   - build
 *   - test
 *   - deploy
 *
 * compile:
 *   - stage: build
 *   ...
 *
 * test-job:
 *   - stage: test
 *   ...
 *
 * deploy-job:
 *   - stage: deploy
 *   ...
 * </pre></blockquote>
 *
 * <p>Example invalid configuration (duplicate stage names):
 * <blockquote><pre>
 * stages:
 *   - build
 *   - test
 *   - build
 * </pre></blockquote>
 * Would collect error: {@code "test.yaml:7:5: ERROR, Duplicate stage name 'build'"}
 *
 * <p>Validation errors are collected rather than thrown immediately, allowing
 * multiple stage-related issues to be reported in a single validation pass.
 * Errors include precise line and column information from the
 * {@link PositionAwareYamlParser}.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to methods in
 * this class will cause a {@link NullPointerException} to be thrown.
 *
 * @implNote This validator is stateless and can be safely used concurrently.
 *     When stages are not explicitly defined, the validator uses default stages
 *     from {@link Pipeline#getStagesOrDefault()}, which returns
 *     {@code ["build", "test", "docs"]}. The empty stage check ensures that
 *     every stage contributes to the pipeline execution.
 * @see YamlValidator
 * @see JobValidator
 * @see Pipeline
 * @see Pipeline#getStagesOrDefault()
 * @see ValidationException
 * @see ValidationErrorCollector
 * @see PositionAwareYamlParser
 */
@Slf4j
public class StageValidator {

  /**
   * Default stages used when no explicit stages are defined in the pipeline.
   *
   * <p>These stages represent a standard CI/CD workflow: build artifacts,
   * run tests, and generate documentation. When a pipeline configuration
   * omits the {@code stages} section, these defaults ensure the pipeline
   * has a valid stage structure for job assignment.
   *
   * <p>The default stages are:
   * <ul>
   *   <li>{@code build} - For compilation and artifact creation</li>
   *   <li>{@code test} - For running test suites</li>
   *   <li>{@code docs} - For documentation generation</li>
   * </ul>
   */
  private static final List<String> DEFAULT_STAGES = List.of("build", "test", "docs");

  /**
   * Validates all stage-related rules for the pipeline.
   *
   * <p>This method coordinates three main validation checks: stage name uniqueness,
   * non-empty stages, and valid job-to-stage references. If no stages are explicitly
   * defined in the pipeline, default stages ({@code ["build", "test", "docs"]}) are
   * used for validation.
   *
   * <p>The validation process executes in the following order:
   * <ol>
   *   <li>Check for duplicate stage names in the stages list</li>
   *   <li>Verify each stage has at least one job assigned to it</li>
   *   <li>Verify all job stage references point to valid stages</li>
   * </ol>
   *
   * <p>All validation errors are collected in the provided error collector rather
   * than throwing immediately, allowing multiple stage issues to be reported
   * together. Each error includes precise position information when available.
   *
   * @param pipeline the pipeline containing stages and jobs to validate
   * @param parser the position-aware parser used to track error locations
   * @param errorCollector the collector that accumulates validation errors
   * @see #validateUniqueStageNames(List, PositionAwareYamlParser, ValidationErrorCollector)
   * @see #validateNoEmptyStages(Pipeline, List, PositionAwareYamlParser, ValidationErrorCollector)
   * @see #validateJobStageReferences(Pipeline, List, PositionAwareYamlParser,
   *     ValidationErrorCollector)
   */
  public void validateStages(
      Pipeline pipeline,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    List<String> stages = pipeline.getStages();

    // If no stages defined, use default stages
    if (stages == null || stages.isEmpty()) {
      stages = DEFAULT_STAGES;
      log.debug("No stages defined, using default stages: {}", DEFAULT_STAGES);
    }

    // Check for duplicate stage names
    validateUniqueStageNames(stages, parser, errorCollector);

    // Check that all stages have at least one job
    validateNoEmptyStages(pipeline, stages, parser, errorCollector);

    // Validate that all jobs reference valid stages
    validateJobStageReferences(pipeline, stages, parser, errorCollector);
  }

  /**
   * Validates that stage names are unique within the pipeline.
   *
   * <p>This method checks the stages list for duplicate entries and reports
   * each duplicate with its exact position in the YAML file. The position
   * reported is that of the duplicate occurrence, not the original.
   *
   * <p>Example invalid configuration:
   * <blockquote><pre>
   * stages:
   *   - build    # Line 2
   *   - test     # Line 3
   *   - build    # Line 4 - ERROR reported here
   * </pre></blockquote>
   * Would add error: {@code "config.yaml:4:5: ERROR, Duplicate stage name 'build'"}
   *
   * @param stages the list of stage names to check for duplicates
   * @param parser the position-aware parser for error location tracking
   * @param errorCollector the collector for validation errors
   */
  private void validateUniqueStageNames(
      List<String> stages,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Set<String> seenStages = new HashSet<>();

    for (int i = 0; i < stages.size(); i++) {
      String stage = stages.get(i);

      if (seenStages.contains(stage)) {
        Position pos = parser.getValuePosition("stages[" + i + "]");
        errorCollector.addError(pos, "Duplicate stage name '" + stage + "'");
      }

      seenStages.add(stage);
    }
  }

  /**
   * Validates that all stages have at least one job assigned.
   *
   * <p>This method counts jobs assigned to each stage and reports any stages
   * that have zero jobs. A stage without jobs would never execute, indicating
   * either a configuration error or an unnecessary stage definition.
   *
   * <p>The job count is determined by examining the {@code stage} field of each
   * job in the pipeline. Stages are checked in order, with position information
   * pointing to the stage name in the stages list.
   *
   * <p>Example invalid configuration:
   * <blockquote><pre>
   * stages:
   *   - build
   *   - test     # No jobs assigned to test
   *   - deploy
   * </pre></blockquote>
   * Would add error: {@code "config.yaml:3:5: ERROR, Stage 'test' has no jobs assigned to it"}
   *
   * @param pipeline the pipeline containing all job definitions
   * @param stages the list of stages to check for emptiness
   * @param parser the position-aware parser for error location tracking
   * @param errorCollector the collector for validation errors
   */
  private void validateNoEmptyStages(
      Pipeline pipeline,
      List<String> stages,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Map<String, Job> jobs = pipeline.getJobs();

    // Count jobs per stage
    Map<String, Integer> jobCountPerStage = new HashMap<>();
    for (String stage : stages) {
      jobCountPerStage.put(stage, 0);
    }

    for (Job job : jobs.values()) {
      String jobStage = job.getStage();
      if (jobStage != null && jobCountPerStage.containsKey(jobStage)) {
        jobCountPerStage.put(jobStage, jobCountPerStage.get(jobStage) + 1);
      }
    }

    // Report empty stages
    for (int i = 0; i < stages.size(); i++) {
      String stage = stages.get(i);
      if (jobCountPerStage.get(stage) == 0) {
        Position pos = parser.getValuePosition("stages[" + i + "]");
        errorCollector.addError(pos,
            "Stage '" + stage + "' has no jobs assigned to it");
      }
    }
  }

  /**
   * Validates that all job stage references point to valid defined stages.
   *
   * <p>This method verifies that each job's {@code stage} field references a
   * stage that exists in the pipeline's stage list. Jobs referencing undefined
   * stages would fail to execute, as they cannot be placed in the execution order.
   *
   * <p>The method attempts to report the position of the stage field within the
   * job definition. If that specific position cannot be determined, it falls back
   * to the position of the job name itself.
   *
   * <p>Example invalid configuration:
   * <blockquote><pre>
   * stages:
   *   - build
   *
   * test-job:
   *   - stage: test    # Error: 'test' not in stages list
   *   - image: alpine
   *   - script: echo test
   * </pre></blockquote>
   * Would add error: {@code "config.yaml:5:12: ERROR, Job 'test-job' references
   * unknown stage 'test'"}
   *
   * @param pipeline the pipeline containing all job definitions
   * @param stages the list of valid stage names
   * @param parser the position-aware parser for error location tracking
   * @param errorCollector the collector for validation errors
   */
  private void validateJobStageReferences(
      Pipeline pipeline,
      List<String> stages,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Set<String> validStages = new HashSet<>(stages);
    Map<String, Job> jobs = pipeline.getJobs();

    for (Map.Entry<String, Job> entry : jobs.entrySet()) {
      String jobName = entry.getKey();
      Job job = entry.getValue();
      String jobStage = job.getStage();

      if (jobStage != null && !validStages.contains(jobStage)) {
        Position pos = parser.getValuePosition(jobName + "[0].stage");
        if (pos == null) {
          pos = parser.getKeyPosition(jobName);
        }
        errorCollector.addError(pos,
            "Job '" + jobName + "' references unknown stage '" + jobStage + "'");
      }
    }
  }
}
