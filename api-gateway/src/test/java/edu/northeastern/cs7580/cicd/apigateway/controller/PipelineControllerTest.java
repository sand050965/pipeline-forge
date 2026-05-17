package edu.northeastern.cs7580.cicd.apigateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.apigateway.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.apigateway.dto.PipelineExecutionRequest;
import edu.northeastern.cs7580.cicd.apigateway.dto.PipelineExecutionResponse;
import edu.northeastern.cs7580.cicd.apigateway.service.ExecutionServiceClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PipelineControllerTest {

  @Mock
  private ExecutionServiceClient executionServiceClient;

  @InjectMocks
  private PipelineController pipelineController;

  private PipelineExecutionRequest request;
  private PipelineExecutionResponse response;

  @BeforeEach
  void setUp() {
    request = PipelineExecutionRequest.builder()
        .pipelineName("test-pipeline")
        .build();

    response = PipelineExecutionResponse.builder()
        .pipelineName("test-pipeline")
        .executionId("exec-123")
        .status("QUEUED")
        .build();
  }

  @Test
  void executePipeline_success_returns201() {
    when(executionServiceClient.executePipeline(any())).thenReturn(Mono.just(response));

    Mono<ResponseEntity<PipelineExecutionResponse>> result =
        pipelineController.executePipeline(request);

    StepVerifier.create(result)
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.CREATED
                && "exec-123".equals(entity.getBody().getExecutionId())
                && "QUEUED".equals(entity.getBody().getStatus()))
        .verifyComplete();
  }

  @Test
  void executePipeline_clientError_returns500() {
    when(executionServiceClient.executePipeline(any()))
        .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

    Mono<ResponseEntity<PipelineExecutionResponse>> result =
        pipelineController.executePipeline(request);

    StepVerifier.create(result)
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR
                && "FAILED".equals(entity.getBody().getStatus()))
        .verifyComplete();
  }

  @Test
  void executePipeline_webClientError_withJsonBody_forwardsStatusAndBody() {
    String jsonBody = "{\"executionId\":\"exec-400\",\"pipelineName\":\"test-pipeline\","
        + "\"runNumber\":0,\"status\":\"FAILED\",\"message\":\"Bad request\"}";
    WebClientResponseException ex = WebClientResponseException.create(
        400,
        "Bad Request",
        null,
        jsonBody.getBytes(StandardCharsets.UTF_8),
        StandardCharsets.UTF_8
    );
    when(executionServiceClient.executePipeline(any()))
        .thenReturn(Mono.error(ex));

    Mono<ResponseEntity<PipelineExecutionResponse>> result =
        pipelineController.executePipeline(request);

    StepVerifier.create(result)
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.BAD_REQUEST
                && "exec-400".equals(entity.getBody().getExecutionId())
                && "FAILED".equals(entity.getBody().getStatus())
                && "Bad request".equals(entity.getBody().getMessage()))
        .verifyComplete();
  }

  @Test
  void executePipeline_webClientError_withInvalidJson_returnsFailedWrapper() {
    String body = "not-json";
    WebClientResponseException ex = WebClientResponseException.create(
        502,
        "Bad Gateway",
        null,
        body.getBytes(StandardCharsets.UTF_8),
        StandardCharsets.UTF_8
    );
    when(executionServiceClient.executePipeline(any()))
        .thenReturn(Mono.error(ex));

    Mono<ResponseEntity<PipelineExecutionResponse>> result =
        pipelineController.executePipeline(request);

    StepVerifier.create(result)
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.BAD_GATEWAY
                && "FAILED".equals(entity.getBody().getStatus())
                && body.equals(entity.getBody().getMessage()))
        .verifyComplete();
  }

  @Test
  void health_returns200WithMessage() {
    StepVerifier.create(pipelineController.health())
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && "API Gateway is healthy".equals(entity.getBody()))
        .verifyComplete();
  }

  @Test
  void executePipeline_success_withNullGitMetadata() {
    PipelineExecutionRequest noGitRequest = PipelineExecutionRequest.builder()
        .pipelineName("test-pipeline")
        .gitMetadata(null)
        .build();
    when(executionServiceClient.executePipeline(any())).thenReturn(Mono.just(response));

    StepVerifier.create(pipelineController.executePipeline(noGitRequest))
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.CREATED
                && "exec-123".equals(entity.getBody().getExecutionId()))
        .verifyComplete();
  }

  @Test
  void executePipeline_success_withGitMetadata() {
    PipelineExecutionRequest gitRequest = PipelineExecutionRequest.builder()
        .pipelineName("test-pipeline")
        .gitMetadata(GitMetadata.builder()
            .repositoryUrl("https://example.com/repo.git")
            .branch("main")
            .commitHash("abc123")
            .build())
        .build();
    when(executionServiceClient.executePipeline(any())).thenReturn(Mono.just(response));

    StepVerifier.create(pipelineController.executePipeline(gitRequest))
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.CREATED
                && "exec-123".equals(entity.getBody().getExecutionId()))
        .verifyComplete();
  }
}
