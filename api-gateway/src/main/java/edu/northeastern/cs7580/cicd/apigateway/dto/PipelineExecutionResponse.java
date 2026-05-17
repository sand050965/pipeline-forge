package edu.northeastern.cs7580.cicd.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the result of a pipeline execution request returned to the CLI.
 *
 * <p>This object is returned by the API Gateway after the Execution Service
 * finishes processing the pipeline. It contains the final execution outcome
 * along with identifiers that can be used to query detailed history via the
 * Report Service.
 *
 * <p><b>Status values:</b>
 * <ul>
 *   <li>{@code SUCCESS} — all jobs completed with exit code 0</li>
 *   <li>{@code FAILED} — one or more jobs exited with a non-zero code,
 *       or an internal error occurred during execution</li>
 *   <li>{@code VALIDATION_FAILED} — the pipeline YAML configuration is invalid;
 *       no jobs were executed</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineExecutionResponse {

  /**
   * Unique identifier assigned to this execution by the Execution Service.
   *
   * <p>A UUID generated at the start of each execution request. Can be used
   * to correlate log entries across services for a single execution.
   *
   * <p>Example: {@code "a1b2c3d4-e5f6-7890-abcd-ef1234567890"}
   *
   * <p>May be {@code null} if the request was rejected before an execution
   * record was created.
   */
  private String executionId;

  /**
   * Logical name of the pipeline that was executed.
   *
   * <p>Corresponds to the {@code pipeline.name} field in the YAML configuration.
   *
   * <p>Example: {@code "default"}, {@code "release-prod"}
   */
  private String pipelineName;

  /**
   * Sequential run counter for this pipeline, scoped per pipeline.
   *
   * <p>Assigned automatically by a PostgreSQL trigger on INSERT and increments
   * independently per pipeline. For example, the third execution of a given
   * pipeline will have {@code runNumber=3} regardless of how many other
   * pipelines exist.
   *
   * <p>Returns {@code 0} if the execution failed before a database record
   * was created (e.g. on validation errors or workspace failures).
   *
   * <p>Example: {@code 42}
   */
  private Integer runNumber;

  /**
   * Final status of the pipeline execution.
   *
   * <p>Possible values:
   * <ul>
   *   <li>{@code "SUCCESS"} — all jobs completed successfully</li>
   *   <li>{@code "FAILED"} — one or more jobs failed or an internal error occurred</li>
   *   <li>{@code "VALIDATION_FAILED"} — pipeline YAML is invalid; no jobs were executed</li>
   * </ul>
   */
  private String status;

  /**
   * Human-readable description of the execution outcome.
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code "Pipeline completed successfully. All 5 jobs passed."}</li>
   *   <li>{@code "Pipeline failed at job 'test'"}</li>
   *   <li>{@code "Pipeline validation failed: Circular dependency detected"}</li>
   *   <li>{@code "Internal error: Docker daemon not reachable"}</li>
   * </ul>
   */
  private String message;
}