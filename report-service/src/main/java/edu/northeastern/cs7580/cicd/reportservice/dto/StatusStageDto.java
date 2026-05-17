package edu.northeastern.cs7580.cicd.reportservice.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the execution status of a single stage, including all its jobs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusStageDto {

  private String name;

  private String status;

  private List<StatusJobDto> jobs;
}
