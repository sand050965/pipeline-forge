package edu.northeastern.cs7580.cicd.executionservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.executionservice.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.executionservice.entity.PipelineRunEntity;
import edu.northeastern.cs7580.cicd.executionservice.executor.DockerExecutor;
import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import edu.northeastern.cs7580.cicd.executionservice.model.JobResult;
import edu.northeastern.cs7580.cicd.executionservice.model.JobStatus;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionServiceTest {

  @Mock private DockerExecutor dockerExecutor;
  @Mock private ExecutionPersistenceService persistenceService;
  @Mock private Tracer tracer;
  @Mock private Span span;
  @Mock private Tracer.SpanInScope spanInScope;
  @Mock private ExecutionPlan plan;
  @Mock private StageExecution stage;
  @Mock private Job job;

  private ExecutionService service;
  private MeterRegistry meterRegistry;

  private static final Path WORKSPACE = Path.of("/tmp/workspace");
  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final GitMetadata GIT = GitMetadata.builder()
      .repositoryUrl("https://github.com/org/repo.git")
      .branch("main")
      .commitHash("abc123")
      .build();

  private static final PipelineRunEntity PIPELINE_RUN = PipelineRunEntity.builder()
      .id(10L).pipelineId(1L).runNo(3)
      .status(ExecutionStatus.RUNNING.name())
      .gitBranch("main").gitHash("abc123")
      .startTime(NOW).createdAt(NOW).updatedAt(NOW)
      .build();

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    ParallelStageExecutor parallelStageExecutor =
        new ParallelStageExecutor(dockerExecutor, persistenceService, tracer, meterRegistry);
    service = new ExecutionService(
        persistenceService, parallelStageExecutor, tracer, meterRegistry);

    // Tracer stubs — span fluent API returns the same mock span
    when(tracer.nextSpan()).thenReturn(span);
    when(span.name(anyString())).thenReturn(span);
    when(span.tag(anyString(), anyString())).thenReturn(span);
    when(span.start()).thenReturn(span);
    when(tracer.withSpan(span)).thenReturn(spanInScope);
  }

  @Test
  void executeSequential_singleJobSuccess_returnsSuccessResult() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    JobResult jobResult = jobResult(JobStatus.COMPLETED);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE))).thenReturn(jobResult);

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isTrue();
          assertThat(result.getFailedJobName()).isNull();
          assertThat(result.getRunNumber()).isEqualTo(3);
          assertThat(result.getJobResults()).hasSize(1);
          assertThat(result.getJobResults().get(0).getStatus()).isEqualTo(JobStatus.COMPLETED);
        })
        .verifyComplete();

    verify(persistenceService).updateStageRun(eq(20L), eq(ExecutionStatus.SUCCESS));
  }

  @Test
  void executeSequential_jobFails_returnsFailedResult() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(false);
    JobResult failed = jobResult(JobStatus.FAILED);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE))).thenReturn(failed);

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isFalse();
          assertThat(result.getFailedJobName()).isEqualTo("compile");
          assertThat(result.getRunNumber()).isEqualTo(3);
        })
        .verifyComplete();

    verify(persistenceService).updateStageRun(eq(20L), eq(ExecutionStatus.FAILED));
  }

  @Test
  void executeSequential_firstJobFails_secondJobSkipped() {
    Job job1 = org.mockito.Mockito.mock(Job.class);
    Job job2 = org.mockito.Mockito.mock(Job.class);

    setupPersistence();
    setupStageWithJobs("build", List.of(job1, job2));
    when(job1.getName()).thenReturn("compile");
    when(job1.getNeeds()).thenReturn(null);
    when(job1.isFailures()).thenReturn(false);
    when(job2.getName()).thenReturn("test");
    when(job2.getNeeds()).thenReturn(List.of("compile"));
    when(dockerExecutor.executeJob(eq(job1), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.FAILED));

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isFalse();
          assertThat(result.getFailedJobName()).isEqualTo("compile");
          assertThat(result.getJobResults()).hasSize(2);
          assertThat(result.getJobResults().get(1).getStatus()).isEqualTo(JobStatus.SKIPPED);
        })
        .verifyComplete();

    verify(dockerExecutor, never()).executeJob(eq(job2), any());
    verify(persistenceService).updateStageRun(eq(20L), eq(ExecutionStatus.FAILED));
  }

  @Test
  void executeSequential_firstStageFails_secondStageSkipped() {
    StageExecution stage2 = org.mockito.Mockito.mock(StageExecution.class);
    Job job2 = org.mockito.Mockito.mock(Job.class);

    when(plan.getStages()).thenReturn(List.of(stage, stage2));
    when(stage.getStageName()).thenReturn("build");
    when(stage.getJobs()).thenReturn(List.of(job));
    when(stage2.getStageName()).thenReturn("deploy");
    when(stage2.getJobs()).thenReturn(List.of(job2));

    when(persistenceService.upsertPipeline(anyString(), any()))
        .thenReturn(Mono.just(1L));
    when(persistenceService.createPipelineRun(anyLong(), any(), any()))
        .thenReturn(Mono.just(PIPELINE_RUN));
    when(persistenceService.preCreateStagesAndJobs(anyLong(), any()))
        .thenReturn(Mono.just(
        Map.of(
            "build", Map.of("__stageRunId__", 20L, "compile", 30L),
            "deploy", Map.of("__stageRunId__", 21L, "release", 31L)
        )
    ));
    when(persistenceService.markStageRunning(anyLong())).thenReturn(Mono.empty());
    when(persistenceService.markJobRunning(anyLong())).thenReturn(Mono.empty());
    when(persistenceService.updateJobRun(anyLong(), any())).thenReturn(Mono.empty());
    when(persistenceService.updateStageRun(anyLong(), any())).thenReturn(Mono.empty());
    when(persistenceService.updatePipelineRun(anyLong(), any())).thenReturn(Mono.empty());

    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(false);
    when(job2.getName()).thenReturn("release");
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE))).thenReturn(jobResult(JobStatus.FAILED));

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isFalse();
          assertThat(result.getFailedJobName()).isEqualTo("compile");
          assertThat(result.getJobResults().stream()
              .filter(r -> r.getStatus() == JobStatus.SKIPPED)
              .count()).isEqualTo(1);
        })
        .verifyComplete();

    verify(dockerExecutor, never()).executeJob(eq(job2), any());
  }

  @Test
  void executeSequential_dbUnavailable_stillExecutesAndReturnsRunNumber0() {
    when(persistenceService.upsertPipeline(anyString(), any())).thenReturn(Mono.empty());
    when(persistenceService.markStageRunning(any())).thenReturn(Mono.empty());
    when(persistenceService.markJobRunning(any())).thenReturn(Mono.empty());
    when(persistenceService.updateJobRun(any(), any())).thenReturn(Mono.empty());
    when(persistenceService.updateStageRun(any(), any())).thenReturn(Mono.empty());

    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isTrue();
          assertThat(result.getRunNumber()).isEqualTo(0);
        })
        .verifyComplete();
  }

  // -------------------------------------------------------------------------
  // Docker throws — job marked as failed
  // -------------------------------------------------------------------------

  @Test
  void executeSequential_dockerThrows_jobMarkedFailed() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(false);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenThrow(new RuntimeException("Docker daemon unreachable"));

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isFalse();
          assertThat(result.getFailedJobName()).isEqualTo("compile");
          assertThat(result.getJobResults().get(0).getStatus()).isEqualTo(JobStatus.FAILED);
          assertThat(result.getJobResults().get(0).getOutput()).contains("Docker execution error");
        })
        .verifyComplete();
  }

  @Test
  void executeSequential_jobNeedsFailed_jobSkipped() {
    Job job1 = org.mockito.Mockito.mock(Job.class);
    Job job2 = org.mockito.Mockito.mock(Job.class);

    setupPersistence();
    setupStageWithJobs("build", List.of(job1, job2));
    when(job1.getName()).thenReturn("compile");
    when(job1.getNeeds()).thenReturn(null);
    when(job1.isFailures()).thenReturn(false);
    when(job2.getName()).thenReturn("test");
    when(job2.getNeeds()).thenReturn(List.of("compile"));
    when(dockerExecutor.executeJob(eq(job1), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.FAILED));

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.getJobResults().get(1).getStatus())
              .isEqualTo(JobStatus.SKIPPED);
        })
        .verifyComplete();

    verify(dockerExecutor, never()).executeJob(eq(job2), any());
  }

  @Test
  void executeSequential_success_updatesPipelineRunWithSuccess() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(false);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    verify(persistenceService).updatePipelineRun(eq(10L), eq(ExecutionStatus.SUCCESS));
  }

  @Test
  void executeSequential_failure_updatesPipelineRunWithFailed() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(false);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE))).thenReturn(jobResult(JobStatus.FAILED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    verify(persistenceService).updatePipelineRun(eq(10L), eq(ExecutionStatus.FAILED));
  }

  @Test
  void executeSequential_jobCompleted_updatesJobRunWithSuccess() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(false);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    verify(persistenceService).updateJobRun(eq(30L), eq(ExecutionStatus.SUCCESS));
  }

  @Test
  void executeSequential_emptyPlan_returnsSuccessWithNoJobs() {
    when(persistenceService.upsertPipeline(anyString(), any()))
        .thenReturn(Mono.just(1L));
    when(persistenceService.createPipelineRun(anyLong(), any(), any()))
        .thenReturn(Mono.just(PIPELINE_RUN));
    when(persistenceService.preCreateStagesAndJobs(anyLong(), any()))
        .thenReturn(Mono.just(Map.of()));
    when(persistenceService.updatePipelineRun(anyLong(), any())).thenReturn(Mono.empty());
    when(plan.getStages()).thenReturn(List.of());

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isTrue();
          assertThat(result.getJobResults()).isEmpty();
          assertThat(result.getRunNumber()).isEqualTo(3);
        })
        .verifyComplete();
  }

  @Test
  void executeSequential_optionalJobFails_pipelineSucceeds() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("optional");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(true);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.FAILED));

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isTrue();
          assertThat(result.getFailedJobName()).isNull();
          assertThat(result.getJobResults().get(0).getStatus()).isEqualTo(JobStatus.FAILED);
        })
        .verifyComplete();

    verify(persistenceService).updatePipelineRun(eq(10L), eq(ExecutionStatus.SUCCESS));
    verify(persistenceService).updateStageRun(eq(20L), eq(ExecutionStatus.SUCCESS));
  }

  @Test
  void executeSequential_optionalFailure_doesNotHaltRemainingJobs() {
    Job job1 = org.mockito.Mockito.mock(Job.class);
    Job job2 = org.mockito.Mockito.mock(Job.class);

    setupPersistence();
    setupStageWithJobs("build", List.of(job1, job2));
    when(job1.getName()).thenReturn("optional");
    when(job1.getNeeds()).thenReturn(null);
    when(job1.isFailures()).thenReturn(true);
    when(job2.getName()).thenReturn("later");
    when(job2.getNeeds()).thenReturn(null);
    when(job2.isFailures()).thenReturn(false);

    when(dockerExecutor.executeJob(eq(job1), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.FAILED));
    when(dockerExecutor.executeJob(eq(job2), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isTrue();
          assertThat(result.getJobResults()).hasSize(2);
          assertThat(result.getJobResults().get(1).getStatus())
              .isEqualTo(JobStatus.COMPLETED);
        })
        .verifyComplete();

    verify(dockerExecutor).executeJob(eq(job2), any());
    verify(persistenceService).updateStageRun(eq(20L), eq(ExecutionStatus.SUCCESS));
  }

  @Test
  void executeSequential_optionalDependencyFails_dependentRuns() {
    Job job1 = org.mockito.Mockito.mock(Job.class);
    Job job2 = org.mockito.Mockito.mock(Job.class);

    setupPersistence();
    setupStageWithJobs("build", List.of(job1, job2));
    when(job1.getName()).thenReturn("compile");
    when(job1.getNeeds()).thenReturn(null);
    when(job1.isFailures()).thenReturn(true);
    when(job2.getName()).thenReturn("test");
    when(job2.getNeeds()).thenReturn(List.of("compile"));
    when(job2.isFailures()).thenReturn(false);

    when(dockerExecutor.executeJob(eq(job1), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.FAILED));
    when(dockerExecutor.executeJob(eq(job2), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isTrue();
          assertThat(result.getFailedJobName()).isNull();
          assertThat(result.getJobResults()).hasSize(2);
          assertThat(result.getJobResults().get(1).getStatus())
              .isEqualTo(JobStatus.COMPLETED);
        })
        .verifyComplete();

    verify(dockerExecutor).executeJob(eq(job2), any());
    verify(persistenceService).updateStageRun(eq(20L), eq(ExecutionStatus.SUCCESS));
  }

  @Test
  void executeSequential_optionalThenCriticalFailure_haltsAfterCritical() {
    Job job1 = org.mockito.Mockito.mock(Job.class);
    Job job2 = org.mockito.Mockito.mock(Job.class);
    Job job3 = org.mockito.Mockito.mock(Job.class);

    setupPersistence();
    setupStageWithJobs("build", List.of(job1, job2, job3));
    when(job1.getName()).thenReturn("optional");
    when(job1.getNeeds()).thenReturn(null);
    when(job1.isFailures()).thenReturn(true);
    when(job2.getName()).thenReturn("critical");
    when(job2.getNeeds()).thenReturn(null);
    when(job2.isFailures()).thenReturn(false);
    when(job3.getName()).thenReturn("after");
    when(job3.getNeeds()).thenReturn(List.of("critical"));

    when(dockerExecutor.executeJob(eq(job1), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.FAILED));
    when(dockerExecutor.executeJob(eq(job2), eq(WORKSPACE)))
        .thenReturn(jobResultNamed("critical", JobStatus.FAILED));

    StepVerifier.create(service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE))
        .assertNext(result -> {
          assertThat(result.isSuccess()).isFalse();
          assertThat(result.getFailedJobName()).isEqualTo("critical");
          assertThat(result.getJobResults()).hasSize(3);
          assertThat(result.getJobResults().get(2).getStatus())
              .isEqualTo(JobStatus.SKIPPED);
        })
        .verifyComplete();

    verify(dockerExecutor, never()).executeJob(eq(job3), any());
    verify(persistenceService).updateStageRun(eq(20L), eq(ExecutionStatus.FAILED));
  }

  // -------------------------------------------------------------------------
  // RUNNING status tracking
  // -------------------------------------------------------------------------

  @Test
  void executeSequential_successfulJob_marksStageAndJobRunning() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    verify(persistenceService).markStageRunning(eq(20L));
    verify(persistenceService).markJobRunning(eq(30L));
  }

  @Test
  void executeSequential_skippedStage_doesNotMarkStageRunning() {
    StageExecution stage2 = mock(StageExecution.class);
    Job job2 = mock(Job.class);

    when(plan.getStages()).thenReturn(List.of(stage, stage2));
    when(stage.getStageName()).thenReturn("build");
    when(stage.getJobs()).thenReturn(List.of(job));
    when(stage2.getStageName()).thenReturn("deploy");
    when(stage2.getJobs()).thenReturn(List.of(job2));

    when(persistenceService.upsertPipeline(anyString(), any())).thenReturn(Mono.just(1L));
    when(persistenceService.createPipelineRun(anyLong(), any(), any()))
        .thenReturn(Mono.just(PIPELINE_RUN));
    when(persistenceService.preCreateStagesAndJobs(anyLong(), any()))
        .thenReturn(Mono.just(Map.of(
            "build", Map.of("__stageRunId__", 20L, "compile", 30L),
            "deploy", Map.of("__stageRunId__", 21L, "release", 31L)
        )));
    when(persistenceService.markStageRunning(anyLong())).thenReturn(Mono.empty());
    when(persistenceService.markJobRunning(anyLong())).thenReturn(Mono.empty());
    when(persistenceService.updateJobRun(anyLong(), any())).thenReturn(Mono.empty());
    when(persistenceService.updateStageRun(anyLong(), any())).thenReturn(Mono.empty());
    when(persistenceService.updatePipelineRun(anyLong(), any())).thenReturn(Mono.empty());

    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(false);
    when(job2.getName()).thenReturn("release");
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE))).thenReturn(jobResult(JobStatus.FAILED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    verify(persistenceService).markStageRunning(eq(20L));
    verify(persistenceService, never()).markStageRunning(eq(21L));
  }

  @Test
  void executeSequential_skippedJob_doesNotMarkJobRunning() {
    Job job1 = mock(Job.class);
    Job job2 = mock(Job.class);

    setupPersistence();
    setupStageWithJobs("build", List.of(job1, job2));
    when(job1.getName()).thenReturn("compile");
    when(job1.getNeeds()).thenReturn(null);
    when(job1.isFailures()).thenReturn(false);
    when(job2.getName()).thenReturn("test");
    when(job2.getNeeds()).thenReturn(List.of("compile"));
    when(dockerExecutor.executeJob(eq(job1), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.FAILED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    verify(persistenceService).markJobRunning(eq(30L));
    verify(persistenceService, never()).markJobRunning(eq(31L));
  }

  // -------------------------------------------------------------------------
  // Distributed tracing — stage and job spans
  // -------------------------------------------------------------------------

  @Test
  void executeSequential_createsStageSpanWithStageName() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    verify(span).name("stage.execute");
    verify(span).tag("stage.name", "build");
  }

  @Test
  void executeSequential_createsJobSpanWithJobNameAndStatus() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    verify(span).name("job.execute");
    verify(span).tag("job.name", "compile");
    verify(span).tag("job.status", JobStatus.COMPLETED.name());
  }

  @Test
  void executeSequential_failedJob_jobSpanTaggedWithFailedStatus() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(false);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.FAILED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    verify(span).tag("job.status", JobStatus.FAILED.name());
  }

  private void setupPersistence() {
    when(persistenceService.upsertPipeline(anyString(), any()))
        .thenReturn(Mono.just(1L));
    when(persistenceService.createPipelineRun(anyLong(), any(), any()))
        .thenReturn(Mono.just(PIPELINE_RUN));
    when(persistenceService.preCreateStagesAndJobs(anyLong(), any()))
        .thenReturn(Mono.just(
        Map.of("build", Map.of("__stageRunId__", 20L, "compile", 30L, "test", 31L))
    ));
    when(persistenceService.markStageRunning(anyLong())).thenReturn(Mono.empty());
    when(persistenceService.markJobRunning(any())).thenReturn(Mono.empty());
    when(persistenceService.updateJobRun(any(), any())).thenReturn(Mono.empty());
    when(persistenceService.updateStageRun(anyLong(), any())).thenReturn(Mono.empty());
    when(persistenceService.updatePipelineRun(anyLong(), any())).thenReturn(Mono.empty());
  }

  private void setupStageWithJobs(String stageName, List<Job> jobs) {
    when(plan.getStages()).thenReturn(List.of(stage));
    when(stage.getStageName()).thenReturn(stageName);
    when(stage.getJobs()).thenReturn(jobs);
  }

  private JobResult jobResult(JobStatus status) {
    return jobResultNamed("compile", status);
  }

  private JobResult jobResultNamed(String name, JobStatus status) {
    return JobResult.builder()
        .jobName(name)
        .status(status)
        .output(status == JobStatus.FAILED ? "error" : "ok")
        .exitCode(status == JobStatus.COMPLETED ? 0 : 1)
        .executionTime(Duration.ofSeconds(5))
        .build();
  }

  // -------------------------------------------------------------------------
  // Metrics — pipeline counter and timer
  // -------------------------------------------------------------------------

  @Test
  void executeFromMessage_pipelineSuccess_recordsSuccessCounter() {
    setupExecuteFromMessage(JobStatus.COMPLETED);

    service.executeFromMessage(plan, WORKSPACE, 10L, Map.of("build",
        Map.of("__stageRunId__", 20L, "compile", 30L)), "my-pipeline", 3);

    Counter counter = meterRegistry.find("cicd.pipeline.runs")
        .tag("pipeline", "my-pipeline")
        .tag("status", "success")
        .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void executeFromMessage_pipelineFailure_recordsFailureCounter() {
    setupExecuteFromMessage(JobStatus.FAILED);

    service.executeFromMessage(plan, WORKSPACE, 10L, Map.of("build",
        Map.of("__stageRunId__", 20L, "compile", 30L)), "my-pipeline", 3);

    Counter counter = meterRegistry.find("cicd.pipeline.runs")
        .tag("pipeline", "my-pipeline")
        .tag("status", "failure")
        .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void executeFromMessage_pipelineSuccess_recordsDurationTimer() {
    setupExecuteFromMessage(JobStatus.COMPLETED);

    service.executeFromMessage(plan, WORKSPACE, 10L, Map.of("build",
        Map.of("__stageRunId__", 20L, "compile", 30L)), "my-pipeline", 3);

    Timer timer = meterRegistry.find("cicd.pipeline.duration")
        .tag("pipeline", "my-pipeline")
        .timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
  }

  // -------------------------------------------------------------------------
  // Metrics — job counter and timer
  // -------------------------------------------------------------------------

  @Test
  void executeSequential_jobSuccess_recordsJobSuccessCounter() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    Counter counter = meterRegistry.find("cicd.job.runs")
        .tag("pipeline", "my-pipeline")
        .tag("stage", "build")
        .tag("job_name", "compile")
        .tag("status", "success")
        .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void executeSequential_jobFailure_recordsJobFailureCounter() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(false);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE))).thenReturn(jobResult(JobStatus.FAILED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    Counter counter = meterRegistry.find("cicd.job.runs")
        .tag("pipeline", "my-pipeline")
        .tag("stage", "build")
        .tag("job_name", "compile")
        .tag("status", "failure")
        .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void executeSequential_jobSkipped_recordsSkippedCounter() {
    Job job1 = org.mockito.Mockito.mock(Job.class);
    Job job2 = org.mockito.Mockito.mock(Job.class);

    setupPersistence();
    setupStageWithJobs("build", List.of(job1, job2));
    when(job1.getName()).thenReturn("compile");
    when(job1.getNeeds()).thenReturn(null);
    when(job1.isFailures()).thenReturn(false);
    when(job2.getName()).thenReturn("test");
    when(job2.getNeeds()).thenReturn(List.of("compile"));
    when(dockerExecutor.executeJob(eq(job1), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.FAILED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    Counter counter = meterRegistry.find("cicd.job.runs")
        .tag("pipeline", "my-pipeline")
        .tag("stage", "build")
        .tag("job_name", "test")
        .tag("status", "skipped")
        .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  void executeSequential_executedJob_recordsJobDurationTimer() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    Timer timer = meterRegistry.find("cicd.job.duration")
        .tag("pipeline", "my-pipeline")
        .tag("stage", "build")
        .tag("job_name", "compile")
        .timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
  }

  @Test
  void executeSequential_skippedJob_doesNotRecordJobDurationTimer() {
    Job job1 = org.mockito.Mockito.mock(Job.class);
    Job job2 = org.mockito.Mockito.mock(Job.class);

    setupPersistence();
    setupStageWithJobs("build", List.of(job1, job2));
    when(job1.getName()).thenReturn("compile");
    when(job1.getNeeds()).thenReturn(null);
    when(job1.isFailures()).thenReturn(false);
    when(job2.getName()).thenReturn("test");
    when(job2.getNeeds()).thenReturn(List.of("compile"));
    when(dockerExecutor.executeJob(eq(job1), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.FAILED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    Timer timer = meterRegistry.find("cicd.job.duration")
        .tag("job_name", "test")
        .timer();
    assertThat(timer).isNull();
  }

  // -------------------------------------------------------------------------
  // Metrics — stage timer
  // -------------------------------------------------------------------------

  @Test
  void executeSequential_executedStage_recordsStageDurationTimer() {
    setupPersistence();
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE)))
        .thenReturn(jobResult(JobStatus.COMPLETED));

    service.executeSequential(plan, GIT, "my-pipeline", WORKSPACE).block();

    Timer timer = meterRegistry.find("cicd.stage.duration")
        .tag("pipeline", "my-pipeline")
        .tag("stage", "build")
        .timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
  }

  private void setupExecuteFromMessage(JobStatus jobStatus) {
    when(persistenceService.markPipelineRunRunning(anyLong())).thenReturn(Mono.empty());
    when(persistenceService.markStageRunning(anyLong())).thenReturn(Mono.empty());
    when(persistenceService.markJobRunning(anyLong())).thenReturn(Mono.empty());
    when(persistenceService.updateJobRun(anyLong(), any())).thenReturn(Mono.empty());
    when(persistenceService.updateStageRun(anyLong(), any())).thenReturn(Mono.empty());
    when(persistenceService.updatePipelineRun(anyLong(), any())).thenReturn(Mono.empty());
    setupStageWithJobs("build", List.of(job));
    when(job.getName()).thenReturn("compile");
    when(job.getNeeds()).thenReturn(null);
    when(job.isFailures()).thenReturn(jobStatus != JobStatus.FAILED);
    when(dockerExecutor.executeJob(eq(job), eq(WORKSPACE))).thenReturn(jobResult(jobStatus));
  }
}
