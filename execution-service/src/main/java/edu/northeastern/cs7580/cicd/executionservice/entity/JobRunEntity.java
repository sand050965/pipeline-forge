package edu.northeastern.cs7580.cicd.executionservice.entity;

import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity representing a single job execution record in the {@code job_runs} table.
 *
 * <p>Each row corresponds to one job within a stage run. Records are created as
 * {@code PENDING} before execution begins, transitioned to {@code RUNNING} when the
 * job starts, and updated with the final status and {@code end_time} once execution
 * completes.
 *
 * <p>The lifecycle of a {@code JobRunEntity} follows this pattern:
 * <ol>
 *   <li>INSERT with {@code status=PENDING}, {@code start_time=now}, {@code end_time=null}
 *       — row is pre-created so the UI can render the full plan immediately</li>
 *   <li>UPDATE to {@code status=RUNNING} when the job begins executing</li>
 *   <li>Job executes inside a Docker container</li>
 *   <li>UPDATE with {@code status=SUCCESS|FAILED}, {@code end_time=now}</li>
 * </ol>
 *
 * <p>Jobs that are skipped due to a prior failure transition directly from
 * {@code PENDING} to {@code FAILED}, bypassing the {@code RUNNING} state, so the
 * Report Service always has a complete picture of every job in the run.
 *
 * <p>{@code status} is stored as a plain {@code VARCHAR} string matching the
 * {@link ExecutionStatus} enum name (e.g. {@code "RUNNING"}, {@code "SUCCESS"}).
 * Use {@link ExecutionStatus#name()} when writing and {@link ExecutionStatus#valueOf}
 * when reading to avoid R2DBC type-binding issues with custom PostgreSQL ENUM types.
 *
 * @see StageRunEntity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "job_runs")
public class JobRunEntity {

  /**
   * Auto-generated surrogate primary key.
   */
  @Id
  private Long id;

  /**
   * Foreign key referencing the parent {@code stage_runs} row.
   *
   * <p>Every job run belongs to exactly one stage run. Deleting the parent
   * stage run cascades to this row (enforced at the DB level).
   */
  @Column("stage_run_id")
  private Long stageRunId;

  /**
   * Logical name of the job as declared in the pipeline YAML configuration.
   *
   * <p>Example: {@code "build"}, {@code "test"}, {@code "deploy"}
   */
  @Column("job_name")
  private String jobName;

  /**
   * Whether this job is allowed to fail without failing the stage or pipeline.
   *
   * <p>Persisted from the pipeline YAML {@code failures} key. Defaults
   * to {@code false} when the key is absent.
   */
  @Column("failures")
  @Builder.Default
  private boolean failures = false;

  /**
   * Current execution status of this job, stored as a plain {@code VARCHAR}.
   *
   * <p>Valid values correspond to {@link ExecutionStatus} enum names:
   * <ul>
   *   <li>{@code "PENDING"}  — row created, execution has not started yet</li>
   *   <li>{@code "RUNNING"}  — job is currently executing</li>
   *   <li>{@code "SUCCESS"}  — job completed with exit code 0</li>
   *   <li>{@code "FAILED"}   — job exited with a non-zero code, threw an
   *       exception, or was skipped due to a prior failure</li>
   * </ul>
   */
  @Column("status")
  private String status;

  /**
   * Timestamp when this job started executing.
   *
   * <p>Set at INSERT time and does not change thereafter.
   * Uses {@code TIMESTAMP WITH TIME ZONE} in PostgreSQL.
   */
  @Column("start_time")
  private OffsetDateTime startTime;

  /**
   * Timestamp when this job finished executing, or {@code null} while still running.
   *
   * <p>Set during the UPDATE that follows job completion. The difference between
   * {@code end_time} and {@code start_time} represents the wall-clock execution
   * duration of this job.
   */
  @Column("end_time")
  private OffsetDateTime endTime;

  /**
   * Timestamp when this row was first inserted into the database.
   *
   * <p>Set once at INSERT time and never modified thereafter.
   */
  @Column("created_at")
  private OffsetDateTime createdAt;

  /**
   * Timestamp of the most recent update to this row.
   *
   * <p>Updated automatically by a database trigger on every {@code UPDATE}.
   */
  @Column("updated_at")
  private OffsetDateTime updatedAt;
}
