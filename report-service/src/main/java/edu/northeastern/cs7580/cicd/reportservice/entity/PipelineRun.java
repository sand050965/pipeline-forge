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
 * R2DBC entity representing a single pipeline execution record stored in the
 * {@code pipeline_runs} database table.
 *
 * <p>Each row corresponds to one invocation of a CI/CD pipeline. The entity
 * references its parent {@link Pipeline} via {@code pipelineId}, which links
 * to the {@code pipelines} table containing the pipeline name and repository
 * information.
 *
 * <p>The combination of {@code pipelineId} and {@code runNo} is unique,
 * enforced by a database constraint. The {@code runNo} is auto-incremented
 * per pipeline by a database trigger.
 *
 * <p>Fields in this entity:
 * <ul>
 *   <li>{@code pipelineId} — foreign key to the {@code pipelines} table</li>
 *   <li>{@code runNo} — incrementing counter scoped per pipeline</li>
 *   <li>{@code status} — execution status (pending, running, success, failed,
 *       cancelled)</li>
 *   <li>{@code gitBranch}, {@code gitHash} — Git metadata for this run</li>
 *   <li>{@code startTime}, {@code endTime} — execution timestamps</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("pipeline_runs")
public class PipelineRun {

  @Id
  private Long id;

  @Column("pipeline_id")
  private Long pipelineId;

  @Column("run_no")
  private Integer runNo;

  private String status;

  @Column("git_branch")
  private String gitBranch;

  @Column("git_hash")
  private String gitHash;

  @Column("start_time")
  private OffsetDateTime startTime;

  @Column("end_time")
  private OffsetDateTime endTime;

  @Column("created_at")
  private OffsetDateTime createdAt;

  @Column("updated_at")
  private OffsetDateTime updatedAt;

  @Column("trace_id")
  private String traceId;
}
