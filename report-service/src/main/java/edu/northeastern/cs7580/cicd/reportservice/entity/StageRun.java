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
 * R2DBC entity representing a single stage execution record stored in the
 * {@code stage_runs} database table.
 *
 * <p>Each row corresponds to one stage within a pipeline run. A stage groups
 * related jobs (e.g. build, test, deploy) and executes them according to the
 * pipeline's defined order.
 *
 * <p>The entity is linked to its parent pipeline run via
 * {@code pipelineRunId}, which references the {@code pipeline_runs} table.
 *
 * <p>Fields in this entity:
 * <ul>
 *   <li>{@code pipelineRunId} — foreign key to the owning pipeline run</li>
 *   <li>{@code stageName} — the name of this stage</li>
 *   <li>{@code status} — execution status (pending, running, success, failed,
 *       cancelled)</li>
 *   <li>{@code startTime}, {@code endTime} — execution timestamps</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("stage_runs")
public class StageRun {

  @Id
  private Long id;

  @Column("pipeline_run_id")
  private Long pipelineRunId;

  @Column("stage_name")
  private String stageName;

  private String status;

  @Column("start_time")
  private OffsetDateTime startTime;

  @Column("end_time")
  private OffsetDateTime endTime;

  @Column("created_at")
  private OffsetDateTime createdAt;

  @Column("updated_at")
  private OffsetDateTime updatedAt;
}
