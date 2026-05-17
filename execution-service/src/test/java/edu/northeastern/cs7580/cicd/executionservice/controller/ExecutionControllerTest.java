package edu.northeastern.cs7580.cicd.executionservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.executionservice.config.RabbitMqConfig;
import edu.northeastern.cs7580.cicd.executionservice.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionMessage;
import edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionRequest;
import edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionResponse;
import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import edu.northeastern.cs7580.cicd.executionservice.service.ExecutionPersistenceService;
import edu.northeastern.cs7580.cicd.executionservice.service.ExecutionService;
import edu.northeastern.cs7580.cicd.executionservice.service.WorkspaceService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ExecutionControllerTest {

  @Mock private ExecutionService executionService;
  @Mock private ExecutionPersistenceService persistenceService;
  @Mock private WorkspaceService workspaceService;
  @Mock private RabbitTemplate rabbitTemplate;
  @Mock private PipelineService pipelineService;
  @Mock private ExecutionPlan mockPlan;
  @Mock private StageExecution mockStage;

  private WebTestClient client;
  private Path fakeWorkspace;

  private static final Long RUN_ID = 10L;
  private static final int RUN_NO = 5;
  private static final String PIPELINE_NAME = "CI";
  private static final String PIPELINE_FILE = ".pipelines/ci.yaml";

  private static final GitMetadata GIT = GitMetadata.builder()
      .repositoryUrl("https://github.com/org/repo.git")
      .branch("main")
      .commitHash("abc123")
      .build();

  private static final PipelineExecutionMessage ENQUEUED_MESSAGE =
      PipelineExecutionMessage.builder()
          .runId(RUN_ID)
          .runNo(RUN_NO)
          .pipelineName(PIPELINE_NAME)
          .pipelineFilePath(PIPELINE_FILE)
          .gitMetadata(GIT)
          .idMap(Map.of())
          .build();

  @BeforeEach
  void setUp() {
    fakeWorkspace = Path.of("/tmp/fake-workspace");
    ExecutionController controller = new ExecutionController(
        executionService, persistenceService, workspaceService, rabbitTemplate, pipelineService);
    client = WebTestClient.bindToController(controller).build();
  }

  // -------------------------------------------------------------------------
  // Happy path
  // -------------------------------------------------------------------------

  @Test
  void executePipeline_validRequest_returns202() {
    stubHappyPath();

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void executePipeline_validRequest_returnsRunNumberAndPipelineName() {
    stubHappyPath();

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectBody(PipelineExecutionResponse.class)
        .value(resp -> {
          assert resp.getRunNumber() == RUN_NO;
          assert resp.getPipelineName().equals(PIPELINE_NAME);
          assert resp.getStatus() == ExecutionStatus.PENDING;
        });
  }

  @Test
  void executePipeline_validRequest_publishesToRabbitMq() {
    stubHappyPath();

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.ACCEPTED);

    verify(rabbitTemplate).convertAndSend(
        eq(RabbitMqConfig.EXCHANGE_NAME),
        eq(RabbitMqConfig.ROUTING_KEY),
        eq(ENQUEUED_MESSAGE));
  }

  @Test
  void executePipeline_validRequest_cleansUpWorkspace() {
    stubHappyPath();

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.ACCEPTED);

    verify(workspaceService).cleanupWorkspace(fakeWorkspace);
  }

  @Test
  void executePipeline_validRequest_doesNotCallExecuteSequential() {
    stubHappyPath();

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.ACCEPTED);

    verify(executionService, never()).executeSequential(any(), any(), any(), any());
  }

  // -------------------------------------------------------------------------
  // Validation failure (400)
  // -------------------------------------------------------------------------

  @Test
  void executePipeline_yamlInvalid_returns400() {
    when(workspaceService.prepareWorkspace(any())).thenReturn(fakeWorkspace);
    when(pipelineService.createExecutionPlan(any()))
        .thenThrow(new ValidationException("missing stages"));

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody(PipelineExecutionResponse.class)
        .value(resp -> {
          assert resp.getStatus() == ExecutionStatus.VALIDATION_FAILED;
          assert resp.getRunNumber() == 0;
          assert resp.getMessage().contains("missing stages");
        });
  }

  @Test
  void executePipeline_yamlInvalid_doesNotTouchDb() {
    when(workspaceService.prepareWorkspace(any())).thenReturn(fakeWorkspace);
    when(pipelineService.createExecutionPlan(any()))
        .thenThrow(new ValidationException("bad yaml"));

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isBadRequest();

    verify(executionService, never()).initializePipelineRun(any(), any(), any(), any());
  }

  @Test
  void executePipeline_missingPipelineName_returns400() {
    client.post().uri("/api/v1/execution/execute")
        .bodyValue(PipelineExecutionRequest.builder()
            .pipelineName("").pipelineFilePath(PIPELINE_FILE).gitMetadata(GIT).build())
        .exchange()
        .expectStatus().isBadRequest();

    verify(workspaceService, never()).prepareWorkspace(any());
  }

  @Test
  void executePipeline_missingGitMetadata_returns400() {
    client.post().uri("/api/v1/execution/execute")
        .bodyValue(PipelineExecutionRequest.builder()
            .pipelineName(PIPELINE_NAME).pipelineFilePath(PIPELINE_FILE).gitMetadata(null).build())
        .exchange()
        .expectStatus().isBadRequest();

    verify(workspaceService, never()).prepareWorkspace(any());
  }

  // -------------------------------------------------------------------------
  // DB initialization failure (500, no orphan records)
  // -------------------------------------------------------------------------

  @Test
  void executePipeline_dbInitFails_returns500() {
    when(workspaceService.prepareWorkspace(any())).thenReturn(fakeWorkspace);
    when(pipelineService.createExecutionPlan(any())).thenReturn(mockPlan);
    when(mockPlan.getStages()).thenReturn(List.of(mockStage));
    when(mockStage.getJobs()).thenReturn(List.of());
    when(executionService.initializePipelineRun(any(), any(), any(), any()))
        .thenReturn(Mono.empty());

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        .expectBody(PipelineExecutionResponse.class)
        .value(resp -> {
          assert resp.getRunNumber() == 0;
          assert resp.getMessage().contains("Internal error");
        });
  }

  @Test
  void executePipeline_dbInitFails_doesNotPublishToQueue() {
    when(workspaceService.prepareWorkspace(any())).thenReturn(fakeWorkspace);
    when(pipelineService.createExecutionPlan(any())).thenReturn(mockPlan);
    when(mockPlan.getStages()).thenReturn(List.of(mockStage));
    when(mockStage.getJobs()).thenReturn(List.of());
    when(executionService.initializePipelineRun(any(), any(), any(), any()))
        .thenReturn(Mono.empty());

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

    verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
  }

  // -------------------------------------------------------------------------
  // RabbitMQ publish failure (500, DB rolled back)
  // -------------------------------------------------------------------------

  @Test
  void executePipeline_rabbitPublishFails_returns500() {
    when(workspaceService.prepareWorkspace(any())).thenReturn(fakeWorkspace);
    when(pipelineService.createExecutionPlan(any())).thenReturn(mockPlan);
    when(mockPlan.getStages()).thenReturn(List.of(mockStage));
    when(mockStage.getJobs()).thenReturn(List.of());
    when(executionService.initializePipelineRun(any(), any(), any(), any()))
        .thenReturn(Mono.just(ENQUEUED_MESSAGE));
    doThrow(new AmqpException("broker unavailable"))
        .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    when(persistenceService.rollbackPipelineRun(any())).thenReturn(Mono.empty());

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void executePipeline_rabbitPublishFails_rollsBackRunRecord() {
    when(workspaceService.prepareWorkspace(any())).thenReturn(fakeWorkspace);
    when(pipelineService.createExecutionPlan(any())).thenReturn(mockPlan);
    when(mockPlan.getStages()).thenReturn(List.of(mockStage));
    when(mockStage.getJobs()).thenReturn(List.of());
    when(executionService.initializePipelineRun(any(), any(), any(), any()))
        .thenReturn(Mono.just(ENQUEUED_MESSAGE));
    doThrow(new AmqpException("broker unavailable"))
        .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    when(persistenceService.rollbackPipelineRun(any())).thenReturn(Mono.empty());

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

    verify(persistenceService).rollbackPipelineRun(RUN_ID);
  }

  // -------------------------------------------------------------------------
  // Workspace clone failure (500)
  // -------------------------------------------------------------------------

  @Test
  void executePipeline_workspaceCloneFails_returns500() {
    when(workspaceService.prepareWorkspace(any()))
        .thenThrow(new RuntimeException("Git clone failed"));

    client.post().uri("/api/v1/execution/execute")
        .bodyValue(request())
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private void stubHappyPath() {
    when(workspaceService.prepareWorkspace(any())).thenReturn(fakeWorkspace);
    when(pipelineService.createExecutionPlan(any())).thenReturn(mockPlan);
    when(mockPlan.getStages()).thenReturn(List.of(mockStage));
    when(mockStage.getJobs()).thenReturn(List.of());
    when(executionService.initializePipelineRun(any(), any(), any(), any()))
        .thenReturn(Mono.just(ENQUEUED_MESSAGE));
  }

  private PipelineExecutionRequest request() {
    return PipelineExecutionRequest.builder()
        .pipelineName(PIPELINE_NAME)
        .pipelineFilePath(PIPELINE_FILE)
        .gitMetadata(GIT)
        .build();
  }
}
