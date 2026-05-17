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
 * Entity representing a single execution run of a CI/CD pipeline.
 *
 * <p>Maps to the {@code pipeline_runs} database table and captures the full lifecycle
 * of a pipeline execution, including its trigger context (branch, commit hash),
 * current status, and timing metadata.</p>
 *
 * <p>Instances are typically created via the {@link PipelineRunEntity} and
 * persisted through
 * {@link edu.northeastern.cs7580.cicd.executionservice.repository.PipelineRunRepository}.</p>
 *
 * @see edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus
 * @see edu.northeastern.cs7580.cicd.executionservice.repository.PipelineRunRepository
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pipeline_runs")
public class PipelineRunEntity {

  /**
   * Auto-generated primary key for this pipeline run record.
   */
  @Id
  private Long id;

  /**
   * Foreign key referencing the pipeline definition that this run belongs to.
   */
  @Column("pipeline_id")
  private Long pipelineId;

  /**
   * Sequential run number for the pipeline, incremented with each new execution.
   * For example, the third execution of a pipeline has a {@code runNo} of {@code 3}.
   */
  @Column("run_no")
  private Integer runNo;

  /**
   * Current execution status of the pipeline run.
   * Expected values align with {@link ExecutionStatus} (e.g., {@code PENDING},
   * {@code RUNNING}, {@code SUCCESS}, {@code FAILED}).
   */
  @Column("status")
  private String status;

  /**
   * Name of the Git branch that triggered this pipeline run.
   */
  @Column("git_branch")
  private String gitBranch;

  /**
   * Full or abbreviated Git commit hash associated with this pipeline run,
   * identifying the exact source code revision being built.
   */
  @Column("git_hash")
  private String gitHash;

  /**
   * Timestamp indicating when the pipeline run began execution.
   * Stored with timezone offset to support multi-region deployments.
   */
  @Column("start_time")
  private OffsetDateTime startTime;

  /**
   * Timestamp indicating when the pipeline run completed, either successfully or with failure.
   * {@code null} if the run is still in progress.
   */
  @Column("end_time")
  private OffsetDateTime endTime;

  /**
   * Timestamp of when this record was first persisted to the database.
   * Typically set once on insert and never modified.
   */
  @Column("created_at")
  private OffsetDateTime createdAt;

  /**
   * Timestamp of the most recent update to this record.
   * Updated whenever the run's status or timing information changes.
   */
  @Column("updated_at")
  private OffsetDateTime updatedAt;

  /**
   * Human-readable error message populated when the run fails due to an internal
   * consumer error (e.g. unexpected exception during execution).
   *
   * <p>{@code null} for successful runs and runs that failed at the job level.
   * Only set when the failure originates inside the execution infrastructure
   * itself, so the user knows to file an issue rather than fix their pipeline.</p>
   */
  @Column("error_message")
  private String errorMessage;

  /**
   * 32-character lowercase hex W3C trace ID assigned when this run was created.
   * Allows operators to correlate a {@code cicd report --run N} result with the
   * corresponding distributed trace in Tempo.
   */
  @Column("trace_id")
  private String traceId;
}