package edu.northeastern.cs7580.cicd.reportservice.repository;

import edu.northeastern.cs7580.cicd.reportservice.entity.Pipeline;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for querying {@link Pipeline} records from the
 * {@code pipelines} database table.
 *
 * <p>This repository extends {@link ReactiveCrudRepository} to provide
 * non-blocking database access via R2DBC. It defines a custom query method
 * for looking up pipelines by their logical name.
 *
 * <p>In local mode the CLI only provides the pipeline name (no repository
 * identifier), so the primary query method is {@link #findByPipelineName}.
 * A future enhancement may add repository-scoped queries using
 * {@code repoId}.
 */
@Repository
public interface PipelineRepository
    extends ReactiveCrudRepository<Pipeline, Long> {

  /**
   * Finds all pipelines matching the given name.
   *
   * <p>In a multi-repository environment, multiple pipelines can share the
   * same name across different repositories. In local mode this typically
   * returns a single result.
   *
   * @param pipelineName the pipeline name to search for
   * @return a {@link Flux} of matching pipelines
   */
  Flux<Pipeline> findByPipelineName(String pipelineName);

  /**
   * Finds all pipelines associated with the given repository URL.
   *
   * @param gitRepo the repository URL stored in the {@code git_repo} column
   * @return a {@link Flux} of matching pipelines
   */
  Flux<Pipeline> findByGitRepo(String gitRepo);
}
