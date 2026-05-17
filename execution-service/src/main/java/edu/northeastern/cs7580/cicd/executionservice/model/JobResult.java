package edu.northeastern.cs7580.cicd.executionservice.model;

import edu.northeastern.cs7580.cicd.executionservice.executor.DockerExecutor;
import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a single job execution within a pipeline.
 *
 * <p>This domain model captures all relevant information about a job's execution,
 * including its outcome status, output logs, exit code, and execution duration.
 * Each job in a pipeline produces exactly one JobResult, which is included in
 * the overall {@link ExecutionResult}.
 *
 * <p><b>Job Lifecycle:</b>
 * <ol>
 *   <li>Job is selected for execution from the execution plan</li>
 *   <li>{@link DockerExecutor} creates a container and runs the job's steps</li>
 *   <li>Execution completes with either success (exit code 0) or failure (non-zero)</li>
 *   <li>JobResult is created with captured output, exit code, and duration</li>
 *   <li>Result is added to the pipeline's {@link ExecutionResult}</li>
 * </ol>
 *
 * <p><b>Status Values:</b>
 * For executed jobs (not skipped), the status will be:
 * <ul>
 *   <li>{@link JobStatus#COMPLETED} - Job executed successfully (exit code 0)</li>
 *   <li>{@link JobStatus#FAILED} - Job execution failed (non-zero exit code)</li>
 * </ul>
 *
 * <p>For skipped jobs (due to earlier failure), the status will be:
 * <ul>
 *   <li>{@link JobStatus#SKIPPED} - Job not executed due to dependency failure</li>
 * </ul>
 *
 * <p><b>Output Capture:</b>
 * The {@code output} field contains both stdout and stderr from the Docker container
 * where the job executed. This includes all log messages, error messages, and any
 * output produced by the job's steps.
 *
 * <p><b>Execution Time Tracking:</b>
 * The {@code executionTime} field tracks how long the job took to execute, from
 * container start to container stop. This does not include time spent waiting for
 * previous jobs to complete. For skipped jobs, this field is null.
 *
 * <p><b>Example - Completed Job:</b>
 * <blockquote><pre>
 * JobResult result = JobResult.builder()
 *     .jobName("test")
 *     .status(JobStatus.COMPLETED)
 *     .output("Running tests...\nAll tests passed!\n")
 *     .exitCode(0)
 *     .executionTime(Duration.ofSeconds(45))
 *     .build();
 * </pre></blockquote>
 *
 * <p><b>Example - Failed Job:</b>
 * <blockquote><pre>
 * JobResult result = JobResult.builder()
 *     .jobName("lint")
 *     .status(JobStatus.FAILED)
 *     .output("Running linter...\nError: unused variable at line 42\n")
 *     .exitCode(1)
 *     .executionTime(Duration.ofSeconds(12))
 *     .build();
 * </pre></blockquote>
 *
 * <p><b>Example - Skipped Job:</b>
 * <blockquote><pre>
 * JobResult result = JobResult.builder()
 *     .jobName("deploy")
 *     .status(JobStatus.SKIPPED)
 *     .output("Job skipped due to previous job failure")
 *     .exitCode(0)
 *     .executionTime(null)
 *     .build();
 * </pre></blockquote>
 *
 * @see JobStatus
 * @see ExecutionResult
 * @see DockerExecutor
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResult {

  /**
   * The name of the job that was executed.
   *
   * <p>This corresponds to the {@code name} field in the job definition from the
   * pipeline configuration YAML. Job names must be unique within a pipeline.
   *
   * <p>Example: {@code "build"}, {@code "test"}, {@code "deploy"}
   */
  private String jobName;

  /**
   * The execution status of this job.
   *
   * <p>For jobs that were executed, this will be either {@link JobStatus#COMPLETED}
   * (success, exit code 0) or {@link JobStatus#FAILED} (failure, non-zero exit code).
   *
   * <p>For jobs that were not executed due to an earlier failure, this will be
   * {@link JobStatus#SKIPPED}.
   *
   * <p>This field is never null.
   *
   * @see JobStatus
   */
  private JobStatus status;

  /**
   * Combined stdout and stderr output from the job execution.
   *
   * <p>For executed jobs, this contains all log output from the Docker container,
   * including output from all steps executed within the job. This is captured
   * from both standard output and standard error streams.
   *
   * <p>For skipped jobs, this contains a message explaining why the job was skipped,
   * typically: {@code "Job skipped due to previous job failure"}.
   *
   * <p>This field is never null but may be an empty string if the job produced no output.
   *
   * <p>Example output:
   * <pre>
   * Cloning repository...
   * Installing dependencies...
   * Running build...
   * Build completed successfully
   * </pre>
   */
  private String output;

  /**
   * The exit code returned by the job execution.
   *
   * <p>For executed jobs:
   * <ul>
   *   <li>{@code 0} indicates success ({@link JobStatus#COMPLETED})</li>
   *   <li>Non-zero indicates failure ({@link JobStatus#FAILED})</li>
   * </ul>
   *
   * <p>For skipped jobs, this is set to {@code 0} by convention (the job didn't
   * actually execute, but we need a value for the field).
   *
   * <p>Common non-zero exit codes:
   * <ul>
   *   <li>{@code 1} - General error</li>
   *   <li>{@code 2} - Misuse of shell command</li>
   *   <li>{@code 126} - Command cannot execute</li>
   *   <li>{@code 127} - Command not found</li>
   *   <li>{@code 130} - Script terminated by Ctrl+C</li>
   * </ul>
   */
  private int exitCode;

  /**
   * Time spent executing this job, from container start to stop.
   *
   * <p>For executed jobs, this represents the actual wall-clock time spent running
   * the job in the Docker container. This includes time for all steps within the job
   * but does not include time spent waiting for previous jobs to complete.
   *
   * <p>For skipped jobs, this is {@code null} since the job was never executed.
   *
   * <p>This value is useful for:
   * <ul>
   *   <li>Performance monitoring and optimization</li>
   *   <li>Identifying slow jobs in the pipeline</li>
   *   <li>Billing or resource allocation</li>
   * </ul>
   *
   * <p>Example values: {@code Duration.ofSeconds(45)}, {@code Duration.ofMinutes(3)},
   * or {@code null} for skipped jobs
   */
  private Duration executionTime;
}
