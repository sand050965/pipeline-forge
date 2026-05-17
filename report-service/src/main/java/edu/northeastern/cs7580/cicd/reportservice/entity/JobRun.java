package edu.northeastern.cs7580.cicd.reportservice.entity;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity representing a single job execution record stored in the
 * {@code job_runs} database table.
 *
 * <p>Each row corresponds to one job within a stage run. A job is the smallest
 * unit of work in a pipeline — it runs a set of commands inside a Docker
 * container with a specified image.
 *
 * <p>The entity is linked to its parent stage run via {@code stageRunId},
 * which references the {@code stage_runs} table.
 *
 * <p>Fields in this entity:
 * <ul>
 *   <li>{@code stageRunId} — foreign key to the owning stage run</li>
 *   <li>{@code jobName} — the name of this job</li>
 *   <li>{@code status} — execution status (pending, running, success, failed,
 *       cancelled)</li>
 *   <li>{@code startTime}, {@code endTime} — execution timestamps</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("job_runs")
public class JobRun {

  @Id
  private Long id;

  @Column("stage_run_id")
  private Long stageRunId;

  @Column("job_name")
  private String jobName;

  private String status;

  @Column("failures")
  private boolean failures;

  @Column("start_time")
  private OffsetDateTime startTime;

  @Column("end_time")
  private OffsetDateTime endTime;

  @Column("created_at")
  private OffsetDateTime createdAt;

  @Column("updated_at")
  private OffsetDateTime updatedAt;
}
