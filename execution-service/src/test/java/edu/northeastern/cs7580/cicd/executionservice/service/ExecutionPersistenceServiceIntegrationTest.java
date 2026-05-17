package edu.northeastern.cs7580.cicd.executionservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.northeastern.cs7580.cicd.executionservice.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.executionservice.entity.JobRunEntity;
import edu.northeastern.cs7580.cicd.executionservice.entity.StageRunEntity;
import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ExecutionPersistenceServiceIntegrationTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("cicd")
          .withUsername("postgres")
          .withPassword("cicd");

  private static final GitMetadata GIT = GitMetadata.builder()
      .repositoryUrl("https://github.com/org/repo.git")
      .branch("main")
      .commitHash("abc123")
      .build();

  @Autowired
  private ExecutionPersistenceService service;

  @Autowired
  private edu.northeastern.cs7580.cicd.executionservice.repository.JobRunRepository jobRunRepo;

  @Autowired
  private edu.northeastern.cs7580.cicd.executionservice.repository.StageRunRepository stageRunRepo;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);

    registry.add("spring.r2dbc.host", POSTGRES::getHost);
    registry.add("spring.r2dbc.port", () -> POSTGRES.getMappedPort(5432));
    registry.add("spring.r2dbc.database", POSTGRES::getDatabaseName);
    registry.add("spring.r2dbc.username", POSTGRES::getUsername);
    registry.add("spring.r2dbc.password", POSTGRES::getPassword);

    registry.add("spring.flyway.enabled", () -> true);

    registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> false);
    registry.add("spring.session.store-type", () -> "none");
    registry.add("management.health.rabbit.enabled", () -> false);
    registry.add("management.health.redis.enabled", () -> false);
  }

  @Test
  void preCreateStagesAndJobs_persistsAllowFailuresFlags() {
    Job required = Job.builder()
        .name("compile")
        .stage("build")
        .image("alpine:3.20")
        .script("echo compile")
        .failures(true)
        .build();

    Job optionalFalse = Job.builder()
        .name("lint")
        .stage("build")
        .image("alpine:3.20")
        .script("echo lint")
        .failures(false)
        .build();

    Job defaultFalse = Job.builder()
        .name("docs")
        .stage("build")
        .image("alpine:3.20")
        .script("echo docs")
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(List.of(required, optionalFalse, defaultFalse))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(stage))
        .build();

    Long pipelineId = service.upsertPipeline("demo", GIT)
        .block(Duration.ofSeconds(5));
    assertThat(pipelineId).isNotNull();

    var pipelineRun = service.createPipelineRun(pipelineId, GIT, ExecutionStatus.RUNNING)
        .block(Duration.ofSeconds(5));
    assertThat(pipelineRun).isNotNull();

    service.preCreateStagesAndJobs(pipelineRun.getId(), plan)
        .block(Duration.ofSeconds(5));

    List<Long> stageRunIds = stageRunRepo.findAll()
        .filter(stageRun -> pipelineRun.getId().equals(stageRun.getPipelineRunId()))
        .map(StageRunEntity::getId)
        .collectList()
        .block(Duration.ofSeconds(5));

    List<JobRunEntity> jobRuns = jobRunRepo.findAll()
        .filter(jobRun -> stageRunIds.contains(jobRun.getStageRunId()))
        .collectList()
        .block(Duration.ofSeconds(5));

    assertThat(jobRuns).isNotNull();
    Map<String, Boolean> failuresByName = jobRuns.stream()
        .collect(Collectors.toMap(JobRunEntity::getJobName, JobRunEntity::isFailures));

    assertThat(failuresByName.get("compile")).isTrue();
    assertThat(failuresByName.get("lint")).isFalse();
    assertThat(failuresByName.get("docs")).isFalse();
  }
}
