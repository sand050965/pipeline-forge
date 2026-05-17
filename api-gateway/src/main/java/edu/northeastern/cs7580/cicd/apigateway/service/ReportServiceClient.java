package edu.northeastern.cs7580.cicd.apigateway.service;

import edu.northeastern.cs7580.cicd.apigateway.controller.ReportController;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reactive HTTP client for communicating with the Report Service.
 *
 * <p>This client uses Spring WebFlux {@link WebClient} for non-blocking HTTP
 * communication with the Report Service. It forwards report query requests from
 * the API Gateway to the backend service that queries pipeline execution history
 * from the database.
 *
 * <p>Responses are returned as raw JSON strings and passed through to the caller
 * without deserialization, preserving the Report Service's response structure
 * intact.
 *
 * <p><b>Configuration:</b>
 * The Report Service base URL is resolved from the {@code services.report.url}
 * application property, which can be overridden via the {@code REPORT_SERVICE_URL}
 * environment variable:
 * <ul>
 *   <li><b>Local development</b> — defaults to {@code http://localhost:8082}</li>
 *   <li><b>Docker Compose</b> — set to the service hostname (e.g.
 *       {@code http://report-service:8082})</li>
 *   <li><b>Kubernetes</b> — set to the in-cluster service DNS name</li>
 * </ul>
 *
 * <p><b>Error handling:</b>
 * 4xx and 5xx responses from the Report Service surface as
 * {@link org.springframework.web.reactive.function.client.WebClientResponseException}
 * and are caught by the global exception handler, which forwards the original
 * status code and body to the caller.
 *
 * @see ReportController
 */
@Service
public class ReportServiceClient {

  private static final Logger log = LoggerFactory.getLogger(ReportServiceClient.class);
  private final WebClient webClient;

  /**
   * Constructs a {@code ReportServiceClient} and initializes the underlying
   * {@link WebClient} with the configured base URL.
   *
   * @param reportServiceUrl base URL of the Report Service; resolved from the
   *                         {@code services.report.url} property or the
   *                         {@code REPORT_SERVICE_URL} environment variable;
   *                         defaults to {@code http://localhost:8082} if neither
   *                         is set
   */
  public ReportServiceClient(
      @Value("${services.report.url:http://localhost:8082}") String reportServiceUrl) {

    this.webClient = WebClient.builder()
        .baseUrl(reportServiceUrl)
        .build();

    log.info("Report Service client initialized with URL: {}", reportServiceUrl);
  }

  /**
   * Retrieves a summary of all runs for the specified pipeline.
   *
   * <p>Sends a {@code GET} request to
   * {@code /api/v1/report/pipelines/{pipeline}}. The response includes a list
   * of run summaries ordered by run number, each containing the run status,
   * Git metadata, and timestamps.
   *
   * @param pipeline the logical pipeline name as declared in the YAML configuration
   *                 (e.g. {@code "default"}, {@code "release-prod"}); must not be
   *                 {@code null} or blank
   * @return a {@link Mono} emitting the raw JSON response string from the Report
   *         Service; errors are emitted as {@code WebClientResponseException} or
   *         {@code TimeoutException} if no response is received within 30 seconds
   */
  public Mono<String> getPipelineReport(String pipeline) {
    log.debug("Fetching report for pipeline: {}", pipeline);

    return webClient.get()
        .uri("/api/v1/report/pipelines/{pipeline}", pipeline)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(30));
  }

  /**
   * Retrieves details of a specific run within a pipeline.
   *
   * <p>Sends a {@code GET} request to
   * {@code /api/v1/report/pipelines/{pipeline}/runs/{runNo}}. The response
   * includes the run summary and a breakdown of all stages, including
   * per-stage status and timing information.
   *
   * @param pipeline the logical pipeline name; must not be {@code null} or blank
   * @param runNo    the sequential run number scoped to the pipeline (e.g. {@code 1},
   *                 {@code 42})
   * @return a {@link Mono} emitting the raw JSON response string from the Report
   *         Service; errors are emitted as {@code WebClientResponseException} or
   *         {@code TimeoutException} if no response is received within 30 seconds
   */
  public Mono<String> getRunReport(String pipeline, int runNo) {
    log.debug("Fetching report for pipeline: {}, run: {}", pipeline, runNo);

    return webClient.get()
        .uri("/api/v1/report/pipelines/{pipeline}/runs/{runNo}", pipeline, runNo)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(30));
  }

  /**
   * Retrieves details of a specific stage within a pipeline run.
   *
   * <p>Sends a {@code GET} request to
   * {@code /api/v1/report/pipelines/{pipeline}/runs/{runNo}/stages/{stage}}.
   * The response includes the stage summary and a breakdown of all jobs in the
   * stage, including per-job status, exit codes, and timing information.
   *
   * @param pipeline the logical pipeline name; must not be {@code null} or blank
   * @param runNo    the sequential run number scoped to the pipeline
   * @param stage    the stage name as declared in the YAML configuration
   *                 (e.g. {@code "build"}, {@code "test"})
   * @return a {@link Mono} emitting the raw JSON response string from the Report
   *         Service; errors are emitted as {@code WebClientResponseException} or
   *         {@code TimeoutException} if no response is received within 30 seconds
   */
  public Mono<String> getStageReport(String pipeline, int runNo, String stage) {
    log.debug("Fetching report for pipeline: {}, run: {}, stage: {}",
        pipeline, runNo, stage);

    return webClient.get()
        .uri("/api/v1/report/pipelines/{pipeline}/runs/{runNo}/stages/{stage}",
            pipeline, runNo, stage)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(30));
  }

  /**
   * Retrieves details of a specific job within a stage of a pipeline run.
   *
   * <p>Sends a {@code GET} request to
   * {@code /api/v1/report/pipelines/{pipeline}/runs/{runNo}/stages/{stage}/jobs/{job}}.
   * The response includes full job execution details: status, exit code,
   * combined stdout/stderr output, and wall-clock execution duration.
   *
   * @param pipeline the logical pipeline name; must not be {@code null} or blank
   * @param runNo    the sequential run number scoped to the pipeline
   * @param stage    the stage name as declared in the YAML configuration
   * @param job      the job name as declared in the YAML configuration
   *                 (e.g. {@code "compile"}, {@code "unit-tests"})
   * @return a {@link Mono} emitting the raw JSON response string from the Report
   *         Service; errors are emitted as {@code WebClientResponseException} or
   *         {@code TimeoutException} if no response is received within 30 seconds
   */
  public Mono<String> getJobReport(String pipeline, int runNo, String stage, String job) {
    log.debug("Fetching report for pipeline: {}, run: {}, stage: {}, job: {}",
        pipeline, runNo, stage, job);

    return webClient.get()
        .uri("/api/v1/report/pipelines/{pipeline}/runs/{runNo}/stages/{stage}/jobs/{job}",
            pipeline, runNo, stage, job)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(30));
  }

  /**
   * Retrieves the status of the active or most recently completed run for
   * the given repository URL.
   *
   * @param repoUrl the repository URL to query
   * @return a {@link Mono} emitting the raw JSON status response
   */
  public Mono<String> getStatusByRepo(String repoUrl) {
    log.debug("Fetching status for repo: {}", repoUrl);

    return webClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/api/v1/status")
            .queryParam("repo", repoUrl)
            .build())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(30));
  }

  /**
   * Retrieves the status of a specific run of the named pipeline.
   *
   * @param pipeline the pipeline name
   * @param runNo    the run number
   * @return a {@link Mono} emitting the raw JSON status response
   */
  public Mono<String> getStatusByRun(String pipeline, int runNo) {
    log.debug("Fetching status for pipeline: {}, run: {}", pipeline, runNo);

    return webClient.get()
        .uri("/api/v1/status/{pipeline}/runs/{runNo}", pipeline, runNo)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(30));
  }
}