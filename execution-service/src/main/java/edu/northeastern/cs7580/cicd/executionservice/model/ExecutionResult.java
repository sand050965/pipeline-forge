package edu.northeastern.cs7580.cicd.executionservice.model;

import edu.northeastern.cs7580.cicd.executionservice.controller.ExecutionController;
import edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionResponse;
import edu.northeastern.cs7580.cicd.executionservice.entity.PipelineRunEntity;
import edu.northeastern.cs7580.cicd.executionservice.service.ExecutionService;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a complete pipeline execution containing all job outcomes.
 *
 * <p>This domain model represents the final state of a pipeline execution after
 * all jobs have either completed, failed, or been skipped. It provides a complete
 * record of what happened during the execution, including individual job results,
 * overall success/failure status, and identification of the failed job if applicable.
 *
 * <p><b>Contents:</b>
 * <ul>
 *   <li><b>jobResults:</b> Ordered list of all job results in execution order</li>
 *   <li><b>success:</b> Boolean flag indicating overall pipeline success</li>
 *   <li><b>failedJobName:</b> Name of the first job that failed (null if all succeeded)</li>
 * </ul>
 *
 * <p><b>Execution Outcomes:</b>
 * <ul>
 *   <li><b>All jobs succeeded:</b> {@code success = true}, {@code failedJobName = null},
 *       all jobs have status COMPLETED</li>
 *   <li><b>One job failed:</b> {@code success = false}, {@code failedJobName} points to
 *       the failed job, remaining jobs have status SKIPPED</li>
 * </ul>
 *
 * <p><b>Job Result Order:</b>
 * The {@code jobResults} list maintains execution order, which is the topological
 * order determined by job dependencies. This allows consumers to reconstruct the
 * exact sequence of events that occurred during execution.
 *
 * <p><b>Usage in Response Flow:</b>
 * <ol>
 *   <li>{@link ExecutionService} creates this result after executing all jobs</li>
 *   <li>{@link ExecutionController} uses it to construct {@link PipelineExecutionResponse}</li>
 *   <li>CLI or other clients receive simplified status information</li>
 * </ol>
 *
 * <p><b>Example - Successful Execution:</b>
 * <blockquote><pre>
 * ExecutionResult result = ExecutionResult.builder()
 *     .jobResults(Arrays.asList(
 *         // All jobs completed successfully
 *         jobResult1, jobResult2, jobResult3
 *     ))
 *     .success(true)
 *     .failedJobName(null)
 *     .build();
 * </pre></blockquote>
 *
 * <p><b>Example - Failed Execution:</b>
 * <blockquote><pre>
 * ExecutionResult result = ExecutionResult.builder()
 *     .jobResults(Arrays.asList(
 *         completedJob1,  // Status: COMPLETED
 *         completedJob2,  // Status: COMPLETED
 *         failedJob,      // Status: FAILED (this is where it stopped)
 *         skippedJob1,    // Status: SKIPPED
 *         skippedJob2     // Status: SKIPPED
 *     ))
 *     .success(false)
 *     .failedJobName("test")
 *     .build();
 * </pre></blockquote>
 *
 * @see JobResult
 * @see ExecutionService
 * @see JobStatus
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

  /**
   * Ordered list of all job results in execution order.
   *
   * <p>This list contains one {@link JobResult} for each job in the pipeline,
   * in the order they were processed (topological order). Each result includes
   * the job's name, status, output, exit code, and execution time.
   *
   * <p>For successful executions, all jobs will have status {@link JobStatus#COMPLETED}.
   * For failed executions, jobs up to and including the failed job will have
   * status COMPLETED or FAILED, and remaining jobs will have status
   * {@link JobStatus#SKIPPED}.
   *
   * <p>This list is never null and contains at least one element.
   */
  private List<JobResult> jobResults;

  /**
   * Overall success status of the pipeline execution.
   *
   * <p>This is {@code true} if and only if all jobs completed successfully
   * (all have status {@link JobStatus#COMPLETED}). It is {@code false} if
   * any job failed.
   *
   * <p>This flag provides a quick way to determine the pipeline outcome without
   * iterating through all job results.
   */
  private boolean success;

  /**
   * Name of the first job that failed, or null if all jobs succeeded.
   *
   * <p>When {@code success} is {@code false}, this field identifies which job
   * caused the pipeline to fail. This is the first job in execution order that
   * returned status {@link JobStatus#FAILED}.
   *
   * <p>When {@code success} is {@code true}, this field is {@code null}.
   *
   * <p>This provides quick identification of the failure point without needing
   * to search through the job results list.
   *
   * <p>Example: {@code "test"}, {@code "deploy"}, or {@code null}
   */
  private String failedJobName;

  private Integer runNumber;
}
