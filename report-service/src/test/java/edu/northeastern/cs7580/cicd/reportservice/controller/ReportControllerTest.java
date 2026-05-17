package edu.northeastern.cs7580.cicd.reportservice.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.reportservice.dto.JobDetailResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.JobSummary;
import edu.northeastern.cs7580.cicd.reportservice.dto.PipelineReportResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.RunDetailResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.RunSummary;
import edu.northeastern.cs7580.cicd.reportservice.dto.StageDetailResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.StageSummary;
import edu.northeastern.cs7580.cicd.reportservice.exception.ResourceNotFoundException;
import edu.northeastern.cs7580.cicd.reportservice.service.ReportService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

  @Mock
  private ReportService reportService;

  @InjectMocks
  private ReportController reportController;

  private PipelineReportResponse pipelineReportResponse;
  private RunDetailResponse runDetailResponse;
  private StageDetailResponse stageDetailResponse;
  private JobDetailResponse jobDetailResponse;

  @BeforeEach
  void setUp() {
    pipelineReportResponse = PipelineReportResponse.builder()
        .pipeline(PipelineReportResponse.PipelineData.builder()
            .name("default")
            .runs(List.of(RunSummary.builder()
                .runNo(1)
                .status("success")
                .gitRepo("git@github.com:user/repo.git")
                .gitBranch("main")
                .gitHash("abc123")
                .start("2025-08-29T16:17:52-07:00")
                .end("2025-08-29T16:21:32-07:00")
                .build()))
            .build())
        .build();

    runDetailResponse = RunDetailResponse.builder()
        .pipeline(RunDetailResponse.PipelineRunData.builder()
            .name("default")
            .runNo(1)
            .status("success")
            .start("2025-08-29T16:17:52-07:00")
            .end("2025-08-29T16:24:32-07:00")
            .stages(List.of(StageSummary.builder()
                .name("build")
                .status("success")
                .start("2025-08-29T16:18:05-07:00")
                .end("2025-08-29T16:19:32-07:00")
                .build()))
            .build())
        .build();

    stageDetailResponse = StageDetailResponse.builder()
        .pipeline(StageDetailResponse.PipelineStageData.builder()
            .name("default")
            .runNo(1)
            .status("success")
            .start("2025-08-29T16:17:52-07:00")
            .end("2025-08-29T16:24:32-07:00")
            .stage(StageDetailResponse.StageWithJobs.builder()
                .name("build")
                .status("success")
                .start("2025-08-29T16:18:05-07:00")
                .end("2025-08-29T16:19:32-07:00")
                .jobs(List.of(JobSummary.builder()
                    .name("compile")
                    .status("success")
                    .failures(false)
                    .start("2025-08-29T16:18:05-07:00")
                    .end("2025-08-29T16:19:32-07:00")
                    .build()))
                .build())
            .build())
        .build();

    jobDetailResponse = JobDetailResponse.builder()
        .pipeline(JobDetailResponse.PipelineJobData.builder()
            .name("default")
            .runNo(1)
            .status("success")
            .start("2025-08-29T16:17:52-07:00")
            .end("2025-08-29T16:24:32-07:00")
            .stage(JobDetailResponse.StageWithJob.builder()
                .name("build")
                .status("success")
                .start("2025-08-29T16:18:05-07:00")
                .end("2025-08-29T16:19:32-07:00")
                .job(JobSummary.builder()
                    .name("compile")
                    .status("success")
                    .failures(false)
                    .start("2025-08-29T16:18:05-07:00")
                    .end("2025-08-29T16:19:32-07:00")
                    .build())
                .build())
            .build())
        .build();
  }

  @Test
  void getPipelineReport_success_returns200() {
    when(reportService.getPipelineReport("default"))
        .thenReturn(Mono.just(pipelineReportResponse));

    StepVerifier.create(reportController.getPipelineReport("default"))
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && "default".equals(
                    entity.getBody().getPipeline().getName())
                && entity.getBody().getPipeline().getRuns().size() == 1)
        .verifyComplete();
  }

  @Test
  void getPipelineReport_notFound_propagatesError() {
    when(reportService.getPipelineReport("nonexistent"))
        .thenReturn(Mono.error(
            new ResourceNotFoundException("Pipeline 'nonexistent' not found")));

    StepVerifier.create(reportController.getPipelineReport("nonexistent"))
        .expectError(ResourceNotFoundException.class)
        .verify();
  }

  @Test
  void getRunReport_success_returns200() {
    when(reportService.getRunReport("default", 1))
        .thenReturn(Mono.just(runDetailResponse));

    StepVerifier.create(reportController.getRunReport("default", 1))
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && "default".equals(entity.getBody().getPipeline().getName())
                && entity.getBody().getPipeline().getRunNo() == 1
                && entity.getBody().getPipeline().getStages().size() == 1)
        .verifyComplete();
  }

  @Test
  void getRunReport_notFound_propagatesError() {
    when(reportService.getRunReport(anyString(), anyInt()))
        .thenReturn(Mono.error(
            new ResourceNotFoundException("Run 99 not found")));

    StepVerifier.create(reportController.getRunReport("default", 99))
        .expectError(ResourceNotFoundException.class)
        .verify();
  }

  @Test
  void getStageReport_success_returns200() {
    when(reportService.getStageReport("default", 1, "build"))
        .thenReturn(Mono.just(stageDetailResponse));

    StepVerifier.create(
        reportController.getStageReport("default", 1, "build"))
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && "build".equals(
                    entity.getBody().getPipeline().getStage().getName())
                && entity.getBody().getPipeline().getStage()
                    .getJobs().size() == 1)
        .verifyComplete();
  }

  @Test
  void getStageReport_notFound_propagatesError() {
    when(reportService.getStageReport(anyString(), anyInt(), anyString()))
        .thenReturn(Mono.error(
            new ResourceNotFoundException("Stage 'xyz' not found")));

    StepVerifier.create(
        reportController.getStageReport("default", 1, "xyz"))
        .expectError(ResourceNotFoundException.class)
        .verify();
  }

  @Test
  void getJobReport_success_returns200() {
    when(reportService.getJobReport("default", 1, "build", "compile"))
        .thenReturn(Mono.just(jobDetailResponse));

    StepVerifier.create(
        reportController.getJobReport("default", 1, "build", "compile"))
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && "compile".equals(
                    entity.getBody().getPipeline().getStage()
                        .getJob().getName()))
        .verifyComplete();
  }

  @Test
  void getJobReport_notFound_propagatesError() {
    when(reportService.getJobReport(
        anyString(), anyInt(), anyString(), anyString()))
        .thenReturn(Mono.error(
            new ResourceNotFoundException("Job 'xyz' not found")));

    StepVerifier.create(
        reportController.getJobReport("default", 1, "build", "xyz"))
        .expectError(ResourceNotFoundException.class)
        .verify();
  }

  @Test
  void getStageReport_jobResponseIncludesAllowFailuresField() {
    StageDetailResponse responseWithAllowFailures = StageDetailResponse.builder()
        .pipeline(StageDetailResponse.PipelineStageData.builder()
            .name("default")
            .runNo(1)
            .status("success")
            .start("2025-08-29T16:17:52-07:00")
            .end("2025-08-29T16:24:32-07:00")
            .stage(StageDetailResponse.StageWithJobs.builder()
                .name("build")
                .status("success")
                .start("2025-08-29T16:18:05-07:00")
                .end("2025-08-29T16:19:32-07:00")
                .jobs(List.of(JobSummary.builder()
                    .name("flaky-check")
                    .status("failed")
                    .failures(true)
                    .start("2025-08-29T16:18:05-07:00")
                    .end("2025-08-29T16:19:32-07:00")
                    .build()))
                .build())
            .build())
        .build();

    when(reportService.getStageReport("default", 1, "build"))
        .thenReturn(Mono.just(responseWithAllowFailures));

    StepVerifier.create(
        reportController.getStageReport("default", 1, "build"))
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && entity.getBody().getPipeline().getStage()
                    .getJobs().get(0).isFailures())
        .verifyComplete();
  }

  @Test
  void health_returns200WithMessage() {
    StepVerifier.create(reportController.health())
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && "Report Service is healthy".equals(entity.getBody()))
        .verifyComplete();
  }
}
