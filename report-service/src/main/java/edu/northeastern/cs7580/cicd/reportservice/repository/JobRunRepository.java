package edu.northeastern.cs7580.cicd.reportservice.repository;

import edu.northeastern.cs7580.cicd.reportservice.entity.JobRun;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for querying {@link JobRun} records from the
 * {@code job_runs} database table.
 *
 * <p>This repository extends {@link ReactiveCrudRepository} to provide
 * non-blocking database access via R2DBC. It defines custom query methods
 * for looking up jobs belonging to a specific stage run.
 *
 * <p>Supported queries:
 * <ul>
 *   <li>Find all job runs for a given stage run ID.</li>
 *   <li>Find a specific job run by stage run ID and job name.</li>
 * </ul>
 */
@Repository
public interface JobRunRepository
    extends ReactiveCrudRepository<JobRun, Long> {

  /**
   * Finds all job runs belonging to a stage run.
   *
   * @param stageRunId the ID of the parent stage run
   * @return a {@link Flux} of matching job runs
   */
  Flux<JobRun> findByStageRunId(Long stageRunId);

  /**
   * Finds a single job run by stage run ID and job name.
   *
   * @param stageRunId the ID of the parent stage run
   * @param jobName    the job name to search for
   * @return a {@link Mono} containing the matching job, or empty if not found
   */
  Mono<JobRun> findByStageRunIdAndJobName(Long stageRunId, String jobName);
}
