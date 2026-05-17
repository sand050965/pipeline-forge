package edu.northeastern.cs7580.cicd.executionservice.repository;

import edu.northeastern.cs7580.cicd.executionservice.entity.PipelineEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for {@link PipelineEntity}.
 *
 * <p>Provides standard CRUD operations for the {@code pipelines} table, plus
 * a derived query method for looking up a pipeline by its natural key.
 * Spring Data R2DBC generates the implementation at startup — no manual
 * SQL or connection handling is required.
 *
 * <p>Inherited operations include:
 * <ul>
 *   <li>{@code save(entity)} — INSERT or UPDATE a pipeline row</li>
 *   <li>{@code findById(id)} — look up a pipeline by its surrogate key</li>
 *   <li>{@code deleteById(id)} — delete a pipeline row</li>
 * </ul>
 *
 * @see PipelineEntity
 */
public interface PipelineRepository extends ReactiveCrudRepository<PipelineEntity, Long> {

  /**
   * Finds a pipeline by its natural key: the repository hash and pipeline name.
   *
   * <p>Used during the upsert flow at the start of each execution to check
   * whether a definition row already exists before creating a new one.
   * {@code repo_id} is the SHA-256 hash of the Git repository URL, computed
   * by the application layer before calling this method.
   *
   * @param repoId       SHA-256 hash of the Git repository URL (64 hex chars)
   * @param pipelineName logical name of the pipeline (e.g. {@code "deploy-prod"})
   * @return a {@code Mono} emitting the matching entity, or empty if none exists
   */
  Mono<PipelineEntity> findByRepoIdAndPipelineName(String repoId, String pipelineName);
}