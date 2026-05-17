package edu.northeastern.cs7580.cicd.cli.client;

import java.util.Map;

/**
 * Abstraction for querying live pipeline execution status from the API Gateway.
 *
 * <p>Implementations communicate with the {@code /api/v1/status} endpoints
 * and return the response as a {@code Map<String, Object>} for YAML rendering.
 */
public interface PipelineStatusClient {

  /**
   * Returns the status of the active or most recently completed run for the given repo URL.
   *
   * @param repoUrl the repository URL to query
   * @return the status response as a map
   * @throws edu.northeastern.cs7580.cicd.cli.exception.ReportNotFoundException if not found
   * @throws Exception if a network or deserialization error occurs
   */
  Map<String, Object> getStatusByRepo(String repoUrl) throws Exception;

  /**
   * Returns the status of a specific run of the named pipeline.
   *
   * @param pipeline the pipeline name
   * @param runNo    the run number
   * @return the status response as a map
   * @throws edu.northeastern.cs7580.cicd.cli.exception.ReportNotFoundException if not found
   * @throws Exception if a network or deserialization error occurs
   */
  Map<String, Object> getStatusByRun(String pipeline, int runNo) throws Exception;
}
