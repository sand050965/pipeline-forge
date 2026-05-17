package edu.northeastern.cs7580.cicd.reportservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a summary of a single stage execution within a
 * pipeline run.
 *
 * <p>This object appears as an element in the {@code stages} list of
 * {@link RunDetailResponse}, or as the {@code stage} object in
 * {@link StageDetailResponse} and {@link JobDetailResponse}. It contains
 * the stage name, status, and start/end timestamps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageSummary {

  private String name;

  private String status;

  private String start;

  private String end;
}
