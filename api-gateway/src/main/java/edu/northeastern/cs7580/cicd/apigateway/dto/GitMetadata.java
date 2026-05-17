package edu.northeastern.cs7580.cicd.apigateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO carrying Git repository metadata required to locate and clone the
 * correct source snapshot for pipeline execution.
 *
 * <p>An instance of this class is embedded in every
 * {@link PipelineExecutionRequest} and forwarded to the Execution Service,
 * which uses it to clone the repository and check out the specified
 * branch and commit before running jobs.
 *
 * <p>All fields are mandatory — the Execution Service requires a fully
 * qualified source snapshot (repository URL + branch + commit) to ensure
 * reproducible pipeline runs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitMetadata {

  /**
   * URL of the Git repository to clone.
   *
   * <p>Both HTTPS and SSH formats are accepted:
   * <ul>
   *   <li>{@code https://github.com/user/repo.git}</li>
   *   <li>{@code git@github.com:user/repo.git}</li>
   * </ul>
   *
   * <p>Must be non-blank. The Execution Service will attempt to clone
   * from this URL; an unreachable or invalid URL will cause workspace
   * preparation to fail.
   */
  @NotBlank(message = "Repository URL is required")
  private String repositoryUrl;

  /**
   * Name of the Git branch to check out after cloning.
   *
   * <p>Example values: {@code "main"}, {@code "develop"},
   * {@code "feature/new-ui"}
   *
   * <p>Must be non-blank and must exist in the remote repository.
   */
  @NotBlank(message = "Branch name is required")
  private String branch;

  /**
   * Full 40-character SHA-1 commit hash identifying the exact source
   * snapshot to execute against.
   *
   * <p>Example: {@code "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"}
   *
   * <p>Must be non-blank. The CLI validates that this hash matches the
   * current HEAD before forwarding the request, ensuring the pipeline
   * runs against the code the developer has checked out locally.
   */
  @NotBlank(message = "Commit hash is required")
  private String commitHash;

}
