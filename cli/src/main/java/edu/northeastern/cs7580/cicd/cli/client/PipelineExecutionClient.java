package edu.northeastern.cs7580.cicd.cli.client;

import edu.northeastern.cs7580.cicd.cli.client.dto.RunRequestDto;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunResponseDto;

/**
 * Abstraction for starting pipeline executions.
 */
public interface PipelineExecutionClient {

  /**
   * Executes a pipeline remotely.
   *
   * @param request execution request
   * @return execution response
   * @throws Exception if execution fails
   */
  RunResponseDto executePipeline(RunRequestDto request) throws Exception;
}