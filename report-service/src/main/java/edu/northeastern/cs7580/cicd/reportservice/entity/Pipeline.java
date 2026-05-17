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
 * R2DBC entity representing a pipeline definition stored in the
 * {@code pipelines} database table.
 *
 * <p>Each row uniquely identifies a pipeline within a specific Git repository.
 * The combination of {@code repoId} (a SHA-256 hash of the Git repository URL)
 * and {@code pipelineName} is unique, ensuring that different repositories can
 * have pipelines with the same name without conflict.
 *
 * <p>Fields in this entity:
 * <ul>
 *   <li>{@code repoId} — SHA-256 hash of the Git repository URL (64 hex chars)</li>
 *   <li>{@code pipelineName} — logical name of the pipeline</li>
 *   <li>{@code gitRepo} — original repository URL for human readability</li>
 *   <li>{@code createdAt}, {@code updatedAt} — audit timestamps</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("pipelines")
public class Pipeline {

  @Id
  private Long id;

  @Column("repo_id")
  private String repoId;

  @Column("pipeline_name")
  private String pipelineName;

  @Column("git_repo")
  private String gitRepo;

  @Column("created_at")
  private OffsetDateTime createdAt;

  @Column("updated_at")
  private OffsetDateTime updatedAt;
}
