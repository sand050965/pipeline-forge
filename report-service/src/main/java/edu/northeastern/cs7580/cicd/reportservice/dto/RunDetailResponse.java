package edu.northeastern.cs7580.cicd.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level response DTO for {@code GET /report/{pipeline}/{runNo}}, which
 * returns the details of a specific pipeline run including its stages.
 *
 * <p>The JSON structure mirrors the project specification's report output:
 * <pre>
 * {
 *   "pipeline": {
 *     "name": "default",
 *     "run-no": 1,
 *     "status": "success",
 *     "start": "2025-08-29T16:17:52-07:00",
 *     "end": "2025-08-29T16:24:32-07:00",
 *     "stages": [
 *       { "name": "build", "status": "success", ... }
 *     ]
 *   }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunDetailResponse {

  private PipelineRunData pipeline;

  /**
   * Inner object representing the pipeline run data including its stages.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PipelineRunData {

    private String name;

    @JsonProperty("run-no")
    private Integer runNo;

    private String status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("trace-id")
    private String traceId;

    private String start;

    private String end;

    private List<StageSummary> stages;
  }
}
