package edu.northeastern.cs7580.cicd.executionservice.service;

import edu.northeastern.cs7580.cicd.executionservice.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.executionservice.exception.WorkspaceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

/**
 * Prepares a local workspace directory for pipeline execution.
 *
 * <p>This service is responsible for materializing a repository state on disk so
 * jobs can run against a stable, committed snapshot of code. It clones the Git
 * repository and checks out the requested branch and commit.
 *
 * <p>The {@code WorkspaceService} enforces the following rules:
 * <ul>
 *   <li>A unique temporary workspace directory is created for each execution.</li>
 *   <li>The repository is cloned from {@code repositoryUrl}.</li>
 *   <li>The workspace is checked out to {@code commitHash} when provided.</li>
 *   <li>Workspace cleanup is best-effort and recursive.</li>
 * </ul>
 *
 * <p>Prepared workspaces are intended to be bind-mounted into Docker containers
 * as {@code /workspace} for job execution.
 */
@Service
public class WorkspaceService {

  /**
   * Clones the repository and checks out the requested branch and (optionally) commit.
   *
   * @param gitMetadata Git repository metadata (repo URL, branch, commit)
   * @return the path to the prepared workspace directory
   * @throws WorkspaceException if clone or checkout fails
   */
  public Path prepareWorkspace(GitMetadata gitMetadata) {
    Objects.requireNonNull(gitMetadata, "gitMetadata");
    validateGitMetadata(gitMetadata);

    Path workspaceDir;
    try {
      workspaceDir = Files.createTempDirectory("cicd-workspace-");
    } catch (IOException e) {
      throw new WorkspaceException("Failed to create workspace directory", e);
    }

    try (Git git = buildCloneCommand(gitMetadata, workspaceDir).call()) {

      String commitHash = gitMetadata.getCommitHash();
      if (!isBlank(commitHash)) {
        git.checkout().setName(commitHash).call();
      }

      return workspaceDir;

    } catch (GitAPIException e) {
      cleanupWorkspace(workspaceDir);
      throw new WorkspaceException(
          "Failed to prepare workspace for repo=" + gitMetadata.getRepositoryUrl()
              + ", branch=" + gitMetadata.getBranch()
              + ", commit=" + gitMetadata.getCommitHash(),
          e);
    }
  }

  /**
   * Deletes the workspace directory recursively.
   *
   * @param workspaceDir the workspace directory, or null if not created
   */
  public void cleanupWorkspace(Path workspaceDir) {
    if (workspaceDir == null) {
      return;
    }
    try {
      Files.walk(workspaceDir)
          .sorted(Comparator.reverseOrder())
          .forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException ignored) {
              // Best-effort cleanup.
            }
          });
    } catch (IOException ignored) {
      // Best-effort cleanup.
    }
  }

  /**
   * Validates required Git metadata fields.
   *
   * @param gitMetadata Git metadata to validate
   */
  private void validateGitMetadata(GitMetadata gitMetadata) {
    if (isBlank(gitMetadata.getRepositoryUrl())) {
      throw new WorkspaceException("Repository URL is required");
    }
    if (isBlank(gitMetadata.getBranch())) {
      throw new WorkspaceException("Branch is required");
    }
    // commitHash is optional (branch-only execution is allowed).
  }

  /**
   * Returns true if the string is null or blank.
   *
   * @param value the value to check
   * @return true if blank
   */
  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private org.eclipse.jgit.api.CloneCommand buildCloneCommand(
      GitMetadata gitMetadata, Path workspaceDir) {
    String repositoryUri = resolveRepositoryUri(gitMetadata.getRepositoryUrl());
    org.eclipse.jgit.api.CloneCommand command = Git.cloneRepository()
        .setURI(repositoryUri)
        .setDirectory(workspaceDir.toFile())
        .setBranch(gitMetadata.getBranch());

    return command;
  }

  private String resolveRepositoryUri(String repositoryUrl) {
    if (isBlank(repositoryUrl)) {
      return repositoryUrl;
    }
    if (repositoryUrl.startsWith("http://")
        || repositoryUrl.startsWith("https://")
        || repositoryUrl.startsWith("ssh://")
        || repositoryUrl.startsWith("git@")
        || repositoryUrl.startsWith("file:")) {
      return repositoryUrl;
    }
    try {
      Path candidate = Path.of(repositoryUrl);
      if (Files.isDirectory(candidate)) {
        return candidate.toUri().toString();
      }
    } catch (Exception ignored) {
      // Fall through to original value.
    }
    return repositoryUrl;
  }
}
