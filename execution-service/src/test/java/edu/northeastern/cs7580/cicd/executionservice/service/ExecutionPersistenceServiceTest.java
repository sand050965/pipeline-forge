package edu.northeastern.cs7580.cicd.executionservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ExecutionPersistenceServiceTest {

  @Mock
  private PipelineRepository pipelineRepo;
  @Mock
  private PipelineRunRepository pipelineRunRepo;
  @Mock
  private StageRunRepository stageRunRepo;
  @Mock
  private JobRunRepository jobRunRepo;
  @Mock
  private ExecutionPlan plan;
  @Mock
  private StageExecution stage;
  @Mock
  private Job job;

  private ExecutionPersistenceService service;

  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final GitMetadata GIT = GitMetadata.builder()
      .repositoryUrl("https://github.com/org/repo.git")
      .branch("main")
      .commitHash("abc123")
      .build();

  @BeforeEach
  void setUp() {
    service = new ExecutionPersistenceService(
        pipelineRepo,
        pipelineRunRepo,
        stageRunRepo,
        jobRunRepo
    );
  }

  @Test
  void upsertPipeline_existingPipeline_returnsSavedId() {
    PipelineEntity existing = PipelineEntity.builder().id(1L).build();
    when(pipelineRepo.findByRepoIdAndPipelineName(any(), any())).thenReturn(Mono.just(existing));

    StepVerifier.create(service.upsertPipeline("my-pipeline", GIT))
        .expectNext(1L)
        .verifyComplete();

    verify(pipelineRepo, never()).save(any());
  }

  @Test
  void upsertPipeline_newPipeline_savesAndReturnsId() {
    PipelineEntity saved = PipelineEntity.builder().id(2L).build();
    when(pipelineRepo.findByRepoIdAndPipelineName(any(), any())).thenReturn(Mono.empty());
    when(pipelineRepo.save(any())).thenReturn(Mono.just(saved));

    StepVerifier.create(service.upsertPipeline("my-pipeline", GIT))
        .expectNext(2L)
        .verifyComplete();

    verify(pipelineRepo).save(any());
  }

  @Test
  void upsertPipeline_dbError_returnsEmpty() {
    when(pipelineRepo.findByRepoIdAndPipelineName(any(), any()))
        .thenReturn(Mono.error(new RuntimeException("DB down")));

    StepVerifier.create(service.upsertPipeline("my-pipeline", GIT))
        .verifyComplete();
  }

  @Test
  void createPipelineRun_nullPipelineId_returnsEmpty() {
    StepVerifier.create(service.createPipelineRun(null, GIT, ExecutionStatus.RUNNING))
        .verifyComplete();

    verify(pipelineRunRepo, never()).save(any());
  }

  @Test
  void createPipelineRun_validId_savesAndRefetches() {
    PipelineRunEntity saved = PipelineRunEntity.builder()
        .id(10L).pipelineId(1L).runNo(1).status(ExecutionStatus.RUNNING.name())
        .gitBranch("main").gitHash("abc123")
        .startTime(NOW).createdAt(NOW).updatedAt(NOW).build();

    when(pipelineRunRepo.save(any())).thenReturn(Mono.just(saved));
    when(pipelineRunRepo.findById(10L)).thenReturn(Mono.just(saved));

    StepVerifier.create(service.createPipelineRun(1L, GIT, ExecutionStatus.RUNNING))
        .assertNext(result -> {
          assertThat(result.getId()).isEqualTo(10L);
          assertThat(result.getRunNo()).isEqualTo(1);
          assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING.name());
        })
        .verifyComplete();
  }

  @Test
  void createPipelineRun_dbError_returnsEmpty() {
    when(pipelineRunRepo.save(any())).thenReturn(Mono.error(new RuntimeException("DB down")));

    StepVerifier.create(service.createPipelineRun(1L, GIT, ExecutionStatus.RUNNING))
        .verifyComplete();
  }

  @Test
  void updatePipelineRun_nullRunId_returnsEmpty() {
    StepVerifier.create(service.updatePipelineRun(null, ExecutionStatus.SUCCESS))
        .verifyComplete();

    verify(pipelineRunRepo, never()).findById(any(Long.class));
  }

  @Test
  void updatePipelineRun_validId_updatesStatusAndEndTime() {
    PipelineRunEntity entity = PipelineRunEntity.builder()
        .id(10L).status(ExecutionStatus.RUNNING.name())
        .startTime(NOW).createdAt(NOW).updatedAt(NOW).build();

    when(pipelineRunRepo.findById(10L)).thenReturn(Mono.just(entity));
    when(pipelineRunRepo.save(any())).thenReturn(Mono.just(entity));

    StepVerifier.create(service.updatePipelineRun(10L, ExecutionStatus.SUCCESS))
        .verifyComplete();

    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCESS.name());
    assertThat(entity.getEndTime()).isNotNull();
  }

  @Test
  void updatePipelineRun_dbError_completesWithoutThrowing() {
    when(pipelineRunRepo.findById(any(Long.class)))
        .thenReturn(Mono.error(new RuntimeException("DB down")));

    StepVerifier.create(service.updatePipelineRun(10L, ExecutionStatus.FAILED))
        .verifyComplete();
  }

  @Test
  void markStageRunning_nullId_returnsEmpty() {
    StepVerifier.create(service.markStageRunning(null))
        .verifyComplete();

    verify(stageRunRepo, never()).findById(any(Long.class));
  }

  @Test
  void markStageRunning_validId_setsStatusToRunning() {
    StageRunEntity entity = StageRunEntity.builder()
        .id(20L).status(ExecutionStatus.PENDING.name())
        .startTime(NOW).createdAt(NOW).updatedAt(NOW).build();

    when(stageRunRepo.findById(20L)).thenReturn(Mono.just(entity));
    when(stageRunRepo.save(any())).thenReturn(Mono.just(entity));

    StepVerifier.create(service.markStageRunning(20L))
        .verifyComplete();

    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.RUNNING.name());
  }

  @Test
  void updateStageRun_nullId_returnsEmpty() {
    StepVerifier.create(service.updateStageRun(null, ExecutionStatus.SUCCESS))
        .verifyComplete();

    verify(stageRunRepo, never()).findById(any(Long.class));
  }

  @Test
  void updateStageRun_validId_updatesStatusAndEndTime() {
    StageRunEntity entity = StageRunEntity.builder()
        .id(20L).status(ExecutionStatus.RUNNING.name())
        .startTime(NOW).createdAt(NOW).updatedAt(NOW).build();

    when(stageRunRepo.findById(20L)).thenReturn(Mono.just(entity));
    when(stageRunRepo.save(any())).thenReturn(Mono.just(entity));

    StepVerifier.create(service.updateStageRun(20L, ExecutionStatus.SUCCESS))
        .verifyComplete();

    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCESS.name());
    assertThat(entity.getEndTime()).isNotNull();
  }

  @Test
  void markJobRunning_nullId_returnsEmpty() {
    StepVerifier.create(service.markJobRunning(null))
        .verifyComplete();

    verify(jobRunRepo, never()).findById(any(Long.class));
  }

  @Test
  void markJobRunning_validId_setsStatusToRunning() {
    JobRunEntity entity = JobRunEntity.builder()
        .id(30L).status(ExecutionStatus.PENDING.name())
        .startTime(NOW).createdAt(NOW).updatedAt(NOW).build();

    when(jobRunRepo.findById(30L)).thenReturn(Mono.just(entity));
    when(jobRunRepo.save(any())).thenReturn(Mono.just(entity));

    StepVerifier.create(service.markJobRunning(30L))
        .verifyComplete();

    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.RUNNING.name());
  }

  @Test
  void updateJobRun_nullId_returnsEmpty() {
    StepVerifier.create(service.updateJobRun(null, ExecutionStatus.SUCCESS))
        .verifyComplete();

    verify(jobRunRepo, never()).findById(any(Long.class));
  }

  @Test
  void updateJobRun_validId_updatesStatusAndEndTime() {
    JobRunEntity entity = JobRunEntity.builder()
        .id(30L).status(ExecutionStatus.RUNNING.name())
        .startTime(NOW).createdAt(NOW).updatedAt(NOW).build();

    when(jobRunRepo.findById(30L)).thenReturn(Mono.just(entity));
    when(jobRunRepo.save(any())).thenReturn(Mono.just(entity));

    StepVerifier.create(service.updateJobRun(30L, ExecutionStatus.SUCCESS))
        .verifyComplete();

    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCESS.name());
    assertThat(entity.getEndTime()).isNotNull();
  }

  @Test
  void updateJobRun_dbError_completesWithoutThrowing() {
    when(jobRunRepo.findById(any(Long.class)))
        .thenReturn(Mono.error(new RuntimeException("DB down")));

    StepVerifier.create(service.updateJobRun(30L, ExecutionStatus.FAILED))
        .verifyComplete();
  }

  @Test
  void preCreateStagesAndJobs_nullPipelineRunId_returnsEmptyMap() {
    StepVerifier.create(service.preCreateStagesAndJobs(null, plan))
        .assertNext(map -> assertThat(map).isEmpty())
        .verifyComplete();

    verify(stageRunRepo, never()).save(any());
  }

  @Test
  void preCreateStagesAndJobs_oneStageTwoJobs_returnsCorrectIdMap() {
    Job job1 = org.mockito.Mockito.mock(Job.class);
    Job job2 = org.mockito.Mockito.mock(Job.class);
    when(job1.getName()).thenReturn("compile");
    when(job2.getName()).thenReturn("test");
    when(job1.isFailures()).thenReturn(true);
    when(job2.isFailures()).thenReturn(false);

    StageRunEntity savedStage = StageRunEntity.builder()
        .id(20L)
        .pipelineRunId(10L)
        .stageName("build")
        .status(ExecutionStatus.PENDING.name())
        .startTime(NOW)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
    JobRunEntity savedJob1 = JobRunEntity.builder()
        .id(30L)
        .stageRunId(20L)
        .jobName("compile")
        .status(ExecutionStatus.PENDING.name()).startTime(NOW)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
    JobRunEntity savedJob2 = JobRunEntity.builder()
        .id(31L)
        .stageRunId(20L)
        .jobName("test")
        .status(ExecutionStatus.PENDING.name())
        .startTime(NOW)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();

    when(plan.getStages()).thenReturn(List.of(stage));
    when(stage.getStageName()).thenReturn("build");
    when(stage.getJobs()).thenReturn(List.of(job1, job2));
    when(stageRunRepo.save(any())).thenReturn(Mono.just(savedStage));
    when(jobRunRepo.save(any()))
        .thenReturn(Mono.just(savedJob1))
        .thenReturn(Mono.just(savedJob2));

    StepVerifier.create(service.preCreateStagesAndJobs(10L, plan))
        .assertNext(map -> {
          assertThat(map).containsKey("build");
          Map<String, Long> stageEntry = map.get("build");
          assertThat(stageEntry.get("__stageRunId__")).isEqualTo(20L);
          assertThat(stageEntry.get("compile")).isEqualTo(30L);
          assertThat(stageEntry.get("test")).isEqualTo(31L);
        })
        .verifyComplete();

    ArgumentCaptor<JobRunEntity> jobCaptor = ArgumentCaptor.forClass(JobRunEntity.class);
    verify(jobRunRepo, times(2)).save(jobCaptor.capture());
    List<JobRunEntity> savedJobs = jobCaptor.getAllValues();
    assertThat(savedJobs).hasSize(2);
    assertThat(savedJobs.get(0).isFailures()).isTrue();
    assertThat(savedJobs.get(1).isFailures()).isFalse();
  }

  @Test
  void preCreateStagesAndJobs_dbError_returnsEmptyMap() {
    when(plan.getStages()).thenReturn(List.of(stage));
    when(stage.getStageName()).thenReturn("build");
    when(stageRunRepo.save(any())).thenReturn(Mono.error(new RuntimeException("DB down")));

    StepVerifier.create(service.preCreateStagesAndJobs(10L, plan))
        .assertNext(map -> assertThat(map).isEmpty())
        .verifyComplete();
  }
}
