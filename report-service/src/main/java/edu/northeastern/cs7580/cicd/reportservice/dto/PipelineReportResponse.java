package edu.northeastern.cs7580.cicd.reportservice.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level response DTO for {@code GET /report/{pipeline}}, which returns
 * all past runs for a named pipeline.
 *
 * <p>The JSON structure mirrors the project specification's report output:
 * <pre>
 * {
 *   "pipeline": {
 *     "name": "default",
 *     "runs": [
 *       { "run-no": 1, "status": "success", ... },
 *       { "run-no": 2, "status": "failed", ... }
 *     ]
 *   }
 * }
 * </pre>
 *
 * <p>The outer wrapper contains a single {@code pipeline} field whose
 * value is a {@link PipelineData} object holding the pipeline name and
 * list of {@link RunSummary} entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineReportResponse {

  private PipelineData pipeline;

  /**
   * Inner object representing the pipeline data that contains the name
   * and runs list.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PipelineData {

    private String name;

    private List<RunSummary> runs;
  }
}
