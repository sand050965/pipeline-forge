package edu.northeastern.cs7580.cicd.pipelinelib.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * The {@code Job} class represents a unit of work in a CI/CD pipeline.
 * All job definitions in pipeline configuration files are implemented
 * as instances of this class.
 *
 * <p>Jobs are the fundamental building blocks of a pipeline, encapsulating
 * a set of commands to be executed within a specific stage. Each job runs
 * in an isolated Docker container with its own image and script commands.
 *
 * <p>Here are some examples of how jobs can be used:
 * <blockquote><pre>
 *     Job buildJob = Job.builder()
 *         .name("compile")
 *         .stage("build")
 *         .image("gradle:jdk21")
 *         .script("gradle build")
 *         .build();
 *
 *     Job testJob = Job.builder()
 *         .name("test")
 *         .stage("test")
 *         .image("gradle:jdk21")
 *         .script(List.of("gradle test", "gradle jacocoTestReport"))
 *         .needs(List.of("compile"))
 *         .build();
 * </pre></blockquote>
 *
 * <p>The class {@code Job} includes methods for retrieving script commands
 * in a normalized format, supporting both single-command and multi-command
 * configurations. Jobs can declare dependencies on other jobs within the
 * same stage using the {@link #needs} field.
 *
 * <p>Job instances are typically created by parsing YAML configuration files
 * where they are defined with the following structure:
 * <blockquote><pre>
 * job-name:
 *   - stage: build
 *   - image: alpine:latest
 *   - script: echo "Hello"
 *   - needs: [other-job]
 *   - failures: true
 * </pre></blockquote>
 *
 * <p>Unless otherwise noted, passing a {@code null} value to required
 * fields will result in validation errors during pipeline execution.
 *
 * @implNote Jobs are validated to ensure all required fields are present
 *     and that referenced dependencies exist within the same stage.
 *     The {@link #script} field accepts both String and List types for
 *     flexibility in YAML configuration.
 * @see Pipeline
 * @see PipelineMetadata
 */
@SuppressWarnings("unchecked")
@Data
@Builder
public class Job {

  /**
   * The unique identifier for this job within the pipeline.
   *
   * <p>Job names must be unique within a single pipeline configuration file.
   * The name is used to reference this job in dependency declarations and
   * in execution logs. Related fields include {@link #stage} which defines
   * where this job executes.
   *
   * @see #stage
   * @see #needs
   */
  private String name;

  /**
   * The stage this job belongs to.
   *
   * <p>Must be one of the stages defined in the pipeline's {@code stages} list.
   * Jobs within the same stage may execute in parallel, while stages execute
   * sequentially. The stage determines the execution order of this job relative
   * to other jobs. Related field is {@link #name}.
   *
   * @see Pipeline#getStagesOrDefault()
   * @see #needs
   */
  private String stage;

  /**
   * The Docker image to use for executing this job.
   *
   * <p>Specifies the container image that provides the runtime environment
   * for this job's commands. The image must be available from a Docker registry
   * (e.g., DockerHub). Examples include {@code "gradle:jdk21-corretto"},
   * {@code "alpine:latest"}, or {@code "python:3.11-slim"}.
   *
   * @see #script
   */
  private String image;

  /**
   * The script commands to execute for this job.
   *
   * <p>Can be either a single String command or a List of String commands.
   * When specified as a String, it represents a single command to execute.
   * When specified as a List, each element is executed sequentially.
   * Use {@link #getScriptCommands()} to retrieve the commands in normalized
   * List format regardless of the original type.
   *
   * <p>Examples:
   * <blockquote><pre>
   *     script: "gradle build"
   *     script: ["gradle clean", "gradle build", "gradle test"]
   * </pre></blockquote>
   *
   * @see #getScriptCommands()
   * @see #image
   */
  private Object script;

  /**
   * Whether this job is allowed to fail without failing the entire pipeline.
   *
   * <p>When set to {@code true}, a non-zero exit code from this job will be reported
   * but will not cause the overall pipeline to be marked as failed. This is useful
   * for optional or experimental jobs whose failures should not block downstream
   * stages.
   *
   * <p>Corresponds to the {@code failures} key in YAML configuration:
   * <blockquote><pre>
   *     failures: true   # job failure is tolerated
   *     failures: false  # default; job failure fails the pipeline
   * </pre></blockquote>
   *
   * <p>When the key is absent the value defaults to {@code false}, preserving
   * backward-compatibility with existing pipeline definitions.
   *
   * @see Pipeline
   */
  @Builder.Default
  private boolean failures = false;

  /**
   * List of job names that must complete successfully before this job can start.
   *
   * <p>All referenced jobs must exist and must be in the same {@link #stage}
   * as this job. Dependencies are used to control execution order within a stage.
   * This field is optional; if {@code null} or empty, the job has no dependencies
   * and may execute as soon as its stage begins.
   *
   * <p>Examples:
   * <blockquote><pre>
   *     needs: [unit-tests]
   *     needs: [checkstyle, spotbugs]
   * </pre></blockquote>
   *
   * @implNote Circular dependencies are detected during validation and will
   *     result in a {@code ValidationException}. Dependencies across different
   *     stages are not permitted.
   * @see #stage
   * @see #name
   */
  private List<String> needs;

  /**
   * Converts the script field into a list of commands.
   *
   * <p>This method normalizes the {@link #script} field into a consistent
   * {@code List<String>} format, regardless of whether it was originally
   * specified as a single String or a List. This normalization simplifies
   * command execution logic.
   *
   * <p>Behavior:
   * <ul>
   *   <li>If {@code script} is a {@code String}, returns a single-element list</li>
   *   <li>If {@code script} is a {@code List}, returns it as-is</li>
   *   <li>If {@code script} is {@code null} or invalid type, returns an empty list</li>
   * </ul>
   *
   * <p>Examples:
   * <blockquote><pre>
   *     Job job1 = Job.builder().script("gradle build").build();
   *     job1.getScriptCommands();  // returns ["gradle build"]
   *
   *     Job job2 = Job.builder()
   *         .script(List.of("gradle clean", "gradle build"))
   *         .build();
   *     job2.getScriptCommands();  // returns ["gradle clean", "gradle build"]
   *
   *     Job job3 = Job.builder().script(null).build();
   *     job3.getScriptCommands();  // returns []
   * </pre></blockquote>
   *
   * @return a list of script commands to execute. Returns an empty list if
   *     {@code script} is {@code null} or not a String or List type.
   * @implNote This method performs type checking using {@code instanceof}
   *     rather than casting directly to avoid {@code ClassCastException}.
   *     The returned list is immutable when created from a String using
   *     {@code List.of()}.
   * @see #script
   */
  public List<String> getScriptCommands() {
    if (script instanceof String) {
      return List.of((String) script);
    } else if (script instanceof List) {
      return (List<String>) script;
    }
    return List.of();
  }

  /**
   * Returns an unmodifiable view of the job dependencies.
   *
   * @return an unmodifiable list of job names this job depends on, or {@code null}
   */
  public List<String> getNeeds() {
    return needs == null ? null : Collections.unmodifiableList(needs);
  }

  /**
   * Sets the job dependencies with a defensive copy.
   *
   * @param needs the list of job names this job depends on, or {@code null}
   */
  public void setNeeds(List<String> needs) {
    this.needs = needs == null ? null : new ArrayList<>(needs);
  }
}
