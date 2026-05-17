package edu.northeastern.cs7580.cicd.executionservice.service;

import edu.northeastern.cs7580.cicd.executionservice.executor.DockerExecutor;
import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import edu.northeastern.cs7580.cicd.executionservice.model.JobResult;
import edu.northeastern.cs7580.cicd.executionservice.model.JobStatus;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Executes the jobs of a single stage using parallel wave scheduling.
 *
 * <p><b>Wave scheduling:</b> Jobs are grouped into waves based on their
 * {@code needs} dependencies:
 * <ul>
 *   <li><b>Wave 0:</b> jobs with no {@code needs}</li>
 *   <li><b>Wave N:</b> jobs whose every declared dependency completed in waves 0…N-1</li>
 * </ul>
 * All jobs within a wave are dispatched concurrently via
 * {@link CompletableFuture#supplyAsync} on a dedicated cached thread pool.
 * Waves execute sequentially — wave N only starts after wave N-1 is fully resolved.
 *
 * <p><b>Failure propagation:</b> A failed job causes only its direct and transitive
 * dependents to be skipped. Independent jobs in later waves are unaffected.
 * Whether a failure is "real" depends on the job's {@code failures} flag: if
 * {@code failures: true}, dependent jobs treat it as successful and still run.
 *
 * <p><b>Shared workspace:</b> All parallel jobs in a stage share the same
 * workspace directory. This is safe because jobs in the same wave are, by
 * definition, independent — the {@code needs} validator prevents two jobs that
 * write to the same paths from running concurrently.
 *
 * <p><b>MDC propagation:</b> The MDC context present on the calling thread is
 * captured before any future is submitted and restored at the start of each
 * async thread, so log messages carry the full pipeline/stage context regardless
 * of which thread they run on.
 *
 * <p><b>Thread safety:</b> Stateless. {@code executeStage} is safe to call
 * concurrently for different stages, but the {@code statusMap} parameter must
 * be a {@link java.util.concurrent.ConcurrentHashMap}.
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "DockerExecutor, ExecutionPersistenceService, Tracer, and MeterRegistry are "
        + "Spring-managed singleton beans; storing the references is intentional and safe."
)
@Component
public class ParallelStageExecutor {

  private static final Logger log = LoggerFactory.getLogger(ParallelStageExecutor.class);
  private static final String SKIPPED_MESSAGE = "Job skipped due to previous job failure";

  private final DockerExecutor dockerExecutor;
  private final ExecutionPersistenceService persistenceService;
  private final Tracer tracer;
  private final MeterRegistry meterRegistry;
  private final Executor jobExecutor;

  /**
   * Constructs a {@code ParallelStageExecutor} with the required collaborators.
   *
   * <p>A dedicated cached thread pool is created internally to run blocking Docker
   * calls without starving the R2DBC or RabbitMQ event loops.
   *
   * @param dockerExecutor     executes individual jobs inside Docker containers
   * @param persistenceService writes and updates execution records in PostgreSQL
   * @param tracer             Micrometer tracer for creating per-job spans
   * @param meterRegistry      Micrometer registry for recording job metrics
   */
  public ParallelStageExecutor(DockerExecutor dockerExecutor,
                               ExecutionPersistenceService persistenceService,
                               Tracer tracer,
                               MeterRegistry meterRegistry) {
    this.dockerExecutor = dockerExecutor;
    this.persistenceService = persistenceService;
    this.tracer = tracer;
    this.meterRegistry = meterRegistry;
    this.jobExecutor = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "parallel-job-executor");
      t.setDaemon(true);
      return t;
    });
  }

  /**
   * Executes all jobs in a stage using parallel wave scheduling.
   *
   * <p>Jobs are grouped into waves via {@link #computeWaves}. Each wave is
   * executed by {@link #executeWave}, which dispatches jobs concurrently and
   * waits for all of them before returning. Results from all waves are
   * accumulated and returned in wave order.
   *
   * <p>The {@code statusMap} is updated in-place as jobs complete so that
   * subsequent waves can evaluate their dependency conditions correctly. The
   * caller must pass a {@link java.util.concurrent.ConcurrentHashMap}.
   *
   * @param stage           the stage whose jobs should be executed
   * @param jobDependencies pipeline-level dependency map from
   *                        {@link edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan}
   * @param workspacePath   shared workspace directory bind-mounted into every container
   * @param stageIds        map of {@code "__stageRunId__"} and job name → DB row id,
   *                        produced by
   *                        {@link ExecutionPersistenceService#preCreateStagesAndJobs}
   * @param statusMap       cumulative job-status map across all stages; updated in-place
   * @param pipelineName    pipeline name for logging and metrics tags
   * @param runNo           run number for logging and metrics tags
   * @return all {@link JobResult}s for the stage, in wave order
   */
  public List<JobResult> executeStage(StageExecution stage,
                                      Map<String, List<String>> jobDependencies,
                                      Path workspacePath,
                                      Map<String, Long> stageIds,
                                      Map<String, JobStatus> statusMap,
                                      String pipelineName,
                                      int runNo) {
    List<List<Job>> waves = computeWaves(stage.getJobs(), jobDependencies);

    Map<String, Job> jobsByName = new HashMap<>();
    for (Job job : stage.getJobs()) {
      jobsByName.put(job.getName(), job);
    }

    List<JobResult> allResults = new ArrayList<>();

    for (List<Job> wave : waves) {
      List<JobResult> waveResults = executeWave(
          wave, workspacePath, stageIds, statusMap, jobsByName,
          stage.getStageName(), pipelineName, runNo);
      allResults.addAll(waveResults);
    }

    return allResults;
  }

  /**
   * Computes the execution waves for the jobs in a single stage.
   *
   * <p>A job's wave index equals {@code max(wave index of its in-stage dependencies) + 1},
   * or {@code 0} if it has no in-stage dependencies. Jobs with the same wave index have
   * no dependency ordering between them and may run concurrently.
   *
   * <p>Only dependencies that belong to the same stage are considered. Cross-stage
   * {@code needs} are rejected by the pipeline validator before this method is called,
   * so they will not appear in practice.
   *
   * @param stageJobs       jobs belonging to this stage
   * @param jobDependencies pipeline-level dependency map
   * @return ordered list of waves; each wave is a non-empty list of jobs
   */
  List<List<Job>> computeWaves(List<Job> stageJobs,
                               Map<String, List<String>> jobDependencies) {
    Map<String, List<String>> deps =
        (jobDependencies != null) ? jobDependencies : Collections.emptyMap();
    Map<String, Integer> waveIndex = new HashMap<>();
    Map<String, Job> jobsByName = new HashMap<>();
    for (Job job : stageJobs) {
      jobsByName.put(job.getName(), job);
    }

    for (Job job : stageJobs) {
      assignWave(job.getName(), deps, jobsByName, waveIndex);
    }

    int maxWave = waveIndex.values().stream().mapToInt(Integer::intValue).max().orElse(0);

    List<List<Job>> waves = new ArrayList<>();
    for (int i = 0; i <= maxWave; i++) {
      waves.add(new ArrayList<>());
    }
    for (Job job : stageJobs) {
      waves.get(waveIndex.get(job.getName())).add(job);
    }

    waves.removeIf(List::isEmpty);
    return waves;
  }

  /**
   * Recursively computes and memoises the wave index for a single job.
   *
   * @param jobName         the job whose wave index should be assigned
   * @param jobDependencies pipeline-level dependency map
   * @param jobsByName      in-stage jobs by name (used to filter cross-stage deps)
   * @param waveIndex       memoisation map; updated in-place
   * @return the assigned wave index
   */
  private int assignWave(String jobName,
                         Map<String, List<String>> jobDependencies,
                         Map<String, Job> jobsByName,
                         Map<String, Integer> waveIndex) {
    if (waveIndex.containsKey(jobName)) {
      return waveIndex.get(jobName);
    }

    List<String> deps = jobDependencies.getOrDefault(jobName, Collections.emptyList());
    int wave = 0;
    for (String dep : deps) {
      if (jobsByName.containsKey(dep)) {
        wave = Math.max(wave, assignWave(dep, jobDependencies, jobsByName, waveIndex) + 1);
      }
    }

    waveIndex.put(jobName, wave);
    return wave;
  }

  /**
   * Executes one wave of jobs, running eligible jobs concurrently and skipping
   * those whose dependencies were not satisfied.
   *
   * <p>For each job in the wave:
   * <ul>
   *   <li>If {@link #canRun} returns {@code false}, the job is marked
   *       {@link JobStatus#SKIPPED} and its DB row updated immediately on the
   *       calling thread.</li>
   *   <li>Otherwise, the job is submitted to the internal thread pool via
   *       {@link CompletableFuture#supplyAsync}.</li>
   * </ul>
   *
   * <p>{@link CompletableFuture#allOf} is called after all futures are submitted
   * so the method blocks until every job in the wave has produced a result.
   * Each result is written to {@code statusMap} before returning.
   *
   * @param wave          jobs in this wave
   * @param workspacePath shared workspace for all containers
   * @param stageIds      job name → DB row id map for this stage
   * @param statusMap     cumulative status map; read to evaluate dependencies,
   *                      written after each job completes
   * @param jobsByName    all jobs in the stage by name (for {@code failures} lookup)
   * @param stageName     stage name for MDC and metrics
   * @param pipelineName  pipeline name for MDC and metrics
   * @param runNo         run number for MDC and metrics
   * @return results for every job in this wave, in submission order
   */
  private List<JobResult> executeWave(List<Job> wave,
                                      Path workspacePath,
                                      Map<String, Long> stageIds,
                                      Map<String, JobStatus> statusMap,
                                      Map<String, Job> jobsByName,
                                      String stageName,
                                      String pipelineName,
                                      int runNo) {
    Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
    if (mdcSnapshot == null) {
      mdcSnapshot = Collections.emptyMap();
    }
    final Map<String, String> callerMdc = mdcSnapshot;
    final Span callerSpan = tracer.currentSpan();

    List<CompletableFuture<JobResult>> futures = new ArrayList<>();

    for (Job job : wave) {
      Long jobRunId = stageIds.get(job.getName());

      if (!canRun(job, statusMap, jobsByName)) {
        persistenceService.updateJobRun(jobRunId, ExecutionStatus.FAILED).block();
        JobResult skipped = skippedResult(job.getName());
        statusMap.put(job.getName(), JobStatus.SKIPPED);
        Counter.builder("cicd.job.runs")
            .tag("pipeline", pipelineName)
            .tag("stage", stageName)
            .tag("job_name", job.getName())
            .tag("run_no", String.valueOf(runNo))
            .tag("status", "skipped")
            .register(meterRegistry)
            .increment();
        futures.add(CompletableFuture.completedFuture(skipped));
        log.info("Job '{}' skipped", job.getName());
      } else {
        CompletableFuture<JobResult> future = CompletableFuture.supplyAsync(
            () -> executeJobAsync(job, workspacePath, jobRunId,
                stageName, pipelineName, runNo, callerMdc, callerSpan),
            jobExecutor
        );
        futures.add(future);
      }
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    List<JobResult> results = new ArrayList<>();
    for (CompletableFuture<JobResult> future : futures) {
      JobResult result = future.join();
      results.add(result);
      statusMap.put(result.getJobName(), result.getStatus());
    }

    return results;
  }

  /**
   * Executes a single job inside a Docker container on an async thread.
   *
   * <p>This method is the body of each {@link CompletableFuture} submitted by
   * {@link #executeWave}. It:
   * <ol>
   *   <li>Restores the MDC context captured from the calling thread</li>
   *   <li>Starts a Micrometer tracing span for the job</li>
   *   <li>Transitions the job DB row to {@code RUNNING}</li>
   *   <li>Delegates execution to {@link DockerExecutor}</li>
   *   <li>Updates the job DB row with the final status</li>
   *   <li>Records job-level metrics</li>
   * </ol>
   *
   * <p>Any unexpected exception from {@link DockerExecutor} is caught and
   * converted to a {@link JobStatus#FAILED} result so the future never completes
   * exceptionally.
   *
   * @param job          the job to execute
   * @param workspacePath workspace directory to bind-mount into the container
   * @param jobRunId     DB id of the {@code job_runs} row to update
   * @param stageName    stage name for MDC and metrics
   * @param pipelineName pipeline name for MDC and metrics
   * @param runNo        run number for MDC and metrics
   * @param mdcSnapshot  MDC context captured from the calling thread
   * @return the job result; never {@code null}
   */
  private JobResult executeJobAsync(Job job,
                                    Path workspacePath,
                                    Long jobRunId,
                                    String stageName,
                                    String pipelineName,
                                    int runNo,
                                    Map<String, String> mdcSnapshot,
                                    Span parentSpan) {
    MDC.setContextMap(mdcSnapshot);
    MDC.put("pipeline", pipelineName);
    MDC.put("run_no", String.valueOf(runNo));
    MDC.put("stage", stageName);
    MDC.put("job", job.getName());

    long jobStartNanos = System.nanoTime();
    Span jobSpan = (parentSpan != null ? tracer.nextSpan(parentSpan) : tracer.nextSpan())
        .name("job.execute")
        .tag("job.name", job.getName());

    try (Tracer.SpanInScope ignored = tracer.withSpan(jobSpan.start())) {
      persistenceService.markJobRunning(jobRunId).block();
      log.info("Executing job '{}'", job.getName());

      JobResult result;
      try {
        result = dockerExecutor.executeJob(job, workspacePath);
      } catch (Exception e) {
        log.error("Docker error for job '{}': {}", job.getName(), e.getMessage(), e);
        result = JobResult.builder()
            .jobName(job.getName())
            .status(JobStatus.FAILED)
            .output("Docker execution error: " + e.getMessage())
            .exitCode(1)
            .executionTime(Duration.ZERO)
            .build();
      }

      log.info("Job '{}' finished: {}", job.getName(), result.getStatus());
      persistenceService.updateJobRun(jobRunId, toDbStatus(result.getStatus())).block();
      jobSpan.tag("job.status", result.getStatus().name());

      Counter.builder("cicd.job.runs")
          .tag("pipeline", pipelineName)
          .tag("stage", stageName)
          .tag("job_name", job.getName())
          .tag("run_no", String.valueOf(runNo))
          .tag("status", toStatusLabel(result.getStatus()))
          .register(meterRegistry)
          .increment();

      if (result.getStatus() != JobStatus.SKIPPED) {
        Timer.builder("cicd.job.duration")
            .tag("pipeline", pipelineName)
            .tag("stage", stageName)
            .tag("job_name", job.getName())
            .tag("run_no", String.valueOf(runNo))
            .register(meterRegistry)
            .record(System.nanoTime() - jobStartNanos, TimeUnit.NANOSECONDS);
      }

      return result;

    } finally {
      jobSpan.end();
      MDC.clear();
    }
  }

  /**
   * Returns {@code true} if all of the job's declared dependencies are satisfied.
   *
   * <p>A job with no {@code needs} is always eligible. A dependency is considered
   * satisfied if its status is {@link JobStatus#COMPLETED}, or if the dependency
   * job is marked {@code failures: true} (meaning its outcome does not block
   * downstream jobs regardless of whether it succeeded or failed).
   *
   * @param job        the job whose eligibility is being checked
   * @param statusMap  map of job name → completed status for jobs already run
   * @param jobsByName map of job name → job definition for the current stage
   * @return {@code true} if the job may execute
   */
  private static boolean canRun(Job job,
                                 Map<String, JobStatus> statusMap,
                                 Map<String, Job> jobsByName) {
    List<String> needs = job.getNeeds();
    if (needs == null || needs.isEmpty()) {
      return true;
    }
    return needs.stream().allMatch(dep -> {
      JobStatus status = statusMap.getOrDefault(dep, JobStatus.SKIPPED);
      if (status == JobStatus.COMPLETED) {
        return true;
      }
      Job depJob = jobsByName.get(dep);
      return depJob != null && depJob.isFailures();
    });
  }

  /**
   * Builds a {@link JobStatus#SKIPPED} result for a job that was not executed.
   *
   * @param jobName the name of the skipped job
   * @return a skipped {@link JobResult}
   */
  private static JobResult skippedResult(String jobName) {
    return JobResult.builder()
        .jobName(jobName)
        .status(JobStatus.SKIPPED)
        .output(SKIPPED_MESSAGE)
        .exitCode(0)
        .executionTime(null)
        .build();
  }

  /**
   * Maps a {@link JobStatus} to the {@link ExecutionStatus} stored in the database.
   *
   * @param status the job status to convert
   * @return {@link ExecutionStatus#SUCCESS} for {@link JobStatus#COMPLETED},
   *         {@link ExecutionStatus#FAILED} for all other values
   */
  private static ExecutionStatus toDbStatus(JobStatus status) {
    return status == JobStatus.COMPLETED ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED;
  }

  /**
   * Maps a {@link JobStatus} to a short label string for use as a metric tag.
   *
   * @param status the job status to convert
   * @return {@code "success"}, {@code "failure"}, {@code "skipped"}, or the
   *         lower-cased status name for any other value
   */
  private static String toStatusLabel(JobStatus status) {
    return switch (status) {
      case COMPLETED -> "success";
      case FAILED -> "failure";
      case SKIPPED -> "skipped";
      default -> status.name().toLowerCase();
    };
  }
}
