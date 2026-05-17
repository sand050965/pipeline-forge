package edu.northeastern.cs7580.cicd.apigateway.controller;

import edu.northeastern.cs7580.cicd.apigateway.service.ReportServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller that routes report queries from the CLI to the Report Service.
 *
 * <p>All endpoints proxy requests to the Report Service without transformation,
 * preserving the original HTTP status codes and response bodies. Responses are
 * returned as raw JSON strings with {@code Content-Type: application/json}.
 *
 * <p><b>Supported report levels:</b>
 * <ul>
 *   <li><b>Pipeline</b> — all runs for a given pipeline</li>
 *   <li><b>Run</b> — details of a specific run within a pipeline</li>
 *   <li><b>Stage</b> — details of a specific stage within a run</li>
 *   <li><b>Job</b> — details of a specific job within a stage</li>
 * </ul>
 *
 * @see ReportServiceClient
 */
@RestController
@RequestMapping("/api/v1/pipelines/report")
public class ReportController {

  private static final Logger log = LoggerFactory.getLogger(ReportController.class);

  private final ReportServiceClient reportServiceClient;

  /**
   * Constructs a {@code ReportController} with the required service client.
   *
   * @param reportServiceClient reactive client for Report Service communication
   */
  public ReportController(ReportServiceClient reportServiceClient) {
    this.reportServiceClient = reportServiceClient;
  }

  /**
   * Retrieves all runs for the specified pipeline.
   *
   * <p>Returns a list of pipeline run summaries, including run numbers, statuses,
   * Git metadata, and timestamps, ordered by run number.
   *
   * @param pipeline the logical pipeline name as declared in the YAML configuration
   *                 (e.g. {@code "default"}, {@code "release-prod"})
   * @return a {@link Mono} emitting HTTP 200 with a JSON array of pipeline run
   *         summaries, or the Report Service error response if the pipeline is not found
   */
  @GetMapping("/pipelines/{pipeline}")
  public Mono<ResponseEntity<String>> getPipelineReport(
      @PathVariable String pipeline) {
    log.info("Routing report request for pipeline: {}", pipeline);
    return reportServiceClient.getPipelineReport(pipeline)
        .map(body -> ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(body));
  }

  /**
   * Retrieves details of a specific run within a pipeline.
   *
   * <p>Returns the run summary along with stage-level breakdowns, including
   * per-stage status and timing information.
   *
   * @param pipeline the logical pipeline name
   * @param runNo    the sequential run number scoped to the pipeline (e.g. {@code 1}, {@code 42})
   * @return a {@link Mono} emitting HTTP 200 with a JSON object describing the run,
   *         or the Report Service error response if the pipeline or run is not found
   */
  @GetMapping("/pipelines/{pipeline}/runs/{runNo}")
  public Mono<ResponseEntity<String>> getRunReport(
      @PathVariable String pipeline,
      @PathVariable int runNo) {
    log.info("Routing report request for pipeline: {}, run: {}", pipeline, runNo);
    return reportServiceClient.getRunReport(pipeline, runNo)
        .map(body -> ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(body));
  }

  /**
   * Retrieves details of a specific stage within a pipeline run.
   *
   * <p>Returns the stage summary along with job-level breakdowns, including
   * per-job status, exit codes, and timing information.
   *
   * @param pipeline the logical pipeline name
   * @param runNo    the sequential run number scoped to the pipeline
   * @param stage    the stage name as declared in the YAML configuration
   *                 (e.g. {@code "build"}, {@code "test"})
   * @return a {@link Mono} emitting HTTP 200 with a JSON object describing the stage,
   *         or the Report Service error response if the pipeline, run, or stage is not found
   */
  @GetMapping("/pipelines/{pipeline}/runs/{runNo}/stages/{stage}")
  public Mono<ResponseEntity<String>> getStageReport(
      @PathVariable String pipeline,
      @PathVariable int runNo,
      @PathVariable String stage) {
    log.info("Routing report request for pipeline: {}, run: {}, stage: {}",
        pipeline, runNo, stage);
    return reportServiceClient.getStageReport(pipeline, runNo, stage)
        .map(body -> ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(body));
  }

  /**
   * Retrieves details of a specific job within a stage of a pipeline run.
   *
   * <p>Returns full job execution details including status, exit code, combined
   * stdout/stderr output, and wall-clock execution duration.
   *
   * @param pipeline the logical pipeline name
   * @param runNo    the sequential run number scoped to the pipeline
   * @param stage    the stage name as declared in the YAML configuration
   * @param job      the job name as declared in the YAML configuration
   *                 (e.g. {@code "compile"}, {@code "unit-tests"})
   * @return a {@link Mono} emitting HTTP 200 with a JSON object describing the job,
   *         or the Report Service error response if the pipeline, run, stage, or job
   *         is not found
   */
  @GetMapping("/pipelines/{pipeline}/runs/{runNo}/stages/{stage}/jobs/{job}")
  public Mono<ResponseEntity<String>> getJobReport(
      @PathVariable String pipeline,
      @PathVariable int runNo,
      @PathVariable String stage,
      @PathVariable String job) {
    log.info("Routing report request for pipeline: {}, run: {}, stage: {}, job: {}",
        pipeline, runNo, stage, job);
    return reportServiceClient.getJobReport(pipeline, runNo, stage, job)
        .map(body -> ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(body));
  }
}