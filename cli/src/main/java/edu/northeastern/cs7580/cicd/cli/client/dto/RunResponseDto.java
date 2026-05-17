package edu.northeastern.cs7580.cicd.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response payload returned after starting a pipeline execution.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RunResponseDto {

  private String executionId;
  private String pipelineName;
  private Integer runNumber;
  private String status;
  private String message;

  /** Default constructor for deserialization. */
  public RunResponseDto() {}

  /** Returns the unique execution identifier. */
  public String getExecutionId() {
    return executionId;
  }

  /** Returns the pipeline name. */
  public String getPipelineName() {
    return pipelineName;
  }

  /** Returns the run number for this pipeline. */
  public Integer getRunNumber() {
    return runNumber;
  }

  /** Returns the execution status. */
  public String getStatus() {
    return status;
  }

  /** Returns the human-readable result message. */
  public String getMessage() {
    return message;
  }
}
