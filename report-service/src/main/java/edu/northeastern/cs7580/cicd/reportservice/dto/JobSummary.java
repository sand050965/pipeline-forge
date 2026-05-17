package edu.northeastern.cs7580.cicd.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a summary of a single job execution within a stage.
 *
 * <p>This object appears as an element in the {@code jobs} list of
 * {@link StageDetailResponse}, or as the {@code job} object in
 * {@link JobDetailResponse}. It contains the job name, status, and
 * start/end timestamps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSummary {

  private String name;

  private String status;

  @JsonProperty("failures")
  private boolean failures;

  private String start;

  private String end;
}
