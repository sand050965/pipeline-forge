package edu.northeastern.cs7580.cicd.executionservice.model;

import edu.northeastern.cs7580.cicd.executionservice.executor.DockerExecutor;
import edu.northeastern.cs7580.cicd.executionservice.service.ExecutionService;

/**
 * Enumeration of possible states for a job execution.
 *
 * <p>This enum represents the lifecycle states a job can be in during pipeline
 * execution. In the current sequential execution implementation (Sprint 3), only
 * three states are actively used: COMPLETED, FAILED, and SKIPPED. The PENDING
 * and RUNNING states are reserved for future parallel and asynchronous execution
 * implementations.
 *
 * <p><b>State Transitions in Sequential Execution:</b>
 * <pre>
 * [Job Created] → COMPLETED (exit code 0)
 *              → FAILED    (non-zero exit code)
 *
 * [Previous Job Failed] → SKIPPED (never executed)
 * </pre>
 *
 * <p><b>Future State Transitions (Parallel Execution):</b>
 * <pre>
 * PENDING → RUNNING → COMPLETED
 *                  → FAILED
 *
 * [Dependency Failed] → SKIPPED
 * </pre>
 *
 * <p><b>Current Sprint Usage:</b>
 * <ul>
 *   <li><b>COMPLETED:</b> Used when {@link DockerExecutor} returns exit code 0</li>
 *   <li><b>FAILED:</b> Used when {@link DockerExecutor} returns non-zero exit code</li>
 *   <li><b>SKIPPED:</b> Set by {@link ExecutionService} when earlier job fails</li>
 *   <li><b>PENDING:</b> Not used in current implementation</li>
 *   <li><b>RUNNING:</b> Not used in current implementation</li>
 * </ul>
 *
 * @see JobResult
 * @see ExecutionService
 * @see DockerExecutor
 */
public enum JobStatus {
  /** Job is queued and waiting to be executed. */
  PENDING,

  /** Job is currently executing in a Docker container. */
  RUNNING,

  /** Job executed successfully with exit code 0. */
  COMPLETED,

  /** Job execution failed with a non-zero exit code. */
  FAILED,

  /** Job was not executed due to a previous job failure. */
  SKIPPED
}
