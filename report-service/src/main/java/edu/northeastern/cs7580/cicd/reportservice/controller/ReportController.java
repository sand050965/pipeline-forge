package edu.northeastern.cs7580.cicd.reportservice.controller;

import edu.northeastern.cs7580.cicd.reportservice.dto.JobDetailResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.PipelineReportResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.RunDetailResponse;
import edu.northeastern.cs7580.cicd.reportservice.dto.StageDetailResponse;
import edu.northeastern.cs7580.cicd.reportservice.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller exposing endpoints for querying pipeline execution
 * reports from the CI/CD system's database.
 *
 * <p>This controller provides four GET endpoints corresponding to the
 * project specification's {@code cicd report} sub-command options:
 * <ul>
 *   <li>{@code GET /api/v1/report/pipelines/{pipeline}} — all runs for a pipeline</li>
 *   <li>{@code GET /api/v1/report/pipelines/{pipeline}/runs/{runNo}} — a specific run
 *       with its stages</li>
 *   <li>{@code GET /api/v1/report/pipelines/{pipeline}/runs/{runNo}/stages/{stage}}
 *       — a specific stage with its jobs</li>
 *   <li>{@code GET /api/v1/report/pipelines/{pipeline}/runs/{runNo}/stages/{stage}/jobs/{job}}
 *       — a specific job</li>
 * </ul>
 *
 * <p>All endpoints return JSON. The CLI is responsible for formatting
 * the response as YAML for display to the user. If a requested resource
 * is not found, the endpoint returns HTTP 404 with a descriptive error
 * message.
 *
 * <p>All endpoints are reactive and return {@link Mono} types for
 * non-blocking I/O operations when querying the database via R2DBC.
 */
@RestController
@RequestMapping("/api/v1/report")
public class ReportController {

  private static final Logger log =
      LoggerFactory.getLogger(ReportController.class);

  private final ReportService reportService;

  /**
   * Creates a new report controller with the required service dependency.
   *
   * @param reportService the report service for assembling report data
   */
  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  /**
   * Returns all past runs for the given pipeline.
   *
   * @param pipeline the pipeline name to query
   * @return a response containing the pipeline name and list of run summaries
   */
  @GetMapping("/pipelines/{pipeline}")
  public Mono<ResponseEntity<PipelineReportResponse>> getPipelineReport(
      @PathVariable String pipeline) {
    log.info("Received report request for pipeline: {}", pipeline);
    return reportService.getPipelineReport(pipeline)
        .map(ResponseEntity::ok);
  }

  /**
   * Returns the details of a specific run of a pipeline, including its stages.
   *
   * @param pipeline the pipeline name
   * @param runNo    the run number
   * @return a response containing the run details and its stages
   */
  @GetMapping("/pipelines/{pipeline}/runs/{runNo}")
  public Mono<ResponseEntity<RunDetailResponse>> getRunReport(
      @PathVariable String pipeline,
      @PathVariable int runNo) {
    log.info("Received report request for pipeline: {}, run: {}",
        pipeline, runNo);
    return reportService.getRunReport(pipeline, runNo)
        .map(ResponseEntity::ok);
  }

  /**
   * Returns the details of a specific stage within a pipeline run,
   * including its jobs.
   *
   * @param pipeline the pipeline name
   * @param runNo    the run number
   * @param stage    the stage name
   * @return a response containing the stage details and its jobs
   */
  @GetMapping("/pipelines/{pipeline}/runs/{runNo}/stages/{stage}")
  public Mono<ResponseEntity<StageDetailResponse>> getStageReport(
      @PathVariable String pipeline,
      @PathVariable int runNo,
      @PathVariable String stage) {
    log.info("Received report request for pipeline: {}, run: {}, stage: {}",
        pipeline, runNo, stage);
    return reportService.getStageReport(pipeline, runNo, stage)
        .map(ResponseEntity::ok);
  }

  /**
   * Returns the details of a specific job within a stage of a pipeline run.
   *
   * @param pipeline the pipeline name
   * @param runNo    the run number
   * @param stage    the stage name
   * @param job      the job name
   * @return a response containing the job details
   */
  @GetMapping("/pipelines/{pipeline}/runs/{runNo}/stages/{stage}/jobs/{job}")
  public Mono<ResponseEntity<JobDetailResponse>> getJobReport(
      @PathVariable String pipeline,
      @PathVariable int runNo,
      @PathVariable String stage,
      @PathVariable String job) {
    log.info(
        "Received report request for pipeline: {}, run: {}, stage: {}, job: {}",
        pipeline, runNo, stage, job);
    return reportService.getJobReport(pipeline, runNo, stage, job)
        .map(ResponseEntity::ok);
  }

  /**
   * Health check endpoint for the Report Service.
   *
   * @return simple health status message, HTTP 200
   */
  @GetMapping("/health")
  public Mono<ResponseEntity<String>> health() {
    return Mono.just(ResponseEntity.ok("Report Service is healthy"));
  }
}
