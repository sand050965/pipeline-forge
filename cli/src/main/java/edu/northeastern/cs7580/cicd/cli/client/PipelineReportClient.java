package edu.northeastern.cs7580.cicd.cli.client;

import java.util.Map;

/**
 * Client interface for retrieving pipeline execution reports from the API Gateway.
 *
 * <p>Each method corresponds to one of the four {@code cicd report} option combinations.
 * Responses are returned as raw maps so that SnakeYAML can serialize them directly
 * without null-field pollution from typed DTOs.
 *
 * @see ApiGatewayReportClient
 */
public interface PipelineReportClient {

  /**
   * Returns all runs for a pipeline.
   *
   * @param pipeline pipeline name
   * @return report map matching the spec's pipeline-level YAML shape
   * @throws Exception if the request fails
   */
  Map<String, Object> getPipelineReport(String pipeline) throws Exception;

  /**
   * Returns details for a specific run of a pipeline.
   *
   * @param pipeline pipeline name
   * @param run      run number
   * @return report map matching the spec's run-level YAML shape
   * @throws Exception if the request fails
   */
  Map<String, Object> getRunReport(String pipeline, int run) throws Exception;

  /**
   * Returns details for a specific stage within a pipeline run.
   *
   * @param pipeline pipeline name
   * @param run      run number
   * @param stage    stage name
   * @return report map matching the spec's stage-level YAML shape
   * @throws Exception if the request fails
   */
  Map<String, Object> getStageReport(String pipeline, int run, String stage) throws Exception;

  /**
   * Returns details for a specific job within a stage of a pipeline run.
   *
   * @param pipeline pipeline name
   * @param run      run number
   * @param stage    stage name
   * @param job      job name
   * @return report map matching the spec's job-level YAML shape
   * @throws Exception if the request fails
   */
  Map<String, Object> getJobReport(String pipeline, int run, String stage, String job)
      throws Exception;
}
