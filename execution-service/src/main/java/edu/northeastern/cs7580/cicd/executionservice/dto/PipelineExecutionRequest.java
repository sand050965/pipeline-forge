package edu.northeastern.cs7580.cicd.executionservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request object for initiating a pipeline execution.
 *
 * <p>This DTO is received from the API Gateway and contains the relative path
 * to the pipeline YAML file (within the repository), along with Git metadata
 * used to clone the correct version of the repository.
 *
 * <p><b>Request Flow:</b>
 * <ol>
 *   <li>CLI resolves the pipeline YAML file path under .pipelines/</li>
 *   <li>CLI sends the relative file path and Git metadata to the API Gateway</li>
 *   <li>API Gateway forwards to Execution Service</li>
 *   <li>Execution Service clones the repository and reads the YAML from the workspace</li>
 * </ol>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineExecutionRequest {

  /**
   * Name of the pipeline to execute.
   *
   * <p>This corresponds to the {@code pipeline.name} field in the YAML
   * configuration. It's used for tracking, logging, and grouping executions
   * of the same pipeline.
   *
   * <p>Example: {@code "default"}, {@code "release-prod"}
   */
  @NotBlank(message = "Pipeline name is required")
  private String pipelineName;

  /**
   * Relative path to the pipeline YAML file from the repository root.
   *
   * <p>After cloning the repository, the Execution Service reads the YAML
   * file at this path within the cloned workspace.
   *
   * <p>Example: {@code ".pipelines/default.yaml"}
   */
  @NotBlank(message = "Pipeline file path is required")
  private String pipelineFilePath;

  /**
   * Git repository metadata for this execution.
   *
   * <p>Contains information about the source code version being executed,
   * including repository URL, branch name, and commit hash. This metadata
   * is used for:
   * <ul>
   *   <li>Tracking which code version was executed</li>
   *   <li>Reproducing executions at specific commits</li>
   *   <li>Linking execution results to source code changes</li>
   *   <li>Audit trails and compliance reporting</li>
   * </ul>
   */
  @Valid
  @NotNull(message = "Git metadata is required")
  private GitMetadata gitMetadata;
}
