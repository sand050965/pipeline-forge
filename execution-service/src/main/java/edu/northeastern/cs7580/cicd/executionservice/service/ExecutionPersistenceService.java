package edu.northeastern.cs7580.cicd.executionservice.service;

import edu.northeastern.cs7580.cicd.executionservice.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.executionservice.entity.JobRunEntity;
import edu.northeastern.cs7580.cicd.executionservice.entity.PipelineEntity;
import edu.northeastern.cs7580.cicd.executionservice.entity.PipelineRunEntity;
import edu.northeastern.cs7580.cicd.executionservice.entity.StageRunEntity;
import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import edu.northeastern.cs7580.cicd.executionservice.repository.JobRunRepository;
import edu.northeastern.cs7580.cicd.executionservice.repository.PipelineRepository;
import edu.northeastern.cs7580.cicd.executionservice.repository.PipelineRunRepository;
import edu.northeastern.cs7580.cicd.executionservice.repository.StageRunRepository;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handles all database writes for pipeline execution records.
 *
 * <p>This service persists every level of execution data — pipeline, stage, and job —
 * to PostgreSQL so the Report Service can query them. It uses a <b>fail-open</b>
 * strategy: every public method wraps its database call in a try/catch so that a DB
 * outage or transient error never interrupts pipeline execution. Failures are logged
 * as warnings instead.
 *
 * <p>{@code status} values are stored as plain {@code VARCHAR} strings matching the
 * {@link ExecutionStatus} enum name (e.g. {@code "RUNNING"}, {@code "SUCCESS"}).
 * This avoids R2DBC type-binding issues with custom PostgreSQL ENUM types.
 *
 * <p><b>Typical call sequence per execution:</b>
 * <ol>
 *   <li>{@link #upsertPipeline} — find or create the pipeline definition row</li>
 *   <li>{@link #createPipelineRun} — open a run record with {@code status=RUNNING}</li>
 *   <li>{@link #preCreateStagesAndJobs} — insert all stage/job rows as {@code PENDING}
 *       so the UI can display the full plan immediately; returns a map of name → DB id</li>
 *   <li>For each stage: {@link #markStageRunning}, execute all jobs,
 *       then {@link #updateStageRun}</li>
 *   <li>For each job: {@link #markJobRunning}, execute in Docker,
 *       then {@link #updateJobRun}</li>
 *   <li>{@link #updatePipelineRun} — close the run record with final status</li>
 * </ol>
 *
 * @see ExecutionService
 */
@Service
public class ExecutionPersistenceService {

  private static final Logger log = LoggerFactory.getLogger(ExecutionPersistenceService.class);

  private final PipelineRepository pipelineRepo;
  private final PipelineRunRepository pipelineRunRepo;
  private final StageRunRepository stageRunRepo;
  private final JobRunRepository jobRunRepo;

  /**
   * Constructs an {@code ExecutionPersistenceService} with the required repositories.
   *
   * @param pipelineRepo    repository for the {@code pipelines} table
   * @param pipelineRunRepo repository for the {@code pipeline_runs} table
   * @param stageRunRepo    repository for the {@code stage_runs} table
   * @param jobRunRepo      repository for the {@code job_runs} table
   */
  public ExecutionPersistenceService(PipelineRepository pipelineRepo,
                                     PipelineRunRepository pipelineRunRepo,
                                     StageRunRepository stageRunRepo,
                                     JobRunRepository jobRunRepo) {
    this.pipelineRepo = pipelineRepo;
    this.pipelineRunRepo = pipelineRunRepo;
    this.stageRunRepo = stageRunRepo;
    this.jobRunRepo = jobRunRepo;
  }

  /**
   * Finds an existing pipeline definition row or creates one if none exists.
   *
   * <p>The pipeline is keyed by {@code (repo_id, pipeline_name)} where {@code repo_id}
   * is the SHA-256 hash of the repository URL.
   *
   * @param pipelineName logical name of the pipeline
   * @param git          Git metadata containing the repository URL
   * @return {@code Mono} emitting the surrogate ID of the pipeline row,
   *         or empty on DB failure
   */
  public Mono<Long> upsertPipeline(String pipelineName, GitMetadata git) {
    String repoId = sha256(git.getRepositoryUrl());
    return pipelineRepo.findByRepoIdAndPipelineName(repoId, pipelineName)
        .map(PipelineEntity::getId)
        .switchIfEmpty(Mono.defer(() -> {
          OffsetDateTime now = OffsetDateTime.now();
          PipelineEntity entity = PipelineEntity.builder()
              .repoId(repoId)
              .pipelineName(pipelineName)
              .gitRepo(git.getRepositoryUrl())
              .createdAt(now)
              .updatedAt(now)
              .build();
          return pipelineRepo.save(entity).map(PipelineEntity::getId);
        }))
        .onErrorResume(e -> {
          log.warn("DB: failed to upsert pipeline '{}' — {}", pipelineName, e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Inserts a new {@code pipeline_runs} row with the given initial status and returns
   * the saved entity.
   *
   * <p>{@code run_no} is assigned automatically by a PostgreSQL {@code BEFORE INSERT}
   * trigger. A placeholder value of {@code 0} is passed on INSERT; the trigger replaces
   * it with the correct per-pipeline sequence number. The saved entity is re-fetched
   * after INSERT so the returned {@link PipelineRunEntity} always contains the
   * trigger-assigned {@code runNo}.
   *
   * <p>Pass {@link ExecutionStatus#PENDING} when the run is being enqueued for async
   * execution (the consumer will transition it to {@link ExecutionStatus#RUNNING}).
   * Pass {@link ExecutionStatus#RUNNING} for synchronous execution paths.
   *
   * @param pipelineId    surrogate ID of the parent pipeline; {@code null} is a no-op
   * @param git           Git metadata (branch and commit hash) to record on the run
   * @param initialStatus the initial status to persist (typically {@code PENDING} or
   *                      {@code RUNNING})
   * @return {@code Mono} emitting the saved {@link PipelineRunEntity} with {@code id}
   *         and {@code runNo} populated, or empty on DB failure
   */
  public Mono<PipelineRunEntity> createPipelineRun(Long pipelineId, GitMetadata git,
                                                   ExecutionStatus initialStatus) {
    if (pipelineId == null) {
      return Mono.empty();
    }

    OffsetDateTime now = OffsetDateTime.now();
    PipelineRunEntity entity = PipelineRunEntity.builder()
        .pipelineId(pipelineId)
        .status(initialStatus.name())
        .gitBranch(git.getBranch())
        .gitHash(git.getCommitHash())
        .startTime(now)
        .createdAt(now)
        .updatedAt(now)
        .build();

    return pipelineRunRepo.save(entity)
        .flatMap(saved -> pipelineRunRepo.findById(saved.getId()))
        .onErrorResume(e -> {
          log.warn("DB: failed to create pipeline_run — {}", e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Updates the {@code pipeline_runs} row with the final status and {@code end_time}.
   *
   * @param runId  surrogate ID of the row to update; {@code null} is a no-op
   * @param status final status — {@link ExecutionStatus#SUCCESS} or
   *               {@link ExecutionStatus#FAILED}
   * @return {@code Mono} completing when the update finishes; errors are swallowed
   */
  public Mono<Void> updatePipelineRun(Long runId, ExecutionStatus status) {
    if (runId == null) {
      return Mono.empty();
    }
    return pipelineRunRepo.findById(runId)
        .flatMap(entity -> {
          entity.setStatus(status.name());
          entity.setEndTime(OffsetDateTime.now());
          entity.setUpdatedAt(OffsetDateTime.now());
          return pipelineRunRepo.save(entity);
        })
        .then()
        .onErrorResume(e -> {
          log.warn("DB: failed to update pipeline_run id={} — {}", runId, e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Deletes a {@code pipeline_runs} row and all its associated {@code stage_runs}
   * and {@code job_runs} rows (via {@code ON DELETE CASCADE}).
   *
   * <p>Called by the HTTP endpoint when the RabbitMQ publish step fails after
   * DB records have already been created. This ensures no orphaned {@code PENDING}
   * rows are left behind when a submission cannot be queued.</p>
   *
   * @param runId surrogate ID of the row to delete; {@code null} is a no-op
   * @return {@code Mono} completing when the delete finishes; errors are swallowed
   */
  public Mono<Void> rollbackPipelineRun(Long runId) {
    if (runId == null) {
      return Mono.empty();
    }
    return pipelineRunRepo.deleteById(runId)
        .onErrorResume(e -> {
          log.warn("DB: failed to rollback pipeline_run id={} — {}", runId, e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Updates the {@code trace_id} column on a {@code pipeline_runs} row with the
   * real OpenTelemetry trace ID captured from the root span.
   *
   * <p>Called by the consumer immediately after starting the root span so that
   * the OTel trace ID replaces the placeholder set at run-creation time.</p>
   *
   * @param runId   surrogate ID of the row to update; {@code null} is a no-op
   * @param traceId 32-character lowercase hex OTel trace ID
   * @return {@code Mono} completing when the update finishes; errors are swallowed
   */
  public Mono<Void> updateTraceId(Long runId, String traceId) {
    if (runId == null) {
      return Mono.empty();
    }
    return pipelineRunRepo.findById(runId)
        .flatMap(entity -> {
          entity.setTraceId(traceId);
          entity.setUpdatedAt(OffsetDateTime.now());
          return pipelineRunRepo.save(entity);
        })
        .then()
        .onErrorResume(e -> {
          log.warn("DB: failed to update trace_id for pipeline_run id={} — {}", runId,
              e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Transitions a {@code pipeline_runs} row from {@code PENDING} to {@code RUNNING}
   * and records the actual start time.
   *
   * <p>Called by the consumer at the moment it begins executing the pipeline,
   * after picking up the message from the queue.</p>
   *
   * @param runId surrogate ID of the row to update; {@code null} is a no-op
   * @return {@code Mono} completing when the update finishes; errors are swallowed
   */
  public Mono<Void> markPipelineRunRunning(Long runId) {
    if (runId == null) {
      return Mono.empty();
    }
    return pipelineRunRepo.findById(runId)
        .flatMap(entity -> {
          entity.setStatus(ExecutionStatus.RUNNING.name());
          entity.setStartTime(OffsetDateTime.now());
          entity.setUpdatedAt(OffsetDateTime.now());
          return pipelineRunRepo.save(entity);
        })
        .then()
        .onErrorResume(e -> {
          log.warn("DB: failed to mark pipeline_run id={} as RUNNING — {}", runId,
              e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Marks a {@code pipeline_runs} row as {@code FAILED} with an internal error message.
   *
   * <p>Called by the consumer's catch block when an unexpected exception occurs
   * during execution (i.e. not a job-level failure). The {@code errorMessage} is
   * surfaced to the user via {@code cicd status} so they know the failure was
   * infrastructure-related and should file an issue.</p>
   *
   * @param runId        surrogate ID of the row to update; {@code null} is a no-op
   * @param errorMessage human-readable description of the internal error
   * @return {@code Mono} completing when the update finishes; errors are swallowed
   */
  public Mono<Void> markPipelineRunFailed(Long runId, String errorMessage) {
    if (runId == null) {
      return Mono.empty();
    }
    return pipelineRunRepo.findById(runId)
        .flatMap(entity -> {
          entity.setStatus(ExecutionStatus.FAILED.name());
          entity.setErrorMessage(errorMessage);
          entity.setEndTime(OffsetDateTime.now());
          entity.setUpdatedAt(OffsetDateTime.now());
          return pipelineRunRepo.save(entity);
        })
        .then()
        .onErrorResume(e -> {
          log.warn("DB: failed to mark pipeline_run id={} as FAILED — {}", runId,
              e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Pre-creates all stage and job rows as {@code PENDING} so the UI can render the
   * full execution plan before any job starts running.
   *
   * <p>Returns a two-level map:
   * <pre>
   *   stageName → { "__stageRunId__" → id, jobName → id, … }
   * </pre>
   * The caller must pass these IDs to {@link #markStageRunning}, {@link #updateStageRun},
   * {@link #markJobRunning}, and {@link #updateJobRun} instead of creating new rows
   * during execution.
   *
   * @param pipelineRunId parent pipeline run ID; {@code null} returns an empty map
   * @param plan          execution plan containing all stages and jobs
   * @return {@code Mono} emitting the id map; emits an empty map on DB failure
   */
  public Mono<Map<String, Map<String, Long>>> preCreateStagesAndJobs(Long pipelineRunId,
                                                                     ExecutionPlan plan) {
    if (pipelineRunId == null) {
      return Mono.just(new LinkedHashMap<>());
    }

    return Flux.fromIterable(plan.getStages())
        .concatMap(stage ->
            insertStageRun(pipelineRunId, stage.getStageName(), ExecutionStatus.PENDING)
                .flatMap(stageRunId ->
                    Flux.fromIterable(stage.getJobs())
                        .concatMap(job ->
                            insertJobRun(stageRunId, job.getName(), job.isFailures(),
                                ExecutionStatus.PENDING)
                                .map(jobRunId -> Map.entry(job.getName(), jobRunId))
                        )
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                        .map(jobIds -> {
                          Map<String, Long> stageEntry = new LinkedHashMap<>(jobIds);
                          stageEntry.put("__stageRunId__", stageRunId);
                          return Map.entry(stage.getStageName(), stageEntry);
                        })
                )
        )
        .collectMap(Map.Entry::getKey, Map.Entry::getValue)
        .map(m -> (Map<String, Map<String, Long>>) new LinkedHashMap<>(m))
        .onErrorResume(e -> {
          log.warn("DB: failed to pre-create stages/jobs — {}", e.getMessage());
          return Mono.just(new LinkedHashMap<>());
        });
  }

  /**
   * Transitions a {@code stage_runs} row from {@code PENDING} to {@code RUNNING}.
   *
   * @param stageRunId DB id of the row; {@code null} is a no-op
   * @return {@code Mono} completing when the update finishes; errors are swallowed
   */
  public Mono<Void> markStageRunning(Long stageRunId) {
    if (stageRunId == null) {
      return Mono.empty();
    }
    return stageRunRepo.findById(stageRunId)
        .flatMap(entity -> {
          entity.setStatus(ExecutionStatus.RUNNING.name());
          entity.setStartTime(OffsetDateTime.now());
          entity.setUpdatedAt(OffsetDateTime.now());
          return stageRunRepo.save(entity);
        })
        .then()
        .onErrorResume(e -> {
          log.warn("DB: failed to mark stage_run id={} as RUNNING — {}", stageRunId,
              e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Updates a {@code stage_runs} row with the final status and {@code end_time}.
   *
   * @param stageRunId DB id of the row; {@code null} is a no-op
   * @param status     final status — {@link ExecutionStatus#SUCCESS} or
   *                   {@link ExecutionStatus#FAILED}
   * @return {@code Mono} completing when the update finishes; errors are swallowed
   */
  public Mono<Void> updateStageRun(Long stageRunId, ExecutionStatus status) {
    if (stageRunId == null) {
      return Mono.empty();
    }
    return stageRunRepo.findById(stageRunId)
        .flatMap(entity -> {
          entity.setStatus(status.name());
          entity.setEndTime(OffsetDateTime.now());
          entity.setUpdatedAt(OffsetDateTime.now());
          return stageRunRepo.save(entity);
        })
        .then()
        .onErrorResume(e -> {
          log.warn("DB: failed to update stage_run id={} — {}", stageRunId, e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Transitions a {@code job_runs} row from {@code PENDING} to {@code RUNNING}.
   *
   * @param jobRunId DB id of the row; {@code null} is a no-op
   * @return {@code Mono} completing when the update finishes; errors are swallowed
   */
  public Mono<Void> markJobRunning(Long jobRunId) {
    if (jobRunId == null) {
      return Mono.empty();
    }
    return jobRunRepo.findById(jobRunId)
        .flatMap(entity -> {
          entity.setStatus(ExecutionStatus.RUNNING.name());
          entity.setStartTime(OffsetDateTime.now());
          entity.setUpdatedAt(OffsetDateTime.now());
          return jobRunRepo.save(entity);
        })
        .then()
        .onErrorResume(e -> {
          log.warn("DB: failed to mark job_run id={} as RUNNING — {}", jobRunId, e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Updates a {@code job_runs} row with the final status and {@code end_time}.
   *
   * @param jobRunId DB id of the row; {@code null} is a no-op
   * @param status   final status — {@link ExecutionStatus#SUCCESS} or
   *                 {@link ExecutionStatus#FAILED}
   * @return {@code Mono} completing when the update finishes; errors are swallowed
   */
  public Mono<Void> updateJobRun(Long jobRunId, ExecutionStatus status) {
    if (jobRunId == null) {
      return Mono.empty();
    }
    return jobRunRepo.findById(jobRunId)
        .flatMap(entity -> {
          entity.setStatus(status.name());
          entity.setEndTime(OffsetDateTime.now());
          entity.setUpdatedAt(OffsetDateTime.now());
          return jobRunRepo.save(entity);
        })
        .then()
        .onErrorResume(e -> {
          log.warn("DB: failed to update job_run id={} — {}", jobRunId, e.getMessage());
          return Mono.empty();
        });
  }

  /**
   * Inserts a {@code stage_runs} row with the given initial status; returns its id.
   *
   * @param pipelineRunId surrogate ID of the parent pipeline run
   * @param stageName     logical name of the stage (e.g. {@code "build"}, {@code "test"})
   * @param status        initial status — typically {@link ExecutionStatus#PENDING}
   * @return {@code Mono} emitting the surrogate ID of the newly inserted row
   */
  private Mono<Long> insertStageRun(Long pipelineRunId, String stageName, ExecutionStatus status) {
    OffsetDateTime now = OffsetDateTime.now();
    StageRunEntity entity = StageRunEntity.builder()
        .pipelineRunId(pipelineRunId)
        .stageName(stageName)
        .status(status.name())
        .startTime(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
    return stageRunRepo.save(entity).map(StageRunEntity::getId);
  }

  /**
   * Inserts a {@code job_runs} row with the given initial status; returns its id.
   *
   * @param stageRunId surrogate ID of the parent stage run
   * @param jobName    logical name of the job (e.g. {@code "compile"}, {@code "lint"})
   * @param failures whether this job is allowed to fail without failing the stage
   * @param status     initial status — typically {@link ExecutionStatus#PENDING}
   * @return {@code Mono} emitting the surrogate ID of the newly inserted row
   */
  private Mono<Long> insertJobRun(Long stageRunId, String jobName, boolean failures,
                                  ExecutionStatus status) {
    OffsetDateTime now = OffsetDateTime.now();
    JobRunEntity entity = JobRunEntity.builder()
        .stageRunId(stageRunId)
        .jobName(jobName)
        .failures(failures)
        .status(status.name())
        .startTime(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
    return jobRunRepo.save(entity).map(JobRunEntity::getId);
  }

  /**
   * Computes the SHA-256 hash of the given string as a 64-character hex string.
   *
   * @param input the string to hash (e.g. {@code "https://github.com/org/repo.git"})
   * @return lowercase hex-encoded SHA-256 digest
   * @throws IllegalStateException if SHA-256 is unavailable on this JVM
   */
  private static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(64);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
