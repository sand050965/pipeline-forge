package edu.northeastern.cs7580.cicd.reportservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.reportservice.dto.JobSummary;
import edu.northeastern.cs7580.cicd.reportservice.entity.JobRun;
import edu.northeastern.cs7580.cicd.reportservice.entity.Pipeline;
import edu.northeastern.cs7580.cicd.reportservice.entity.PipelineRun;
import edu.northeastern.cs7580.cicd.reportservice.entity.StageRun;
import edu.northeastern.cs7580.cicd.reportservice.exception.ResourceNotFoundException;
import edu.northeastern.cs7580.cicd.reportservice.repository.JobRunRepository;
import edu.northeastern.cs7580.cicd.reportservice.repository.PipelineRepository;
import edu.northeastern.cs7580.cicd.reportservice.repository.PipelineRunRepository;
import edu.northeastern.cs7580.cicd.reportservice.repository.StageRunRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

  @Mock
  private PipelineRepository pipelineRepository;

  @Mock
  private PipelineRunRepository pipelineRunRepository;

  @Mock
  private StageRunRepository stageRunRepository;

  @Mock
  private JobRunRepository jobRunRepository;

  @InjectMocks
  private ReportService reportService;

  private Pipeline pipeline;
  private PipelineRun pipelineRun;
  private StageRun stageRun;
  private JobRun jobRun;

  @BeforeEach
  void setUp() {
    OffsetDateTime start = OffsetDateTime.of(
        2025, 8, 29, 16, 17, 52, 0, ZoneOffset.ofHours(-7));
    OffsetDateTime end = OffsetDateTime.of(
        2025, 8, 29, 16, 24, 32, 0, ZoneOffset.ofHours(-7));

    pipeline = Pipeline.builder()
        .id(1L)
        .repoId("a1b2c3d4e5f6")
        .pipelineName("default")
        .gitRepo("git@github.com:user/repo.git")
        .build();

    pipelineRun = PipelineRun.builder()
        .id(10L)
        .pipelineId(1L)
        .runNo(1)
        .status("success")
        .gitBranch("main")
        .gitHash("c3aefdaaabbccddee11223344556677889900aab")
        .startTime(start)
        .endTime(end)
        .traceId("4bf92f3577b34da6a3ce929d0e0e4736")
        .build();

    stageRun = StageRun.builder()
        .id(100L)
        .pipelineRunId(10L)
        .stageName("build")
        .status("success")
        .startTime(start.plusMinutes(1))
        .endTime(end.minusMinutes(3))
        .build();

    jobRun = JobRun.builder()
        .id(1000L)
        .stageRunId(100L)
        .jobName("compile")
        .status("success")
        .startTime(start.plusMinutes(1))
        .endTime(end.minusMinutes(3))
        .build();
  }

  @Test
  void getPipelineReport_success_returnsRuns() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineId(1L))
        .thenReturn(Flux.just(pipelineRun));

    StepVerifier.create(reportService.getPipelineReport("default"))
        .assertNext(response -> {
          assertNotNull(response.getPipeline());
          assertEquals("default", response.getPipeline().getName());
          assertEquals(1, response.getPipeline().getRuns().size());
          assertEquals(1, response.getPipeline().getRuns().get(0).getRunNo());
          assertEquals("success",
              response.getPipeline().getRuns().get(0).getStatus());
        })
        .verifyComplete();
  }

  @Test
  void getPipelineReport_notFound_throwsException() {
    when(pipelineRepository.findByPipelineName("nonexistent"))
        .thenReturn(Flux.empty());

    StepVerifier.create(reportService.getPipelineReport("nonexistent"))
        .expectErrorMatches(ex ->
            ex instanceof ResourceNotFoundException
                && ex.getMessage().contains("nonexistent"))
        .verify();
  }

  @Test
  void getRunReport_success_returnsRunWithStages() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository.findByPipelineRunId(10L))
        .thenReturn(Flux.just(stageRun));

    StepVerifier.create(reportService.getRunReport("default", 1))
        .assertNext(response -> {
          assertEquals("default", response.getPipeline().getName());
          assertEquals(1, response.getPipeline().getRunNo());
          assertEquals("success", response.getPipeline().getStatus());
          assertEquals(1, response.getPipeline().getStages().size());
          assertEquals("build",
              response.getPipeline().getStages().get(0).getName());
        })
        .verifyComplete();
  }

  @Test
  void getRunReport_runNotFound_throwsException() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 99))
        .thenReturn(Mono.empty());

    StepVerifier.create(reportService.getRunReport("default", 99))
        .expectErrorMatches(ex ->
            ex instanceof ResourceNotFoundException
                && ex.getMessage().contains("Run 99"))
        .verify();
  }

  @Test
  void getStageReport_success_returnsStageWithJobs() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository
        .findByPipelineRunIdAndStageName(10L, "build"))
        .thenReturn(Mono.just(stageRun));
    when(jobRunRepository.findByStageRunId(100L))
        .thenReturn(Flux.just(jobRun));

    StepVerifier.create(
        reportService.getStageReport("default", 1, "build"))
        .assertNext(response -> {
          assertEquals("build",
              response.getPipeline().getStage().getName());
          assertEquals(1,
              response.getPipeline().getStage().getJobs().size());
          assertEquals("compile",
              response.getPipeline().getStage().getJobs().get(0).getName());
        })
        .verifyComplete();
  }

  @Test
  void getStageReport_stageNotFound_throwsException() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository
        .findByPipelineRunIdAndStageName(10L, "nonexistent"))
        .thenReturn(Mono.empty());

    StepVerifier.create(
        reportService.getStageReport("default", 1, "nonexistent"))
        .expectErrorMatches(ex ->
            ex instanceof ResourceNotFoundException
                && ex.getMessage().contains("Stage 'nonexistent'"))
        .verify();
  }

  @Test
  void getJobReport_success_returnsJobDetail() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository
        .findByPipelineRunIdAndStageName(10L, "build"))
        .thenReturn(Mono.just(stageRun));
    when(jobRunRepository
        .findByStageRunIdAndJobName(100L, "compile"))
        .thenReturn(Mono.just(jobRun));

    StepVerifier.create(
        reportService.getJobReport("default", 1, "build", "compile"))
        .assertNext(response -> {
          assertEquals("compile",
              response.getPipeline().getStage().getJob().getName());
          assertEquals("success",
              response.getPipeline().getStage().getJob().getStatus());
        })
        .verifyComplete();
  }

  @Test
  void getJobReport_jobNotFound_throwsException() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository
        .findByPipelineRunIdAndStageName(10L, "build"))
        .thenReturn(Mono.just(stageRun));
    when(jobRunRepository
        .findByStageRunIdAndJobName(100L, "nonexistent"))
        .thenReturn(Mono.empty());

    StepVerifier.create(
        reportService.getJobReport("default", 1, "build", "nonexistent"))
        .expectErrorMatches(ex ->
            ex instanceof ResourceNotFoundException
                && ex.getMessage().contains("Job 'nonexistent'"))
        .verify();
  }

  @Test
  void getPipelineReport_multipleRuns_returnsAll() {
    PipelineRun run2 = PipelineRun.builder()
        .id(11L)
        .pipelineId(1L)
        .runNo(2)
        .status("failed")
        .gitBranch("main")
        .gitHash("def456aabbccddee11223344556677889900aabb")
        .startTime(pipelineRun.getStartTime().plusHours(1))
        .endTime(pipelineRun.getEndTime().plusHours(1))
        .build();

    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineId(1L))
        .thenReturn(Flux.just(pipelineRun, run2));

    StepVerifier.create(reportService.getPipelineReport("default"))
        .assertNext(response -> {
          assertEquals(2, response.getPipeline().getRuns().size());
          assertEquals(1, response.getPipeline().getRuns().get(0).getRunNo());
          assertEquals(2, response.getPipeline().getRuns().get(1).getRunNo());
          assertEquals("failed",
              response.getPipeline().getRuns().get(1).getStatus());
        })
        .verifyComplete();
  }

  @Test
  void getRunReport_noStages_returnsEmptyStagesList() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository.findByPipelineRunId(10L))
        .thenReturn(Flux.empty());

    StepVerifier.create(reportService.getRunReport("default", 1))
        .assertNext(response -> {
          assertEquals(0, response.getPipeline().getStages().size());
        })
        .verifyComplete();
  }

  @Test
  void getRunReport_nullTimestamps_returnsNullStartEnd() {
    PipelineRun runNoTimes = PipelineRun.builder()
        .id(10L)
        .pipelineId(1L)
        .runNo(1)
        .status("running")
        .gitBranch("main")
        .gitHash("c3aefdaaabbccddee11223344556677889900aab")
        .startTime(null)
        .endTime(null)
        .build();

    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(runNoTimes));
    when(stageRunRepository.findByPipelineRunId(10L))
        .thenReturn(Flux.empty());

    StepVerifier.create(reportService.getRunReport("default", 1))
        .assertNext(response -> {
          assertNull(response.getPipeline().getStart());
          assertNull(response.getPipeline().getEnd());
        })
        .verifyComplete();
  }

  @Test
  void getStageReport_jobWithAllowFailuresTrue_isReflectedInResponse() {
    JobRun flakyJob = JobRun.builder()
        .id(1001L)
        .stageRunId(100L)
        .jobName("flaky-check")
        .status("failed")
        .failures(true)
        .startTime(stageRun.getStartTime())
        .endTime(stageRun.getEndTime())
        .build();

    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository.findByPipelineRunIdAndStageName(10L, "build"))
        .thenReturn(Mono.just(stageRun));
    when(jobRunRepository.findByStageRunId(100L))
        .thenReturn(Flux.just(flakyJob));

    StepVerifier.create(
        reportService.getStageReport("default", 1, "build"))
        .assertNext(response -> {
          JobSummary job = response.getPipeline().getStage().getJobs().get(0);
          assertEquals("flaky-check", job.getName());
          assertEquals(true, job.isFailures());
        })
        .verifyComplete();
  }

  @Test
  void getStageReport_jobWithAllowFailuresFalse_isReflectedInResponse() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository.findByPipelineRunIdAndStageName(10L, "build"))
        .thenReturn(Mono.just(stageRun));
    when(jobRunRepository.findByStageRunId(100L))
        .thenReturn(Flux.just(jobRun)); // jobRun has failures = false by default

    StepVerifier.create(
        reportService.getStageReport("default", 1, "build"))
        .assertNext(response -> {
          JobSummary job = response.getPipeline().getStage().getJobs().get(0);
          assertEquals(false, job.isFailures());
        })
        .verifyComplete();
  }

  @Test
  void getJobReport_allowFailuresTrue_isReflectedInResponse() {
    JobRun flakyJob = JobRun.builder()
        .id(1001L)
        .stageRunId(100L)
        .jobName("flaky-check")
        .status("failed")
        .failures(true)
        .startTime(stageRun.getStartTime())
        .endTime(stageRun.getEndTime())
        .build();

    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository.findByPipelineRunIdAndStageName(10L, "build"))
        .thenReturn(Mono.just(stageRun));
    when(jobRunRepository.findByStageRunIdAndJobName(100L, "flaky-check"))
        .thenReturn(Mono.just(flakyJob));

    StepVerifier.create(
        reportService.getJobReport("default", 1, "build", "flaky-check"))
        .assertNext(response -> {
          assertEquals(true,
              response.getPipeline().getStage().getJob().isFailures());
        })
        .verifyComplete();
  }

  @Test
  void getStageReport_noJobs_returnsEmptyJobsList() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository
        .findByPipelineRunIdAndStageName(10L, "build"))
        .thenReturn(Mono.just(stageRun));
    when(jobRunRepository.findByStageRunId(100L))
        .thenReturn(Flux.empty());

    StepVerifier.create(
        reportService.getStageReport("default", 1, "build"))
        .assertNext(response -> {
          assertEquals(0,
              response.getPipeline().getStage().getJobs().size());
        })
        .verifyComplete();
  }

  // ── Status method tests ────────────────────────────────────────────────────

  @Test
  void getStatusByRepo_noPipelineFound_throwsException() {
    when(pipelineRepository.findByGitRepo("git@github.com:user/repo.git"))
        .thenReturn(Flux.empty());

    StepVerifier.create(
        reportService.getStatusByRepo("git@github.com:user/repo.git"))
        .expectErrorMatches(ex ->
            ex instanceof ResourceNotFoundException
                && ex.getMessage().contains("No pipeline found for repo"))
        .verify();
  }

  @Test
  void getStatusByRepo_noRunsFound_throwsException() {
    when(pipelineRepository.findByGitRepo("git@github.com:user/repo.git"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findFirstByPipelineIdOrderByRunNoDesc(1L))
        .thenReturn(Mono.empty());

    StepVerifier.create(
        reportService.getStatusByRepo("git@github.com:user/repo.git"))
        .expectErrorMatches(ex ->
            ex instanceof ResourceNotFoundException
                && ex.getMessage().contains("No runs found for repo"))
        .verify();
  }

  @Test
  void getStatusByRepo_runningRunExists_returnsRunningRun() {
    PipelineRun runningRun = PipelineRun.builder()
        .id(10L)
        .pipelineId(1L)
        .runNo(2)
        .status("RUNNING")
        .gitBranch("main")
        .gitHash("abc123")
        .startTime(pipelineRun.getStartTime().plusHours(1))
        .endTime(null)
        .build();

    when(pipelineRepository.findByGitRepo("git@github.com:user/repo.git"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findFirstByPipelineIdOrderByRunNoDesc(1L))
        .thenReturn(Mono.just(runningRun));
    when(stageRunRepository.findByPipelineRunId(10L))
        .thenReturn(Flux.just(stageRun));
    when(jobRunRepository.findByStageRunId(100L))
        .thenReturn(Flux.just(jobRun));

    StepVerifier.create(
        reportService.getStatusByRepo("git@github.com:user/repo.git"))
        .assertNext(response -> {
          assertEquals("default", response.getPipelineName());
          assertEquals(2, response.getRunNo());
          assertEquals("RUNNING", response.getStatus());
          assertEquals(1, response.getStages().size());
          assertEquals("build", response.getStages().get(0).getName());
          assertEquals(1, response.getStages().get(0).getJobs().size());
          assertEquals("compile",
              response.getStages().get(0).getJobs().get(0).getName());
        })
        .verifyComplete();
  }

  @Test
  void getStatusByRepo_noRunningRun_returnsMostRecentByStartTime() {
    Pipeline pipeline2 = Pipeline.builder()
        .id(2L)
        .repoId("a1b2c3d4e5f6")
        .pipelineName("pipeline2")
        .gitRepo("git@github.com:user/repo.git")
        .build();

    PipelineRun laterRun = PipelineRun.builder()
        .id(20L)
        .pipelineId(2L)
        .runNo(1)
        .status("SUCCESS")
        .gitBranch("main")
        .gitHash("def456")
        .startTime(pipelineRun.getStartTime().plusHours(2))
        .endTime(pipelineRun.getEndTime().plusHours(2))
        .build();

    StageRun stageRun2 = StageRun.builder()
        .id(200L)
        .pipelineRunId(20L)
        .stageName("test")
        .status("SUCCESS")
        .startTime(laterRun.getStartTime())
        .endTime(laterRun.getEndTime())
        .build();

    when(pipelineRepository.findByGitRepo("git@github.com:user/repo.git"))
        .thenReturn(Flux.just(pipeline, pipeline2));
    when(pipelineRunRepository.findFirstByPipelineIdOrderByRunNoDesc(1L))
        .thenReturn(Mono.just(pipelineRun));
    when(pipelineRunRepository.findFirstByPipelineIdOrderByRunNoDesc(2L))
        .thenReturn(Mono.just(laterRun));
    when(stageRunRepository.findByPipelineRunId(20L))
        .thenReturn(Flux.just(stageRun2));
    when(jobRunRepository.findByStageRunId(200L))
        .thenReturn(Flux.empty());

    StepVerifier.create(
        reportService.getStatusByRepo("git@github.com:user/repo.git"))
        .assertNext(response -> {
          assertEquals("pipeline2", response.getPipelineName());
          assertEquals(1, response.getRunNo());
        })
        .verifyComplete();
  }

  @Test
  void getStatusByRepo_nullStartTime_handledByComparator() {
    Pipeline pipeline2 = Pipeline.builder()
        .id(2L)
        .repoId("a1b2c3d4e5f6")
        .pipelineName("pipeline2")
        .gitRepo("git@github.com:user/repo.git")
        .build();

    PipelineRun nullStartRun = PipelineRun.builder()
        .id(11L)
        .pipelineId(2L)
        .runNo(1)
        .status("SUCCESS")
        .gitBranch("main")
        .gitHash("def456")
        .startTime(null)
        .endTime(null)
        .build();

    when(pipelineRepository.findByGitRepo("git@github.com:user/repo.git"))
        .thenReturn(Flux.just(pipeline, pipeline2));
    when(pipelineRunRepository.findFirstByPipelineIdOrderByRunNoDesc(1L))
        .thenReturn(Mono.just(pipelineRun));
    when(pipelineRunRepository.findFirstByPipelineIdOrderByRunNoDesc(2L))
        .thenReturn(Mono.just(nullStartRun));
    when(stageRunRepository.findByPipelineRunId(10L))
        .thenReturn(Flux.just(stageRun));
    when(jobRunRepository.findByStageRunId(100L))
        .thenReturn(Flux.just(jobRun));

    StepVerifier.create(
        reportService.getStatusByRepo("git@github.com:user/repo.git"))
        .assertNext(response -> {
          // pipeline1 has non-null startTime → selected as max
          assertEquals("default", response.getPipelineName());
        })
        .verifyComplete();
  }

  @Test
  void getStatusByRun_success_returnsStatusWithStagesAndJobs() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository.findByPipelineRunId(10L))
        .thenReturn(Flux.just(stageRun));
    when(jobRunRepository.findByStageRunId(100L))
        .thenReturn(Flux.just(jobRun));

    StepVerifier.create(reportService.getStatusByRun("default", 1))
        .assertNext(response -> {
          assertEquals("default", response.getPipelineName());
          assertEquals(1, response.getRunNo());
          assertEquals("success", response.getStatus());
          assertEquals(1, response.getStages().size());
          assertEquals("build", response.getStages().get(0).getName());
          assertEquals(1, response.getStages().get(0).getJobs().size());
          assertEquals("compile",
              response.getStages().get(0).getJobs().get(0).getName());
        })
        .verifyComplete();
  }

  @Test
  void getStatusByRun_pipelineNotFound_throwsException() {
    when(pipelineRepository.findByPipelineName("nonexistent"))
        .thenReturn(Flux.empty());

    StepVerifier.create(reportService.getStatusByRun("nonexistent", 1))
        .expectErrorMatches(ex ->
            ex instanceof ResourceNotFoundException
                && ex.getMessage().contains("nonexistent"))
        .verify();
  }

  @Test
  void getStatusByRun_runNotFound_throwsException() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 99))
        .thenReturn(Mono.empty());

    StepVerifier.create(reportService.getStatusByRun("default", 99))
        .expectErrorMatches(ex ->
            ex instanceof ResourceNotFoundException
                && ex.getMessage().contains("Run 99"))
        .verify();
  }

  @Test
  void getRunReport_traceId_isMappedToResponse() {
    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(pipelineRun));
    when(stageRunRepository.findByPipelineRunId(10L))
        .thenReturn(Flux.empty());

    StepVerifier.create(reportService.getRunReport("default", 1))
        .assertNext(response -> {
          assertEquals("4bf92f3577b34da6a3ce929d0e0e4736",
              response.getPipeline().getTraceId());
        })
        .verifyComplete();
  }

  @Test
  void getRunReport_nullTraceId_isNullInResponse() {
    PipelineRun runNoTrace = PipelineRun.builder()
        .id(10L)
        .pipelineId(1L)
        .runNo(1)
        .status("success")
        .gitBranch("main")
        .gitHash("c3aefdaaabbccddee11223344556677889900aab")
        .startTime(pipelineRun.getStartTime())
        .endTime(pipelineRun.getEndTime())
        .traceId(null)
        .build();

    when(pipelineRepository.findByPipelineName("default"))
        .thenReturn(Flux.just(pipeline));
    when(pipelineRunRepository.findByPipelineIdAndRunNo(1L, 1))
        .thenReturn(Mono.just(runNoTrace));
    when(stageRunRepository.findByPipelineRunId(10L))
        .thenReturn(Flux.empty());

    StepVerifier.create(reportService.getRunReport("default", 1))
        .assertNext(response -> {
          assertNull(response.getPipeline().getTraceId());
        })
        .verifyComplete();
  }
}
