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
 * R2DBC entity representing a single stage execution record in the {@code stage_runs} table.
 *
 * <p>Each row corresponds to one stage within a pipeline run. Records are created as
 * {@code PENDING} before execution begins, transitioned to {@code RUNNING} when the
 * stage starts, and updated with the final status and {@code end_time} once all jobs
 * in the stage have completed.
 *
 * <p>The lifecycle of a {@code StageRunEntity} follows this pattern:
 * <ol>
 *   <li>INSERT with {@code status=PENDING}, {@code start_time=now}, {@code end_time=null}
 *       — row is pre-created so the UI can render the full plan immediately</li>
 *   <li>UPDATE to {@code status=RUNNING} when the stage begins executing</li>
 *   <li>Each job in the stage executes sequentially</li>
 *   <li>UPDATE with {@code status=SUCCESS|FAILED}, {@code end_time=now}</li>
 * </ol>
 *
 * <p>A stage is marked {@code FAILED} if any of its jobs failed or were skipped due to
 * a prior failure. Stages that are entirely skipped (because an earlier stage already
 * failed) transition directly from {@code PENDING} to {@code FAILED}, bypassing the
 * {@code RUNNING} state.
 *
 * <p>{@code status} is stored as a plain {@code VARCHAR} string matching the
 * {@link ExecutionStatus} enum name (e.g. {@code "RUNNING"}, {@code "SUCCESS"}).
 * Use {@link ExecutionStatus#name()} when writing and {@link ExecutionStatus#valueOf}
 * when reading to avoid R2DBC type-binding issues with custom PostgreSQL ENUM types.
 *
 * @see PipelineRunEntity
 * @see JobRunEntity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stage_runs")
public class StageRunEntity {

  /**
   * Auto-generated surrogate primary key.
   *
   * <p>Used as the foreign key in {@code job_runs} to link job records
   * back to their parent stage run.
   */
  @Id
  private Long id;

  /**
   * Foreign key referencing the parent {@code pipeline_runs} row.
   *
   * <p>Every stage run belongs to exactly one pipeline run. Deleting the parent
   * pipeline run cascades to this row (enforced at the DB level).
   */
  @Column("pipeline_run_id")
  private Long pipelineRunId;

  /**
   * Logical name of the stage as declared in the pipeline YAML configuration.
   *
   * <p>Example: {@code "build"}, {@code "test"}, {@code "deploy"}
   */
  @Column("stage_name")
  private String stageName;

  /**
   * Current execution status of this stage, stored as a plain {@code VARCHAR}.
   *
   * <p>Valid values correspond to {@link ExecutionStatus} enum names:
   * <ul>
   *   <li>{@code "PENDING"}  — row created, execution has not started yet</li>
   *   <li>{@code "RUNNING"}  — stage is currently executing</li>
   *   <li>{@code "SUCCESS"}  — all jobs in the stage completed successfully</li>
   *   <li>{@code "FAILED"}   — one or more jobs failed, were skipped, or the
   *       entire stage was skipped due to a prior stage failure</li>
   * </ul>
   */
  @Column("status")
  private String status;

  /**
   * Timestamp when this stage started executing.
   *
   * <p>Set at INSERT time and does not change thereafter.
   * Uses {@code TIMESTAMP WITH TIME ZONE} in PostgreSQL.
   */
  @Column("start_time")
  private OffsetDateTime startTime;

  /**
   * Timestamp when this stage finished executing, or {@code null} while still running.
   *
   * <p>Set during the UPDATE that follows stage completion.
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