package edu.northeastern.cs7580.cicd.apigateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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


class ReportServiceClientTest {

  private MockWebServer mockWebServer;
  private ReportServiceClient client;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    String baseUrl = mockWebServer.url("/").toString();
    client = new ReportServiceClient(baseUrl);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void getPipelineReport_Success_ReturnsResponse() throws Exception {
    String responseBody = "{\"pipeline\":{\"name\":\"default\",\"runs\":[]}}";

    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(responseBody));

    Mono<String> result = client.getPipelineReport("default");

    StepVerifier.create(result)
        .assertNext(body -> assertEquals(responseBody, body))
        .verifyComplete();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("GET", recordedRequest.getMethod());
    assertEquals("/api/v1/report/pipelines/default", recordedRequest.getPath());
  }

  @Test
  void getRunReport_Success_ReturnsResponse() throws Exception {
    String responseBody = "{\"pipeline\":{\"name\":\"default\",\"run-no\":1}}";

    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(responseBody));

    Mono<String> result = client.getRunReport("default", 1);

    StepVerifier.create(result)
        .assertNext(body -> assertEquals(responseBody, body))
        .verifyComplete();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("GET", recordedRequest.getMethod());
    assertEquals("/api/v1/report/pipelines/default/runs/1", recordedRequest.getPath());
  }

  @Test
  void getStageReport_Success_ReturnsResponse() throws Exception {
    String responseBody = "{\"pipeline\":{\"name\":\"default\",\"stage\":{\"name\":\"build\"}}}";

    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(responseBody));

    Mono<String> result = client.getStageReport("default", 1, "build");

    StepVerifier.create(result)
        .assertNext(body -> assertEquals(responseBody, body))
        .verifyComplete();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("GET", recordedRequest.getMethod());
    assertEquals("/api/v1/report/pipelines/default/runs/1/stages/build",
        recordedRequest.getPath());
  }

  @Test
  void getJobReport_Success_ReturnsResponse() throws Exception {
    String responseBody = "{\"pipeline\":{\"name\":\"default\",\"job\":{\"name\":\"compile\"}}}";

    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(responseBody));

    Mono<String> result = client.getJobReport("default", 1, "build", "compile");

    StepVerifier.create(result)
        .assertNext(body -> assertEquals(responseBody, body))
        .verifyComplete();

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("GET", recordedRequest.getMethod());
    assertEquals("/api/v1/report/pipelines/default/runs/1/stages/build/jobs/compile",
        recordedRequest.getPath());
  }

  @Test
  void getPipelineReport_BackendReturns404_ThrowsException() {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(404)
        .setBody("Pipeline not found"));

    Mono<String> result = client.getPipelineReport("nonexistent");

    StepVerifier.create(result)
        .expectError(WebClientResponseException.NotFound.class)
        .verify();
  }

  @Test
  void getRunReport_BackendReturns404_ThrowsException() {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(404)
        .setBody("Run not found"));

    Mono<String> result = client.getRunReport("default", 999);

    StepVerifier.create(result)
        .expectError(WebClientResponseException.NotFound.class)
        .verify();
  }

  @Test
  void getStageReport_BackendReturns404_ThrowsException() {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(404)
        .setBody("Stage not found"));

    Mono<String> result = client.getStageReport("default", 1, "nonexistent");

    StepVerifier.create(result)
        .expectError(WebClientResponseException.NotFound.class)
        .verify();
  }

  @Test
  void getJobReport_BackendReturns404_ThrowsException() {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(404)
        .setBody("Job not found"));

    Mono<String> result = client.getJobReport("default", 1, "build", "nonexistent");

    StepVerifier.create(result)
        .expectError(WebClientResponseException.NotFound.class)
        .verify();
  }

  @Test
  void getPipelineReport_BackendReturns500_ThrowsException() {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(500)
        .setBody("Internal server error"));

    Mono<String> result = client.getPipelineReport("default");

    StepVerifier.create(result)
        .expectError(WebClientResponseException.InternalServerError.class)
        .verify();
  }

  @Test
  void constructor_InitializesWithCorrectUrl() {
    String customUrl = "http://custom-report-service:9999";
    ReportServiceClient customClient = new ReportServiceClient(customUrl);

    assertNotNull(customClient);
  }
}
