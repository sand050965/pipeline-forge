package edu.northeastern.cs7580.cicd.reportservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the execution status of a single job within a status response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusJobDto {

  private String name;

  private String status;
}
