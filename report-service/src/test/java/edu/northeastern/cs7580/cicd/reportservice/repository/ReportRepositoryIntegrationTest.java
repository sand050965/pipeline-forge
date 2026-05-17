package edu.northeastern.cs7580.cicd.reportservice.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import edu.northeastern.cs7580.cicd.reportservice.entity.JobRun;
import edu.northeastern.cs7580.cicd.reportservice.entity.Pipeline;
import edu.northeastern.cs7580.cicd.reportservice.entity.PipelineRun;
import edu.northeastern.cs7580.cicd.reportservice.entity.StageRun;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

@SpringBootTest
@Testcontainers
@Tag("integration")
class ReportRepositoryIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17")
          .withDatabaseName("cicd_test_db")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.r2dbc.url", () ->
        "r2dbc:postgresql://" + postgres.getHost() + ":"
            + postgres.getFirstMappedPort() + "/cicd_test_db");
    registry.add("spring.r2dbc.username", () -> "test");
    registry.add("spring.r2dbc.password", () -> "test");
  }

  @Autowired
  private PipelineRepository pipelineRepository;

  @Autowired
  private PipelineRunRepository pipelineRunRepository;

  @Autowired
  private StageRunRepository stageRunRepository;

  @Autowired
  private JobRunRepository jobRunRepository;

  @Autowired
  private DatabaseClient databaseClient;

  private final OffsetDateTime start = OffsetDateTime.of(
      2025, 8, 29, 16, 17, 52, 0, ZoneOffset.ofHours(-7));
  private final OffsetDateTime end = OffsetDateTime.of(
      2025, 8, 29, 16, 24, 32, 0, ZoneOffset.ofHours(-7));

  @BeforeEach
  void setUp() {
    databaseClient.sql("DELETE FROM job_runs").then().block();
    databaseClient.sql("DELETE FROM stage_runs").then().block();
    databaseClient.sql("DELETE FROM pipeline_runs").then().block();
    databaseClient.sql("DELETE FROM pipelines").then().block();
  }

  @Test
  void findByPipelineName_returnsMatchingPipelines() {
    Pipeline pipeline = pipelineRepository.save(Pipeline.builder()
        .repoId("abc123hash")
        .pipelineName("default")
        .gitRepo("git@github.com:user/repo.git")
        .build()).block();

    assertNotNull(pipeline);

    StepVerifier.create(pipelineRepository.findByPipelineName("default"))
        .assertNext(found -> {
          assertNotNull(found.getId());
          assertEquals("default", found.getPipelineName());
          assertEquals("git@github.com:user/repo.git", found.getGitRepo());
        })
        .verifyComplete();
  }

  @Test
  void findByPipelineName_emptyForNonexistent() {
    StepVerifier.create(
        pipelineRepository.findByPipelineName("nonexistent"))
        .verifyComplete();
  }

  @Test
  void findByPipelineIdAndRunNo_returnsExactMatch() {
    Pipeline pipeline = pipelineRepository.save(Pipeline.builder()
        .repoId("abc123hash")
        .pipelineName("default")
        .gitRepo("git@github.com:user/repo.git")
        .build()).block();

    assertNotNull(pipeline);

    PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
        .pipelineId(pipeline.getId())
        .runNo(1)
        .status("success")
        .gitBranch("main")
        .gitHash("c3aefdaaabbccddee11223344556677889900aab")
        .startTime(start)
        .endTime(end)
        .build()).block();

    assertNotNull(run);

    StepVerifier.create(
        pipelineRunRepository.findByPipelineIdAndRunNo(
            pipeline.getId(), 1))
        .assertNext(found -> {
          assertEquals(pipeline.getId(), found.getPipelineId());
          assertEquals(1, found.getRunNo());
        })
        .verifyComplete();
  }

  @Test
  void findByPipelineIdAndRunNo_emptyForWrongRun() {
    Pipeline pipeline = pipelineRepository.save(Pipeline.builder()
        .repoId("abc123hash")
        .pipelineName("default")
        .gitRepo("git@github.com:user/repo.git")
        .build()).block();

    assertNotNull(pipeline);

    pipelineRunRepository.save(PipelineRun.builder()
        .pipelineId(pipeline.getId())
        .runNo(1)
        .status("success")
        .gitBranch("main")
        .gitHash("c3aefdaaabbccddee11223344556677889900aab")
        .startTime(start)
        .endTime(end)
        .build()).block();

    StepVerifier.create(
        pipelineRunRepository.findByPipelineIdAndRunNo(
            pipeline.getId(), 99))
        .verifyComplete();
  }

  @Test
  void findStagesByPipelineRunId_returnsMatchingStages() {
    Pipeline pipeline = pipelineRepository.save(Pipeline.builder()
        .repoId("abc123hash")
        .pipelineName("default")
        .gitRepo("git@github.com:user/repo.git")
        .build()).block();

    assertNotNull(pipeline);

    PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
        .pipelineId(pipeline.getId())
        .runNo(1)
        .status("success")
        .gitBranch("main")
        .gitHash("c3aefdaaabbccddee11223344556677889900aab")
        .startTime(start)
        .endTime(end)
        .build()).block();

    assertNotNull(run);

    stageRunRepository.save(StageRun.builder()
        .pipelineRunId(run.getId())
        .stageName("build")
        .status("success")
        .startTime(start.plusMinutes(1))
        .endTime(end.minusMinutes(3))
        .build()).block();

    StepVerifier.create(
        stageRunRepository.findByPipelineRunId(run.getId()))
        .assertNext(found -> {
          assertEquals("build", found.getStageName());
          assertEquals("success", found.getStatus());
          assertEquals(run.getId(), found.getPipelineRunId());
        })
        .verifyComplete();
  }

  @Test
  void findStageByPipelineRunIdAndName_returnsExactMatch() {
    Pipeline pipeline = pipelineRepository.save(Pipeline.builder()
        .repoId("abc123hash")
        .pipelineName("default")
        .gitRepo("git@github.com:user/repo.git")
        .build()).block();

    assertNotNull(pipeline);

    PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
        .pipelineId(pipeline.getId())
        .runNo(1)
        .status("success")
        .gitBranch("main")
        .gitHash("c3aefdaaabbccddee11223344556677889900aab")
        .startTime(start)
        .endTime(end)
        .build()).block();

    assertNotNull(run);

    stageRunRepository.save(StageRun.builder()
        .pipelineRunId(run.getId())
        .stageName("build")
        .status("success")
        .startTime(start.plusMinutes(1))
        .endTime(end.minusMinutes(3))
        .build()).block();

    StepVerifier.create(stageRunRepository
        .findByPipelineRunIdAndStageName(run.getId(), "build"))
        .assertNext(found -> assertEquals("build", found.getStageName()))
        .verifyComplete();
  }

  @Test
  void findJobsByStageRunId_returnsMatchingJobs() {
    Pipeline pipeline = pipelineRepository.save(Pipeline.builder()
        .repoId("abc123hash")
        .pipelineName("default")
        .gitRepo("git@github.com:user/repo.git")
        .build()).block();

    assertNotNull(pipeline);

    PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
        .pipelineId(pipeline.getId())
        .runNo(1)
        .status("success")
        .gitBranch("main")
        .gitHash("c3aefdaaabbccddee11223344556677889900aab")
        .startTime(start)
        .endTime(end)
        .build()).block();

    assertNotNull(run);

    StageRun stage = stageRunRepository.save(StageRun.builder()
        .pipelineRunId(run.getId())
        .stageName("build")
        .status("success")
        .startTime(start.plusMinutes(1))
        .endTime(end.minusMinutes(3))
        .build()).block();

    assertNotNull(stage);

    jobRunRepository.save(JobRun.builder()
        .stageRunId(stage.getId())
        .jobName("compile")
        .status("success")
        .startTime(start.plusMinutes(1))
        .endTime(end.minusMinutes(3))
        .build()).block();

    StepVerifier.create(
        jobRunRepository.findByStageRunId(stage.getId()))
        .assertNext(found -> {
          assertEquals("compile", found.getJobName());
          assertEquals("success", found.getStatus());
          assertEquals(stage.getId(), found.getStageRunId());
        })
        .verifyComplete();
  }

  @Test
  void findJobByStageRunIdAndName_returnsExactMatch() {
    Pipeline pipeline = pipelineRepository.save(Pipeline.builder()
        .repoId("abc123hash")
        .pipelineName("default")
        .gitRepo("git@github.com:user/repo.git")
        .build()).block();

    assertNotNull(pipeline);

    PipelineRun run = pipelineRunRepository.save(PipelineRun.builder()
        .pipelineId(pipeline.getId())
        .runNo(1)
        .status("success")
        .gitBranch("main")
        .gitHash("c3aefdaaabbccddee11223344556677889900aab")
        .startTime(start)
        .endTime(end)
        .build()).block();

    assertNotNull(run);

    StageRun stage = stageRunRepository.save(StageRun.builder()
        .pipelineRunId(run.getId())
        .stageName("build")
        .status("success")
        .startTime(start.plusMinutes(1))
        .endTime(end.minusMinutes(3))
        .build()).block();

    assertNotNull(stage);

    jobRunRepository.save(JobRun.builder()
        .stageRunId(stage.getId())
        .jobName("compile")
        .status("success")
        .startTime(start.plusMinutes(1))
        .endTime(end.minusMinutes(3))
        .build()).block();

    StepVerifier.create(jobRunRepository
        .findByStageRunIdAndJobName(stage.getId(), "compile"))
        .assertNext(found -> assertEquals("compile", found.getJobName()))
        .verifyComplete();
  }
}
