package edu.northeastern.cs7580.cicd.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level DTO for a pipeline status response, containing the full
 * stage and job hierarchy for a single pipeline run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusResponse {

  @JsonProperty("pipeline-name")
  private String pipelineName;

  @JsonProperty("run-no")
  private int runNo;

  private String status;

  private List<StatusStageDto> stages;
}
