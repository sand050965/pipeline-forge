package edu.northeastern.cs7580.cicd.reportservice.repository;

import edu.northeastern.cs7580.cicd.reportservice.entity.StageRun;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for querying {@link StageRun} records from the
 * {@code stage_runs} database table.
 *
 * <p>This repository extends {@link ReactiveCrudRepository} to provide
 * non-blocking database access via R2DBC. It defines custom query methods
 * for looking up stages belonging to a specific pipeline run.
 *
 * <p>Supported queries:
 * <ul>
 *   <li>Find all stage runs for a given pipeline run ID.</li>
 *   <li>Find a specific stage run by pipeline run ID and stage name.</li>
 * </ul>
 */
@Repository
public interface StageRunRepository
    extends ReactiveCrudRepository<StageRun, Long> {

  /**
   * Finds all stage runs belonging to a pipeline run.
   *
   * @param pipelineRunId the ID of the parent pipeline run
   * @return a {@link Flux} of matching stage runs
   */
  Flux<StageRun> findByPipelineRunId(Long pipelineRunId);

  /**
   * Finds a single stage run by pipeline run ID and stage name.
   *
   * @param pipelineRunId the ID of the parent pipeline run
   * @param stageName     the stage name to search for
   * @return a {@link Mono} containing the matching stage, or empty if not found
   */
  Mono<StageRun> findByPipelineRunIdAndStageName(
      Long pipelineRunId, String stageName);
}
