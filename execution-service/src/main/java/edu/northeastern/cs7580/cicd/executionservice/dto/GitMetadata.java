package edu.northeastern.cs7580.cicd.executionservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Git repository metadata for pipeline execution tracking.
 *
 * <p>This class encapsulates information about the Git repository state
 * at the time of pipeline execution, enabling traceability between
 * execution results and source code versions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitMetadata {

  /**
   * Git repository URL.
   *
   * <p>Example: {@code "https://github.com/user/repo.git"}
   */
  @NotBlank(message = "Repository URL is required")
  private String repositoryUrl;

  /**
   * Git branch name.
   *
   * <p>Example: {@code "main"}, {@code "feature-new-ui"}
   */
  @NotBlank(message = "Branch name is required")
  private String branch;

  /**
   * Git commit SHA hash.
   *
   * <p>Example: {@code "abc123def456"}
   */
  @NotBlank(message = "Commit hash is required")
  private String commitHash;

}
