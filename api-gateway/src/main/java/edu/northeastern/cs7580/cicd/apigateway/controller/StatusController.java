package edu.northeastern.cs7580.cicd.apigateway.controller;

import edu.northeastern.cs7580.cicd.apigateway.service.ReportServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller that proxies pipeline status queries from the CLI to the Report Service.
 *
 * <p>All endpoints forward requests without transformation, preserving the original
 * HTTP status codes and response bodies from the Report Service.
 *
 * <p>Supported endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/status?repo=} — active or most recent run for a repo</li>
 *   <li>{@code GET /api/v1/status/{pipeline}/runs/{runNo}} — specific run by name and number</li>
 * </ul>
 *
 * @see ReportServiceClient
 */
@RestController
@RequestMapping("/api/v1/status")
public class StatusController {

  private static final Logger log = LoggerFactory.getLogger(StatusController.class);

  private final ReportServiceClient reportServiceClient;

  /**
   * Constructs a {@code StatusController} with the required service client.
   *
   * @param reportServiceClient reactive client for Report Service communication
   */
  public StatusController(ReportServiceClient reportServiceClient) {
    this.reportServiceClient = reportServiceClient;
  }

  /**
   * Returns the status of the active or most recently completed run for the given repo URL.
   *
   * @param repo the repository URL to query
   * @return HTTP 200 with JSON status body, or the Report Service error response if not found
   */
  @GetMapping
  public Mono<ResponseEntity<String>> getStatusByRepo(@RequestParam String repo) {
    log.info("Routing status request for repo: {}", repo);
    return reportServiceClient.getStatusByRepo(repo)
        .map(body -> ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(body));
  }

  /**
   * Returns the status of a specific run of the named pipeline.
   *
   * @param pipeline the pipeline name
   * @param runNo    the run number
   * @return HTTP 200 with JSON status body, or the Report Service error response if not found
   */
  @GetMapping("/{pipeline}/runs/{runNo}")
  public Mono<ResponseEntity<String>> getStatusByRun(
      @PathVariable String pipeline,
      @PathVariable int runNo) {
    log.info("Routing status request for pipeline: {}, run: {}", pipeline, runNo);
    return reportServiceClient.getStatusByRun(pipeline, runNo)
        .map(body -> ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(body));
  }
}
