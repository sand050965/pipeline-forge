package edu.northeastern.cs7580.cicd.apigateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing an inbound pipeline execution request from the CLI.
 *
 * <p>This object is received by the API Gateway and forwarded as-is to the
 * Execution Service, which uses it to locate the pipeline YAML file within
 * the cloned repository and run the pipeline against the specified Git snapshot.
 *
 * <p><b>Request flow:</b>
 * <ol>
 *   <li>CLI resolves the pipeline YAML path under {@code .pipelines/} and
 *       reads the current Git state (branch, commit)</li>
 *   <li>CLI sends this request to the API Gateway</li>
 *   <li>API Gateway validates and forwards to the Execution Service</li>
 *   <li>Execution Service clones the repository, resolves
 *       {@code pipelineFilePath} within the workspace, and executes the pipeline</li>
 * </ol>
 *
 * <p>All fields are mandatory. Constraint violations are rejected by the API
 * Gateway before the request reaches the Execution Service.
 *
 * @see GitMetadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineExecutionRequest {

  /**
   * Logical name of the pipeline to execute.
   *
   * <p>Must match the {@code pipeline.name} field declared in the pipeline
   * YAML configuration. Used for execution tracking, run numbering, and
   * grouping historical runs of the same pipeline.
   *
   * <p>Example: {@code "default"}, {@code "release-prod"}
   *
   * <p>Must be non-blank.
   */
  @NotBlank(message = "Pipeline name is required")
  private String pipelineName;

  /**
   * Relative path to the pipeline YAML file from the repository root.
   *
   * <p>After cloning the repository, the Execution Service resolves this
   * path within the workspace to read and validate the pipeline configuration.
   * The path must point to a file under the {@code .pipelines/} directory.
   *
   * <p>Example: {@code ".pipelines/default.yaml"},
   * {@code ".pipelines/release-prod.yaml"}
   *
   * <p>Must be non-blank.
   */
  @NotBlank(message = "Pipeline file path is required")
  private String pipelineFilePath;

  /**
   * Git repository metadata identifying the exact source snapshot to execute.
   *
   * <p>Contains the repository URL, branch name, and commit hash. The
   * Execution Service uses these values to clone the repository and check
   * out the correct snapshot before running any jobs.
   *
   * <p>Must be non-null; all nested fields are validated transitively via
   * {@code @Valid}.
   *
   * @see GitMetadata
   */
  @Valid
  @NotNull(message = "Git metadata is required")
  private GitMetadata gitMetadata;
}