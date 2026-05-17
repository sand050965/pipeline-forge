package edu.northeastern.cs7580.cicd.reportservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a summary of a single pipeline run, used when listing
 * all runs for a pipeline.
 *
 * <p>This object appears as an element in the {@code runs} list of
 * {@link PipelineReportResponse}. It contains the run number, status,
 * Git metadata, and start/end timestamps.
 *
 * <p>JSON field names use hyphens (e.g. {@code run-no}, {@code git-repo})
 * to match the project specification's report output format. Jackson
 * {@code @JsonProperty} annotations handle the mapping from camelCase
 * Java fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunSummary {

  @JsonProperty("run-no")
  private Integer runNo;

  private String status;

  @JsonProperty("git-repo")
  private String gitRepo;

  @JsonProperty("git-branch")
  private String gitBranch;

  @JsonProperty("git-hash")
  private String gitHash;

  private String start;

  private String end;
}
