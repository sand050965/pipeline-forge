package edu.northeastern.cs7580.cicd.executionservice.entity;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity representing a unique pipeline definition in the {@code pipelines} table.
 *
 * <p>A pipeline is uniquely identified by the combination of its repository
 * ({@code repo_id}) and its logical name ({@code pipeline_name}). This design ensures
 * that pipelines with the same name in different repositories do not conflict.
 *
 * <p>{@code repo_id} is a SHA-256 hash of the full Git repository URL, computed by
 * the application before inserting. Using a hash keeps the primary lookup key
 * fixed-width (64 hex characters) regardless of how long the original URL is.
 *
 * <p>This table acts as the definition layer. Each pipeline can have many associated
 * execution records in the {@code pipeline_runs} table, linked via the surrogate
 * {@code id} of this entity.
 *
 * <p>Rows are upserted (find-or-create) at the start of each execution: if the
 * pipeline has been run before the existing row is reused; otherwise a new row
 * is inserted.
 *
 * @see PipelineRunEntity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pipelines")
public class PipelineEntity {

  /**
   * Auto-generated surrogate primary key.
   *
   * <p>Used as the foreign key in {@code pipeline_runs} to avoid carrying
   * the composite natural key ({@code repo_id + pipeline_name}) into child tables.
   */
  @Id
  private Long id;

  /**
   * SHA-256 hash of the Git repository URL, stored as a 64-character hex string.
   *
   * <p>The hash is computed by the application layer before inserting, using the
   * full repository URL (e.g. {@code "https://github.com/org/repo.git"}) as input.
   * Together with {@code pipeline_name}, this forms the natural unique key of the row.
   *
   * <p>Example: {@code "a3f1c2d4e5b6..."}
   */
  @Column("repo_id")
  private String repoId;

  /**
   * Logical name of the pipeline as declared in the YAML configuration.
   *
   * <p>Together with {@code repo_id}, this uniquely identifies a pipeline across
   * all repositories. Two repositories may each define a pipeline named
   * {@code "deploy-prod"} without conflict because their {@code repo_id} values
   * will differ.
   *
   * <p>Example: {@code "deploy-prod"}, {@code "run-tests"}
   */
  @Column("pipeline_name")
  private String pipelineName;

  /**
   * Original Git repository URL, stored for human readability.
   *
   * <p>This is the raw URL before hashing, retained so that operators can
   * identify which repository a pipeline belongs to without reversing the hash.
   *
   * <p>Example: {@code "https://github.com/org/repo.git"}
   */
  @Column("git_repo")
  private String gitRepo;

  /**
   * Timestamp when this pipeline definition was first recorded.
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