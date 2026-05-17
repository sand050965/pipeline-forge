package edu.northeastern.cs7580.cicd.reportservice.controller;

import edu.northeastern.cs7580.cicd.reportservice.dto.StatusResponse;
import edu.northeastern.cs7580.cicd.reportservice.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller exposing endpoints for querying live pipeline execution status.
 *
 * <p>Unlike the report endpoints (which return historical data), the status
 * endpoints return the current execution state of a pipeline run, including
 * the live RUNNING/PENDING status of each stage and job.
 *
 * <p>Supported endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/status?repo=} — active or most recent run for a repo</li>
 *   <li>{@code GET /api/v1/status/{pipeline}/runs/{runNo}} — specific run by name and number</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/status")
public class StatusController {

  private static final Logger log = LoggerFactory.getLogger(StatusController.class);

  private final ReportService reportService;

  /**
   * Creates a new status controller.
   *
   * @param reportService the service used to query execution status
   */
  public StatusController(ReportService reportService) {
    this.reportService = reportService;
  }

  /**
   * Returns the status of the active or most recently completed run for
   * the given repository URL.
   *
   * @param repo the repository URL (e.g. {@code https://github.com/org/repo})
   * @return HTTP 200 with the status response, or 404 if no pipeline or run exists
   */
  @GetMapping
  public Mono<ResponseEntity<StatusResponse>> getStatusByRepo(
      @RequestParam String repo) {
    log.info("Received status request for repo: {}", repo);
    return reportService.getStatusByRepo(repo)
        .map(ResponseEntity::ok);
  }

  /**
   * Returns the status of a specific run of the named pipeline.
   *
   * @param pipeline the pipeline name
   * @param runNo    the run number
   * @return HTTP 200 with the status response, or 404 if the pipeline or run is not found
   */
  @GetMapping("/{pipeline}/runs/{runNo}")
  public Mono<ResponseEntity<StatusResponse>> getStatusByRun(
      @PathVariable String pipeline,
      @PathVariable int runNo) {
    log.info("Received status request for pipeline: {}, run: {}", pipeline, runNo);
    return reportService.getStatusByRun(pipeline, runNo)
        .map(ResponseEntity::ok);
  }
}
