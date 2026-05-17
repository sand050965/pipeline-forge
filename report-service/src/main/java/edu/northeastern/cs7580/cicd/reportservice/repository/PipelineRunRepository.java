package edu.northeastern.cs7580.cicd.reportservice.repository;

import edu.northeastern.cs7580.cicd.reportservice.entity.PipelineRun;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for querying {@link PipelineRun} records from the
 * {@code pipeline_runs} database table.
 *
 * <p>This repository extends {@link ReactiveCrudRepository} to provide
 * non-blocking database access via R2DBC. It defines custom query methods
 * that Spring Data derives automatically from the method name conventions.
 *
 * <p>Supported queries:
 * <ul>
 *   <li>Find all runs for a given pipeline ID.</li>
 *   <li>Find a specific run by pipeline ID and run number.</li>
 * </ul>
 */
@Repository
public interface PipelineRunRepository
    extends ReactiveCrudRepository<PipelineRun, Long> {

  /**
   * Finds all pipeline runs belonging to a pipeline.
   *
   * @param pipelineId the ID of the parent pipeline
   * @return a {@link Flux} of matching pipeline runs
   */
  Flux<PipelineRun> findByPipelineId(Long pipelineId);

  /**
   * Finds a single pipeline run by its pipeline ID and run number.
   *
   * @param pipelineId the ID of the parent pipeline
   * @param runNo      the sequential run number
   * @return a {@link Mono} containing the matching run, or empty if not found
   */
  Mono<PipelineRun> findByPipelineIdAndRunNo(Long pipelineId, int runNo);

  /**
   * Finds the most recent run for a pipeline, ordered by run number descending.
   *
   * @param pipelineId the ID of the parent pipeline
   * @return a {@link Mono} containing the most recent run, or empty if none exists
   */
  Mono<PipelineRun> findFirstByPipelineIdOrderByRunNoDesc(Long pipelineId);

  /**
   * Finds the most recent run in a given status for a pipeline.
   *
   * @param pipelineId the ID of the parent pipeline
   * @param status     the status to filter on (e.g. {@code "RUNNING"})
   * @return a {@link Mono} containing the matching run, or empty if none exists
   */
  Mono<PipelineRun> findFirstByPipelineIdAndStatusOrderByRunNoDesc(
      Long pipelineId, String status);
}
