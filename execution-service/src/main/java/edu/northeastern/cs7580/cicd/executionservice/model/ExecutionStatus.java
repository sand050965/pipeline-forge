package edu.northeastern.cs7580.cicd.executionservice.model;

/**
 * Execution status values shared across pipeline, stage, and job records.
 *
 * <p>Each constant maps directly to the {@code execution_status} ENUM type in
 * PostgreSQL via {@code @Enumerated(EnumType.STRING)} and
 * {@code @JdbcType(PostgreSQLEnumJdbcType.class)} — no string conversion needed.
 *
 * <p><b>Usage — DB write:</b>
 * <pre>
 * entity.setStatus(ExecutionStatus.RUNNING);   // persisted as "RUNNING"
 * entity.setStatus(ExecutionStatus.SUCCESS);   // persisted as "SUCCESS"
 * </pre>
 *
 * <p><b>Usage — API response:</b>
 * <pre>
 * response.setStatus(ExecutionStatus.SUCCESS.name());           // "SUCCESS"
 * response.setStatus(ExecutionStatus.VALIDATION_FAILED.name()); // "VALIDATION_FAILED"
 * </pre>
 */
public enum ExecutionStatus {

  /**
   * Row has been created but execution has not started yet.
   *
   * <p>Used by stages and jobs that are pre-created so the UI can render the
   * full execution plan before any work begins. Pipeline runs are never
   * {@code PENDING} — they are inserted directly as {@link #RUNNING}.
   */
  PENDING,

  /**
   * Pipeline, stage, or job is currently executing.
   */
  RUNNING,

  /**
   * All jobs completed successfully.
   */
  SUCCESS,

  /**
   * One or more jobs failed, or were skipped due to a prior failure.
   */
  FAILED,

  /**
   * Pipeline YAML configuration is invalid — used in API responses only,
   * never written to the {@code execution_status} DB column.
   */
  VALIDATION_FAILED;
}