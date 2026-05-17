package edu.northeastern.cs7580.cicd.apigateway.service;

import edu.northeastern.cs7580.cicd.apigateway.dto.PipelineExecutionRequest;
import edu.northeastern.cs7580.cicd.apigateway.dto.PipelineExecutionResponse;
import edu.northeastern.cs7580.cicd.apigateway.exception.GlobalExceptionHandler;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reactive HTTP client for communicating with the Execution Service.
 *
 * <p>This client uses Spring WebFlux {@link WebClient} for non-blocking HTTP
 * communication with the Execution Service. It is responsible for forwarding
 * pipeline execution requests from the API Gateway to the backend service that
 * handles Git workspace preparation, Docker container orchestration, and
 * sequential job execution.
 *
 * <p><b>Configuration:</b>
 * The Execution Service base URL is resolved from the
 * {@code services.execution.url} application property, which can be overridden
 * via the {@code EXECUTION_SERVICE_URL} environment variable. This allows the
 * same binary to target different environments without code changes:
 * <ul>
 *   <li><b>Local development</b> — defaults to {@code http://localhost:8081}</li>
 *   <li><b>Docker Compose</b> — set to the service hostname (e.g.
 *       {@code http://execution-service:8081})</li>
 *   <li><b>Kubernetes</b> — set to the in-cluster service DNS name</li>
 * </ul>
 *
 * <p><b>Error handling:</b>
 * 4xx and 5xx responses from the Execution Service surface as
 * {@link org.springframework.web.reactive.function.client.WebClientResponseException}
 * and are caught by {@link GlobalExceptionHandler}, which forwards the original
 * status code and body to the caller.
 *
 * @see GlobalExceptionHandler
 */
@Service
public class ExecutionServiceClient {

  private static final Logger log = LoggerFactory.getLogger(ExecutionServiceClient.class);
  private final WebClient webClient;

  /**
   * Constructs an {@code ExecutionServiceClient} and initializes the underlying
   * {@link WebClient} with the configured base URL.
   *
   * @param executionServiceUrl base URL of the Execution Service; resolved from
   *                            the {@code services.execution.url} property or the
   *                            {@code EXECUTION_SERVICE_URL} environment variable;
   *                            defaults to {@code http://localhost:8081} if neither
   *                            is set
   */
  public ExecutionServiceClient(
      @Value("${services.execution.url:http://localhost:8081}") String executionServiceUrl) {

    this.webClient = WebClient.builder()
        .baseUrl(executionServiceUrl)
        .build();

    log.info("Execution Service client initialized with URL: {}", executionServiceUrl);
  }

  /**
   * Forwards a pipeline execution request to the Execution Service and returns
   * the execution result.
   *
   * <p>Sends a {@code POST} request to
   * {@code /api/v1/execution/execute} with the request serialized as JSON.
   * The Execution Service clones the repository, reads the pipeline YAML,
   * executes all jobs sequentially in Docker containers, and returns the
   * final execution outcome.
   *
   * <p><b>Timeout:</b> the request will be cancelled and a
   * {@link java.util.concurrent.TimeoutException} emitted if no response is
   * received within 5 minutes. This accommodates long-running pipelines
   * while preventing indefinite blocking.
   *
   * <p><b>Error propagation:</b> HTTP 4xx and 5xx responses are surfaced as
   * {@link org.springframework.web.reactive.function.client.WebClientResponseException}
   * and handled by {@link GlobalExceptionHandler}.
   *
   * @param request the pipeline execution request containing the pipeline name,
   *                YAML file path, and Git metadata; must not be {@code null}
   * @return a {@link Mono} emitting the {@link PipelineExecutionResponse} returned
   *         by the Execution Service; errors are emitted as
   *         {@code WebClientResponseException} or {@code TimeoutException}
   */
  public Mono<PipelineExecutionResponse> executePipeline(PipelineExecutionRequest request) {
    log.debug("Forwarding execution request: {}", request.getPipelineName());

    return webClient.post()
        .uri("/api/v1/execution/execute")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .retrieve()
        .bodyToMono(PipelineExecutionResponse.class)
        .timeout(Duration.ofMinutes(5));
  }
}