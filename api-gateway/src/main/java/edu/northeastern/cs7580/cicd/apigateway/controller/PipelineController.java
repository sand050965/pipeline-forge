package edu.northeastern.cs7580.cicd.apigateway.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.cs7580.cicd.apigateway.dto.PipelineExecutionRequest;
import edu.northeastern.cs7580.cicd.apigateway.dto.PipelineExecutionResponse;
import edu.northeastern.cs7580.cicd.apigateway.service.ExecutionServiceClient;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * REST controller that serves as the unified entry point for all pipeline
 * operations in the API Gateway.
 *
 * <p>This controller receives requests from the CLI and routes them to the
 * appropriate backend microservice. Currently supported operations:
 * <ul>
 *   <li><b>Pipeline execution</b> — forwarded to the Execution Service</li>
 * </ul>
 *
 * <p>All endpoints are reactive and return {@link Mono} types for non-blocking
 * I/O when communicating with backend microservices.
 *
 * <p><b>Error handling strategy:</b>
 * <ul>
 *   <li>{@link WebClientResponseException} — the backend HTTP status and body are
 *       preserved and forwarded to the caller unchanged</li>
 *   <li>All other exceptions — translated to HTTP 500 with a {@code FAILED} status
 *       body containing the error message</li>
 * </ul>
 *
 * @see ExecutionServiceClient
 */
@RestController
@RequestMapping("/api/v1/pipelines")
public class PipelineController {

  private static final Logger log = LoggerFactory.getLogger(PipelineController.class);

  private final ExecutionServiceClient executionServiceClient;

  /**
   * Constructs a {@code PipelineController} with the required service client.
   *
   * @param executionServiceClient reactive client for Execution Service communication
   */
  public PipelineController(ExecutionServiceClient executionServiceClient) {
    this.executionServiceClient = executionServiceClient;
  }

  /**
   * Executes a pipeline by forwarding the request to the Execution Service.
   *
   * <p>The request is validated before forwarding. On success, the Execution
   * Service response — including the execution ID, run number, and final status
   * — is returned directly to the caller.
   *
   * <p><b>Response status codes:</b>
   * <ul>
   *   <li>HTTP 201 — pipeline executed (status may be {@code SUCCESS} or {@code FAILED})</li>
   *   <li>HTTP 400 — request validation failed or pipeline YAML is invalid</li>
   *   <li>HTTP 500 — unexpected error or Execution Service unreachable</li>
   * </ul>
   *
   * <p><b>Error handling:</b>
   * <ul>
   *   <li>If the Execution Service returns a well-formed {@link PipelineExecutionResponse}
   *       error body, it is forwarded with the original status code.</li>
   *   <li>If the error body cannot be deserialized, the raw response string is
   *       wrapped in a {@code FAILED} response.</li>
   *   <li>Network or unexpected errors produce HTTP 500.</li>
   * </ul>
   *
   * @param request the pipeline execution request containing the pipeline name,
   *                pipeline file path, and Git metadata; must not be {@code null}
   *                and must pass Jakarta Bean Validation constraints
   * @return a {@link Mono} emitting a {@link ResponseEntity} containing the
   *         {@link PipelineExecutionResponse}; never empty
   */
  @PostMapping("/execute")
  public Mono<ResponseEntity<PipelineExecutionResponse>> executePipeline(
      @Valid @RequestBody PipelineExecutionRequest request) {

    log.info("Received pipeline execution request for: {}", request.getPipelineName());

    return executionServiceClient.executePipeline(request)
        .map(response -> {
          log.info("Pipeline execution initiated: {} (ID: {})",
              response.getPipelineName(), response.getExecutionId());
          return ResponseEntity.status(HttpStatus.CREATED).body(response);
        })
        .onErrorResume(WebClientResponseException.class, e -> {
          log.error("Execution Service returned error: {}", e.getStatusCode());
          try {
            PipelineExecutionResponse errorBody = new ObjectMapper()
                .readValue(e.getResponseBodyAsString(), PipelineExecutionResponse.class);
            return Mono.just(ResponseEntity.status(e.getStatusCode()).body(errorBody));
          } catch (JsonProcessingException ex) {
            return Mono.just(ResponseEntity.status(e.getStatusCode())
                .body(PipelineExecutionResponse.builder()
                    .status("FAILED")
                    .message(e.getResponseBodyAsString())
                    .build()));
          }
        })
        .onErrorResume(e -> {
          log.error("Failed to execute pipeline: {}", e.getMessage(), e);
          return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(PipelineExecutionResponse.builder()
                  .status("FAILED")
                  .message("Failed to execute pipeline: " + e.getMessage())
                  .build()));
        });
  }

  /**
   * Health check endpoint for the API Gateway.
   *
   * <p>Returns a plain-text confirmation that the API Gateway is running.
   * This endpoint does not check the health of downstream services.
   *
   * @return a {@link Mono} emitting HTTP 200 with a health status message
   */
  @GetMapping("/health")
  public Mono<ResponseEntity<String>> health() {
    return Mono.just(ResponseEntity.ok("API Gateway is healthy"));
  }
}
