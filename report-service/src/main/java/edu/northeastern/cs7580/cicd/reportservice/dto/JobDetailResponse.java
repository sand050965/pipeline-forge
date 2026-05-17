package edu.northeastern.cs7580.cicd.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level response DTO for
 * {@code GET /report/{pipeline}/{runNo}/stage/{stage}/job/{job}}, which
 * returns the details of a specific job within a stage of a pipeline run.
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
 *       "job": {
 *         "name": "compile",
 *         "status": "success",
 *         "start": "...",
 *         "end": "..."
 *       }
 *     }
 *   }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailResponse {

  private PipelineJobData pipeline;

  /**
   * Inner object representing the pipeline run data with a single stage
   * containing a single job.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PipelineJobData {

    private String name;

    @JsonProperty("run-no")
    private Integer runNo;

    private String status;

    private String start;

    private String end;

    private StageWithJob stage;
  }

  /**
   * Inner object representing a stage with a single job detail.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StageWithJob {

    private String name;

    private String status;

    private String start;

    private String end;

    private JobSummary job;
  }
}
