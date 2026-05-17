package edu.northeastern.cs7580.cicd.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level response DTO for
 * {@code GET /report/{pipeline}/{runNo}/stage/{stage}}, which returns
 * the details of a specific stage within a pipeline run, including its jobs.
 *
 * <p>The JSON structure mirrors the project specification's report output:
 * <pre>
 * {
 *   "pipeline": {
 *     "name": "default",
 *     "run-no": 1,
 *     "status": "success",
 *     "start": "...",
 *     "end": "...",
 *     "stage": {
 *       "name": "build",
 *       "status": "success",
 *       "start": "...",
 *       "end": "...",
 *       "jobs": [
 *         { "name": "compile", "status": "success", ... }
 *       ]
 *     }
 *   }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageDetailResponse {

  private PipelineStageData pipeline;

  /**
   * Inner object representing the pipeline run data with a single stage
   * and its jobs.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PipelineStageData {

    private String name;

    @JsonProperty("run-no")
    private Integer runNo;

    private String status;

    private String start;

    private String end;

    private StageWithJobs stage;
  }

  /**
   * Inner object representing a stage with its list of jobs.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StageWithJobs {

    private String name;

    private String status;

    private String start;

    private String end;

    private List<JobSummary> jobs;
  }
}
