package edu.northeastern.cs7580.cicd.apigateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.cs7580.cicd.apigateway.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.apigateway.dto.PipelineExecutionRequest;
import edu.northeastern.cs7580.cicd.apigateway.dto.PipelineExecutionResponse;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;


class ExecutionServiceClientTest {

  private MockWebServer mockWebServer;
  private ExecutionServiceClient client;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    String baseUrl = mockWebServer.url("/").toString();
    client = new ExecutionServiceClient(baseUrl);
    objectMapper = new ObjectMapper();
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void executePipeline_Success_ReturnsResponse() throws Exception {
    PipelineExecutionResponse mockResponse = PipelineExecutionResponse.builder()
        .executionId("exec-123")
        .pipelineName("default")
        .runNumber(1)
        .status("QUEUED")
        .message("Pipeline queued")
        .build();

    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(201)
        .setHeader("Content-Type", "application/json")
        .setBody(objectMapper.writeValueAsString(mockResponse)));

    PipelineExecutionRequest request = createValidRequest();

    Mono<PipelineExecutionResponse> result = client.executePipeline(request);

    StepVerifier.create(result)
        .assertNext(response -> {
          assertEquals("exec-123", response.getExecutionId());
          assertEquals("default", response.getPipelineName());
          assertEquals(1, response.getRunNumber());
          assertEquals("QUEUED", response.getStatus());
          assertEquals("Pipeline queued", response.getMessage());
        })
        .verifyComplete();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("POST", recordedRequest.getMethod());
    assertEquals("/api/v1/execution/execute", recordedRequest.getPath());
    assertEquals("application/json", recordedRequest.getHeader("Content-Type"));
  }

  @Test
  void executePipeline_SendsCorrectRequestBody() throws Exception {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(201)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"executionId\":\"exec-123\",\"status\":\"QUEUED\"}"));

    PipelineExecutionRequest request = createValidRequest();

    client.executePipeline(request).block();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    String requestBody = recordedRequest.getBody().readUtf8();

    assertTrue(requestBody.contains("\"pipelineName\":\"default\""));
    assertTrue(requestBody.contains("\"pipelineFilePath\":"));
    assertTrue(requestBody.contains("\"gitMetadata\":{"));
    assertTrue(requestBody.contains("\"repositoryUrl\":\"git@github.com:user/repo.git\""));
  }

  @Test
  void executePipeline_BackendReturns404_ThrowsException() {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(404)
        .setBody("Pipeline not found"));

    PipelineExecutionRequest request = createValidRequest();

    Mono<PipelineExecutionResponse> result = client.executePipeline(request);

    StepVerifier.create(result)
        .expectError(WebClientResponseException.NotFound.class)
        .verify();
  }

  @Test
  void executePipeline_BackendReturns500_ThrowsException() {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(500)
        .setBody("Internal server error"));

    PipelineExecutionRequest request = createValidRequest();

    Mono<PipelineExecutionResponse> result = client.executePipeline(request);

    StepVerifier.create(result)
        .expectError(WebClientResponseException.InternalServerError.class)
        .verify();
  }

  @Test
  void executePipeline_BackendReturns400_ThrowsException() {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(400)
        .setBody("Invalid request"));

    PipelineExecutionRequest request = createValidRequest();

    Mono<PipelineExecutionResponse> result = client.executePipeline(request);

    StepVerifier.create(result)
        .expectError(WebClientResponseException.BadRequest.class)
        .verify();
  }

  @Test
  void executePipeline_InvalidJson_ThrowsException() {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("invalid json{"));

    PipelineExecutionRequest request = createValidRequest();

    Mono<PipelineExecutionResponse> result = client.executePipeline(request);

    StepVerifier.create(result)
        .expectError()
        .verify();
  }

  @Test
  void executePipeline_MultipleRequests_WorksCorrectly() throws Exception {
    for (int i = 1; i <= 3; i++) {
      PipelineExecutionResponse mockResponse = PipelineExecutionResponse.builder()
          .executionId("exec-" + i)
          .pipelineName("default")
          .runNumber(i)
          .status("QUEUED")
          .build();

      mockWebServer.enqueue(new MockResponse()
          .setResponseCode(201)
          .setHeader("Content-Type", "application/json")
          .setBody(objectMapper.writeValueAsString(mockResponse)));
    }

    for (int i = 1; i <= 3; i++) {
      PipelineExecutionRequest request = createValidRequest();
      PipelineExecutionResponse response = client.executePipeline(request).block();

      assertNotNull(response);
      assertEquals("exec-" + i, response.getExecutionId());
      assertEquals(i, response.getRunNumber());
    }

    assertEquals(3, mockWebServer.getRequestCount());
  }

  @Test
  void executePipeline_DifferentPipelineNames_SendsCorrectly() throws Exception {
    String[] pipelineNames = {"build", "test", "deploy"};

    for (String name : pipelineNames) {
      mockWebServer.enqueue(new MockResponse()
          .setResponseCode(201)
          .setHeader("Content-Type", "application/json")
          .setBody("{\"executionId\":\"exec-123\",\"status\":\"QUEUED\"}"));
    }

    for (String name : pipelineNames) {
      PipelineExecutionRequest request = PipelineExecutionRequest.builder()
          .pipelineName(name)
          .pipelineFilePath(".pipelines/default.yaml")
          .gitMetadata(createValidGitMetadata())
          .build();

      client.executePipeline(request).block();

      RecordedRequest recordedRequest = mockWebServer.takeRequest();
      String body = recordedRequest.getBody().readUtf8();
      assertTrue(body.contains("\"pipelineName\":\"" + name + "\""));
    }
  }

  @Test
  void constructor_InitializesWithCorrectUrl() {
    String customUrl = "http://custom-service:9999";
    ExecutionServiceClient customClient = new ExecutionServiceClient(customUrl);

    assertNotNull(customClient);
  }

  private PipelineExecutionRequest createValidRequest() {
    return PipelineExecutionRequest.builder()
        .pipelineName("default")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();
  }

  private GitMetadata createValidGitMetadata() {
    return GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .branch("main")
        .commitHash("abc123def456")
        .build();
  }
}