package edu.northeastern.cs7580.cicd.pipelinelib.internal.validator;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser.Position;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationErrorCollector;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import java.util.Map;

/**
 * The {@code JobValidator} class validates job-related rules and constraints
 * in CI/CD pipeline configurations. All job validation operations are performed
 * through instances of this validator.
 *
 * <p>This validator ensures that jobs meet all structural and semantic requirements
 * including unique naming, presence of required fields, valid stage assignments,
 * and correct script types. Job validation occurs after stage validation in the
 * validation pipeline.
 *
 * <p>The class {@code JobValidator} enforces the following rules:
 * <ul>
 *   <li>Each job must specify a stage, image, and script</li>
 *   <li>Job stages must reference defined stages in the pipeline</li>
 *   <li>Script fields must be either String or List of Strings</li>
 * </ul>
 *
 * <p>Here are some examples of validation scenarios:
 * <blockquote><pre>
 *     JobValidator validator = new JobValidator();
 *     Pipeline pipeline = ...;
 *     Path configFile = Path.of(".pipelines/default.yaml");
 *     PositionAwareYamlParser parser = new PositionAwareYamlParser();
 *     ValidationErrorCollector errorCollector = new ValidationErrorCollector(configFile);
 *
 *     validator.validateJobs(configFile, pipeline, parser, errorCollector);
 *     errorCollector.throwIfHasErrors();
 * </pre></blockquote>
 *
 * <p>Validation errors are collected rather than thrown immediately, allowing
 * multiple job-related issues to be reported in a single validation pass. Errors
 * include precise line and column information from the {@link PositionAwareYamlParser}.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to methods in
 * this class will cause a {@link NullPointerException} to be thrown.
 *
 * @implNote This validator is stateless and can be safely used concurrently.
 *     The duplicate job name check provides defensive programming against
 *     future changes to YAML parsing, though current YAML and Map semantics
 *     prevent duplicate keys at the parsing level. Stage reference validation
 *     ensures jobs are assigned to valid stages defined in the pipeline.
 * @see StageValidator
 * @see NeedValidator
 * @see Pipeline
 * @see Job
 * @see ValidationException
 * @see ValidationErrorCollector
 * @see PositionAwareYamlParser
 */

public class JobValidator {

  /**
   * Validates all job-related rules for the pipeline.
   *
   * <p>This method performs comprehensive validation of job definitions including
   * duplicate name checking and required field verification. Each job must have
   * a unique name and must specify all required fields: stage, image, and script.
   *
   * <p>The validation process checks each job for:
   * <ol>
   *   <li>Presence of required {@code stage} field</li>
   *   <li>Presence of required {@code image} field</li>
   *   <li>Presence of required {@code script} field</li>
   * </ol>
   *
   * <p>All validation errors are collected rather than thrown immediately,
   * allowing multiple job issues to be identified and reported in a single
   * validation pass. Position information points to the job's key in the YAML
   * file for all errors.
   *
   * <p>Example invalid configuration (missing required field):
   * <blockquote><pre>
   * compile:
   *   - stage: build
   *   - image: gradle:jdk21
   *   # Missing required 'script' field
   * </pre></blockquote>
   * Would add error: {@code "config.yaml:7:1: ERROR, Job 'compile' missing
   * required 'script' field"}
   *
   * @param pipeline the pipeline containing all job definitions to validate
   * @param parser the position-aware parser for error location tracking
   * @param errorCollector the collector for validation errors
   * @see Job
   */
  public void validateJobs(
      Pipeline pipeline,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Map<String, Job> jobs = pipeline.getJobs();

    for (Map.Entry<String, Job> entry : jobs.entrySet()) {
      String jobName = entry.getKey();
      Job job = entry.getValue();

      // Check required fields
      if (job.getStage() == null || job.getStage().isEmpty()) {
        Position pos = parser.getKeyPosition(jobName);
        errorCollector.addError(pos, "Job '" + jobName + "' missing required 'stage' field");
      }

      if (job.getImage() == null || job.getImage().isEmpty()) {
        Position pos = parser.getKeyPosition(jobName);
        errorCollector.addError(pos, "Job '" + jobName + "' missing required 'image' field");
      }

      if (job.getScript() == null) {
        Position pos = parser.getKeyPosition(jobName);
        errorCollector.addError(pos, "Job '" + jobName + "' missing required 'script' field");
      }
    }
  }
}
