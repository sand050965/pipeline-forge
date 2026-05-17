package edu.northeastern.cs7580.cicd.executionservice.service;

import edu.northeastern.cs7580.cicd.executionservice.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionMessage;
import edu.northeastern.cs7580.cicd.executionservice.entity.PipelineRunEntity;
import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionResult;
import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import edu.northeastern.cs7580.cicd.executionservice.model.JobResult;
import edu.northeastern.cs7580.cicd.executionservice.model.JobStatus;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Orchestrates parallel pipeline execution and persists results to the database.
 *
 * <p><b>Execution Flow:</b>
 * <ol>
 *   <li>Upsert the pipeline definition row in {@code pipelines}</li>
 *   <li>Insert a {@code pipeline_runs} row with {@code status=RUNNING}</li>
 *   <li>Pre-create all stage/job rows as {@code PENDING} (for immediate UI visibility)</li>
 *   <li>For each stage: transition {@code PENDING → RUNNING}, delegate job execution
 *       to {@link ParallelStageExecutor}, then update to {@code SUCCESS} or
 *       {@code FAILED}</li>
 *   <li>Within each stage, {@link ParallelStageExecutor} groups jobs into dependency
 *       waves and runs each wave concurrently; skipped jobs go directly from
 *       {@code PENDING} to {@code FAILED} without a {@code RUNNING} transition</li>
 *   <li>Update the {@code pipeline_runs} row with the final status and
 *       {@code end_time}</li>
 * </ol>
 *
 * <p><b>Failure Behaviour:</b>
 * Within a stage, a failed job causes only its direct and transitive dependents
 * to be skipped. Independent jobs in later waves are unaffected. If any
 * non-optional job fails across the entire stage, all subsequent stages are
 * fully skipped.
 *
 * <p><b>Fail-open DB Writes:</b>
 * All database operations are delegated to {@link ExecutionPersistenceService}, which
 * catches every exception internally. A DB outage produces warning logs but never
 * interrupts pipeline execution. When the DB is unavailable the pipeline still runs
 * and the returned {@link ExecutionResult} carries {@code runNumber=0}.
 *
 * <p><b>Blocking Operations:</b>
 * {@link ParallelStageExecutor} uses a synchronous Docker library. The entire
 * {@link #executeWithWorkspace} method is offloaded to
 * {@link Schedulers#boundedElastic()} so it never blocks the R2DBC event loop.
 * Persistence calls inside that method use {@code .block()} safely because the
 * method already runs on a bounded-elastic thread.
 *
 * <p><b>Thread Safety:</b>
 * Stateless. Each invocation of {@link #executeSequential} is safe to call
 * concurrently.
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "ExecutionPersistenceService, ParallelStageExecutor, Tracer, and "
        + "MeterRegistry are Spring-managed singleton beans; "
        + "storing the references is intentional and safe."
)
@Service
public class ExecutionService {

  private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
  private static final String SKIPPED_MESSAGE = "Job skipped due to previous job failure";
  private static final String STAGE_RUN_ID_KEY = "__stageRunId__";

  private final ExecutionPersistenceService persistenceService;
  private final ParallelStageExecutor parallelStageExecutor;
  private final Tracer tracer;
  private final MeterRegistry meterRegistry;

  /**
   * Constructs an {@code ExecutionService} with the required collaborators.
   *
   * @param persistenceService   writes and updates execution records in PostgreSQL
   * @param parallelStageExecutor executes jobs within a stage using parallel wave scheduling
   * @param tracer               Micrometer tracer for creating stage spans
   * @param meterRegistry        Micrometer registry for recording pipeline and stage metrics
   */
  public ExecutionService(ExecutionPersistenceService persistenceService,
                          ParallelStageExecutor parallelStageExecutor,
                          Tracer tracer,
                          MeterRegistry meterRegistry) {
    this.persistenceService = persistenceService;
    this.parallelStageExecutor = parallelStageExecutor;
    this.tracer = tracer;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Executes the provided plan sequentially and persists all results to the database.
   *
   * <p>The reactive chain is:
   * <pre>
   *   upsertPipeline
   *     → createPipelineRun
   *     → preCreateStagesAndJobs
   *     → [boundedElastic] executeWithWorkspace   ← blocking Docker calls here
   *     → updatePipelineRun
   * </pre>
   *
   * <p>If the DB is unavailable at any step before {@code executeWithWorkspace}
   * (the chain emits empty), a fallback path executes the pipeline without
   * persistence and returns {@code runNumber=0}.
   *
   * <p>The workspace directory is prepared by the caller
   * ({@code ExecutionController}) before invoking this method. Cleanup is also
   * the caller's responsibility.
   *
   * @param plan          validated execution plan produced by the pipeline library
   * @param gitMetadata   repository metadata (URL, branch, commit hash)
   * @param pipelineName  logical name of the pipeline
   * @param workspacePath path to the pre-cloned repository workspace
   * @return {@code Mono} emitting the complete execution result, including all job
   *         outcomes and the database-assigned {@code run_no}
   */
  public Mono<ExecutionResult> executeSequential(ExecutionPlan plan,
                                                 GitMetadata gitMetadata,
                                                 String pipelineName,
                                                 Path workspacePath) {
    return persistenceService.upsertPipeline(pipelineName, gitMetadata)

        .flatMap(pipelineId -> persistenceService.createPipelineRun(pipelineId, gitMetadata,
            ExecutionStatus.RUNNING))

        .flatMap(pipelineRun ->
            persistenceService.preCreateStagesAndJobs(pipelineRun.getId(), plan)
                .map(idMap -> new RunContext(pipelineRun, idMap))
        )
        .flatMap(ctx ->
            Mono.fromCallable(() -> executeWithWorkspace(plan, workspacePath, ctx.idMap,
                    pipelineName, ctx.pipelineRun.getRunNo()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> {
                  ExecutionStatus finalStatus = result.isSuccess()
                      ? ExecutionStatus.SUCCESS
                      : ExecutionStatus.FAILED;
                  return persistenceService.updatePipelineRun(ctx.pipelineRun.getId(), finalStatus)
                      .thenReturn(ExecutionResult.builder()
                          .jobResults(result.getJobResults())
                          .success(result.isSuccess())
                          .failedJobName(result.getFailedJobName())
                          .runNumber(ctx.pipelineRun.getRunNo())
                          .build());
                })
        )
        .switchIfEmpty(Mono.fromCallable(
                () -> executeWithWorkspace(plan, workspacePath, Map.of(), pipelineName, 0))
            .subscribeOn(Schedulers.boundedElastic())
            .map(result -> ExecutionResult.builder()
                .jobResults(result.getJobResults())
                .success(result.isSuccess())
                .failedJobName(result.getFailedJobName())
                .runNumber(0)
                .build())
        );
  }

  /**
   * Sets up the DB records needed for an async pipeline run and returns the data
   * required to build a {@link PipelineExecutionMessage}.
   *
   * <p>This is the first half of the async execution flow, called by the HTTP
   * endpoint before publishing to RabbitMQ:
   * <pre>
   *   upsertPipeline
   *     → createPipelineRun (status=PENDING)
   *     → preCreateStagesAndJobs (all PENDING)
   *     → return (runId, runNo, idMap)
   * </pre>
   *
   * <p>If any DB step fails the chain emits empty, which the controller treats
   * as a 500 — no orphaned records are left because nothing was committed.</p>
   *
   * @param plan         validated execution plan
   * @param gitMetadata  repository metadata
   * @param pipelineName logical name of the pipeline
   * @return {@code Mono} emitting the message payload needed to publish to the
   *         queue, or empty on DB failure
   */
  public Mono<PipelineExecutionMessage> initializePipelineRun(ExecutionPlan plan,
                                                              GitMetadata gitMetadata,
                                                              String pipelineName,
                                                              String pipelineFilePath) {
    return persistenceService.upsertPipeline(pipelineName, gitMetadata)
        .flatMap(pipelineId -> persistenceService.createPipelineRun(pipelineId, gitMetadata,
            ExecutionStatus.PENDING))
        .flatMap(pipelineRun ->
            persistenceService.preCreateStagesAndJobs(pipelineRun.getId(), plan)
                .map(idMap -> PipelineExecutionMessage.builder()
                    .runId(pipelineRun.getId())
                    .runNo(pipelineRun.getRunNo())
                    .pipelineName(pipelineName)
                    .pipelineFilePath(pipelineFilePath)
                    .gitMetadata(gitMetadata)
                    .idMap(idMap)
                    .build())
        );
  }

  /**
   * Executes a pipeline run picked up from the RabbitMQ queue.
   *
   * <p>This is the second half of the async execution flow, called by the
   * consumer on a separate thread:
   * <pre>
   *   markPipelineRunRunning
   *     → [blocking] executeWithWorkspace   ← Docker calls here
   *     → updatePipelineRun (SUCCESS or FAILED)
   * </pre>
   *
   * <p>This method is intentionally synchronous — it is always called from the
   * consumer, which runs on a RabbitMQ listener thread, so blocking is safe.</p>
   *
   * @param plan          validated execution plan (re-parsed by the consumer)
   * @param workspacePath path to the freshly-cloned repository workspace
   * @param runId         surrogate PK of the {@code pipeline_runs} row to update
   * @param idMap         pre-created stage/job DB ids from {@link #initializePipelineRun}
   * @param pipelineName  logical name of the pipeline (used for MDC log fields)
   * @param runNo         pipeline run number (used for MDC log fields)
   */
  public void executeFromMessage(ExecutionPlan plan,
                                 Path workspacePath,
                                 Long runId,
                                 Map<String, Map<String, Long>> idMap,
                                 String pipelineName,
                                 int runNo) {
    persistenceService.markPipelineRunRunning(runId).block();

    long startNanos = System.nanoTime();
    ExecutionResult result = executeWithWorkspace(plan, workspacePath, idMap, pipelineName, runNo);
    long durationNanos = System.nanoTime() - startNanos;

    ExecutionStatus finalStatus = result.isSuccess()
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;
    persistenceService.updatePipelineRun(runId, finalStatus).block();

    String statusLabel = result.isSuccess() ? "success" : "failure";
    Counter.builder("cicd.pipeline.runs")
        .tag("pipeline", pipelineName)
        .tag("status", statusLabel)
        .register(meterRegistry)
        .increment();
    Timer.builder("cicd.pipeline.duration")
        .tag("pipeline", pipelineName)
        .register(meterRegistry)
        .record(durationNanos, TimeUnit.NANOSECONDS);

    MDC.put("pipeline", pipelineName);
    MDC.put("run_no", String.valueOf(runNo));
    MDC.put("status", statusLabel);
    MDC.put("duration_ms", String.valueOf(TimeUnit.NANOSECONDS.toMillis(durationNanos)));
    log.info("Pipeline run completed: pipeline={}, runNo={}, status={}", pipelineName, runNo,
        statusLabel);
    MDC.remove("pipeline");
    MDC.remove("run_no");
    MDC.remove("status");
    MDC.remove("duration_ms");
  }

  /**
   * Iterates over every stage in the plan, delegating job execution within each
   * stage to {@link ParallelStageExecutor} and recording stage-level outcomes.
   *
   * <p>This method is intentionally synchronous. It is always invoked via
   * {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())}
   * so it never blocks the R2DBC event-loop thread. Persistence calls inside
   * this method use {@code .block()} safely because the method already runs on
   * a bounded-elastic thread.
   *
   * <p>Stages execute sequentially. If any non-optional job fails in a stage,
   * {@code pipelineFailed} is set and all subsequent stages are entirely skipped.
   * Within a stage, {@link ParallelStageExecutor} ensures that only the direct
   * and transitive dependents of a failed job are skipped — independent jobs
   * in later waves continue to run.
   *
   * @param plan          the execution plan
   * @param workspacePath path to the cloned repository workspace
   * @param idMap         stage name → ({@code "__stageRunId__"} → stage DB id,
   *                      job name → job DB id); produced by
   *                      {@link ExecutionPersistenceService#preCreateStagesAndJobs}
   * @param pipelineName  pipeline name for logging and metrics
   * @param runNo         run number for logging and metrics
   * @return the complete execution result
   */
  private ExecutionResult executeWithWorkspace(ExecutionPlan plan,
                                               Path workspacePath,
                                               Map<String, Map<String, Long>> idMap,
                                               String pipelineName,
                                               int runNo) {
    List<JobResult> allJobResults = new ArrayList<>();
    Map<String, JobStatus> statusMap = new ConcurrentHashMap<>();
    AtomicBoolean pipelineFailed = new AtomicBoolean(false);
    AtomicReference<String> failedJobName = new AtomicReference<>(null);

    log.info("Starting pipeline execution: {} stage(s)", plan.getStages().size());

    for (StageExecution stage : plan.getStages()) {
      Map<String, Long> stageIds = idMap.getOrDefault(stage.getStageName(), Map.of());
      Long stageRunId = stageIds.get(STAGE_RUN_ID_KEY);
      boolean stageFailed = false;
      boolean stageSkipped = pipelineFailed.get();
      long stageStartNanos = System.nanoTime();

      Span stageSpan = tracer.nextSpan()
          .name("stage.execute")
          .tag("stage.name", stage.getStageName());
      try (Tracer.SpanInScope stageScope = tracer.withSpan(stageSpan.start())) {

        if (!stageSkipped) {
          persistenceService.markStageRunning(stageRunId).block();
        }

        if (stageSkipped) {
          for (Job job : stage.getJobs()) {
            Long jobRunId = stageIds.get(job.getName());
            persistenceService.updateJobRun(jobRunId, ExecutionStatus.FAILED).block();
            JobResult skipped = skippedResult(job.getName());
            allJobResults.add(skipped);
            statusMap.put(job.getName(), JobStatus.SKIPPED);
            log.info("Job '{}' skipped", job.getName());
          }
          stageFailed = true;
        } else {
          List<JobResult> stageResults = parallelStageExecutor.executeStage(
              stage, plan.getJobDependencies(), workspacePath,
              stageIds, statusMap, pipelineName, runNo);
          allJobResults.addAll(stageResults);

          Map<String, Job> jobsByName = new HashMap<>();
          for (Job job : stage.getJobs()) {
            jobsByName.put(job.getName(), job);
          }
          for (JobResult result : stageResults) {
            if (result.getStatus() == JobStatus.FAILED) {
              Job job = jobsByName.get(result.getJobName());
              if (job != null && !job.isFailures()) {
                pipelineFailed.set(true);
                stageFailed = true;
                failedJobName.compareAndSet(null, result.getJobName());
              }
            }
          }
        }

        ExecutionStatus stageStatus = (stageFailed || stageSkipped)
            ? ExecutionStatus.FAILED
            : ExecutionStatus.SUCCESS;
        persistenceService.updateStageRun(stageRunId, stageStatus).block();
        if (!stageSkipped) {
          Timer.builder("cicd.stage.duration")
              .tag("pipeline", pipelineName)
              .tag("stage", stage.getStageName())
              .tag("run_no", String.valueOf(runNo))
              .register(meterRegistry)
              .record(System.nanoTime() - stageStartNanos, TimeUnit.NANOSECONDS);
        }
        stageSpan.tag("stage.status", stageStatus.name());

      } finally {
        stageSpan.end();
      }
    }

    boolean success = !pipelineFailed.get();
    if (success) {
      log.info("All jobs completed successfully");
    }

    return ExecutionResult.builder()
        .jobResults(allJobResults)
        .success(success)
        .failedJobName(failedJobName.get())
        .build();
  }

  /**
   * Builds a {@link JobStatus#SKIPPED} result for a job that was not executed
   * because its entire stage was skipped.
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
   * Carries the pipeline run entity and the pre-created stage/job id map through
   * the reactive chain so both are available when {@link #executeWithWorkspace}
   * is invoked.
   *
   * @param pipelineRun the saved pipeline run entity (with {@code id} and
   *                    {@code runNo} populated)
   * @param idMap       stage name → (key → DB id) map produced by
   *                    {@link ExecutionPersistenceService#preCreateStagesAndJobs}
   */
  private record RunContext(PipelineRunEntity pipelineRun,
                            Map<String, Map<String, Long>> idMap) {
  }
}
