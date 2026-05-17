package edu.northeastern.cs7580.cicd.apigateway.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.apigateway.service.ReportServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

  @Mock
  private ReportServiceClient reportServiceClient;

  @InjectMocks
  private ReportController reportController;

  @Test
  void getPipelineReport_success_returns200() {
    String responseBody = "{\"pipeline\":{\"name\":\"default\",\"runs\":[]}}";
    when(reportServiceClient.getPipelineReport("default"))
        .thenReturn(Mono.just(responseBody));

    Mono<ResponseEntity<String>> result =
        reportController.getPipelineReport("default");

    StepVerifier.create(result)
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && entity.getHeaders().getContentType().equals(MediaType.APPLICATION_JSON)
                && responseBody.equals(entity.getBody()))
        .verifyComplete();
  }

  @Test
  void getRunReport_success_returns200() {
    String responseBody = "{\"pipeline\":{\"name\":\"default\",\"run-no\":1}}";
    when(reportServiceClient.getRunReport("default", 1))
        .thenReturn(Mono.just(responseBody));

    Mono<ResponseEntity<String>> result =
        reportController.getRunReport("default", 1);

    StepVerifier.create(result)
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && responseBody.equals(entity.getBody()))
        .verifyComplete();
  }

  @Test
  void getStageReport_success_returns200() {
    String responseBody = "{\"pipeline\":{\"name\":\"default\",\"stage\":{\"name\":\"build\"}}}";
    when(reportServiceClient.getStageReport("default", 1, "build"))
        .thenReturn(Mono.just(responseBody));

    Mono<ResponseEntity<String>> result =
        reportController.getStageReport("default", 1, "build");

    StepVerifier.create(result)
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && responseBody.equals(entity.getBody()))
        .verifyComplete();
  }

  @Test
  void getJobReport_success_returns200() {
    String responseBody = "{\"pipeline\":{\"name\":\"default\",\"job\":{\"name\":\"compile\"}}}";
    when(reportServiceClient.getJobReport("default", 1, "build", "compile"))
        .thenReturn(Mono.just(responseBody));

    Mono<ResponseEntity<String>> result =
        reportController.getJobReport("default", 1, "build", "compile");

    StepVerifier.create(result)
        .expectNextMatches(entity ->
            entity.getStatusCode() == HttpStatus.OK
                && responseBody.equals(entity.getBody()))
        .verifyComplete();
  }

  @Test
  void getPipelineReport_clientError_propagatesError() {
    when(reportServiceClient.getPipelineReport(anyString()))
        .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

    Mono<ResponseEntity<String>> result =
        reportController.getPipelineReport("default");

    StepVerifier.create(result)
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void getRunReport_clientError_propagatesError() {
    when(reportServiceClient.getRunReport(anyString(), anyInt()))
        .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

    Mono<ResponseEntity<String>> result =
        reportController.getRunReport("default", 1);

    StepVerifier.create(result)
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void getStageReport_clientError_propagatesError() {
    when(reportServiceClient.getStageReport(anyString(), anyInt(), anyString()))
        .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

    Mono<ResponseEntity<String>> result =
        reportController.getStageReport("default", 1, "build");

    StepVerifier.create(result)
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void getJobReport_clientError_propagatesError() {
    when(reportServiceClient.getJobReport(anyString(), anyInt(), anyString(), anyString()))
        .thenReturn(Mono.error(new RuntimeException("Service unavailable")));

    Mono<ResponseEntity<String>> result =
        reportController.getJobReport("default", 1, "build", "compile");

    StepVerifier.create(result)
        .expectError(RuntimeException.class)
        .verify();
  }
}
