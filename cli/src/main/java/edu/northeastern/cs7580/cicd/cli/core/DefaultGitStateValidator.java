package edu.northeastern.cs7580.cicd.cli.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Implements Git branch and commit validation for CLI pipeline execution.
 *
 * <p>This validator is responsible for verifying that the requested branch and
 * commit match the current local Git repository state. It prevents pipeline
 * execution when the CLI invocation parameters do not correspond to the code
 * version currently checked out in the working tree.
 *
 * <p>The {@code DefaultGitStateValidator} enforces the following rules:
 * <ul>
 *   <li>The repository root must contain a {@code .git/} directory.</li>
 *   <li>The current {@code HEAD} commit must be resolvable; otherwise the
 *       repository is treated as having no commits.</li>
 *   <li>If a requested branch is provided, the current branch must match it.
 *       Detached {@code HEAD} is rejected when a branch is required.</li>
 *   <li>If a requested commit is provided and is not {@code latest}, it must
 *       match the current {@code HEAD} commit SHA (full SHA or prefix).</li>
 * </ul>
 */
public final class DefaultGitStateValidator implements GitStateValidator {

  /**
   * Validates that the repository at {@code repoRoot} matches the requested branch
   * and commit.
   *
   * @param repoRoot repository root directory containing {@code .git/}
   * @param requestedBranch requested branch name; blank means skip validation
   * @param requestedCommit requested commit SHA or prefix; {@code latest} or blank means skip
   * @throws GitStateException if the repository state cannot be read or does not match
   */
  @Override
  public void validate(Path repoRoot, String requestedBranch, String requestedCommit) {
    Objects.requireNonNull(repoRoot, "repoRoot");

    File gitDir = repoRoot.resolve(".git").toFile();
    if (!gitDir.exists() || !gitDir.isDirectory()) {
      throw new GitStateException("Not a Git repository: missing .git directory at " + repoRoot);
    }

    try (Repository repository = openRepository(gitDir)) {
      ObjectId head = resolveHead(repository);
      String headSha = head.name();

      validateBranch(repository, headSha, requestedBranch);
      validateCommit(headSha, requestedCommit);
    } catch (IOException e) {
      throw new GitStateException("Failed to read Git repository state at " + repoRoot, e);
    }
  }

  /**
   * Opens a JGit repository from the provided {@code .git} directory.
   *
   * @param gitDir the {@code .git} directory
   * @return opened repository
   * @throws IOException if repository cannot be opened
   */
  private Repository openRepository(File gitDir) throws IOException {
    return new FileRepositoryBuilder()
        .setGitDir(gitDir)
        .readEnvironment()
        .build();
  }

  /**
   * Resolves the current {@code HEAD} commit.
   *
   * @param repository the git repository
   * @return resolved HEAD object id
   * @throws IOException if HEAD cannot be resolved
   * @throws GitStateException if the repository has no commits
   */
  private ObjectId resolveHead(Repository repository) throws IOException {
    ObjectId head = repository.resolve(Constants.HEAD);
    if (head == null) {
      throw new GitStateException("Repository has no commits (HEAD is undefined).");
    }
    return head;
  }

  /**
   * Validates the current branch against a requested branch.
   *
   * @param repository repository handle
   * @param headSha current HEAD SHA for error reporting
   * @param requestedBranch requested branch; blank means skip
   * @throws IOException if branch cannot be read
   * @throws GitStateException if branch mismatches or repository is detached
   */
  private void validateBranch(Repository repository, String headSha, String requestedBranch)
      throws IOException {
    if (requestedBranch == null || requestedBranch.isBlank()) {
      return;
    }

    String fullBranch = repository.getFullBranch();
    if (fullBranch == null || !fullBranch.startsWith(Constants.R_HEADS)) {
      throw new GitStateException(
          "Detached HEAD at " + headSha + "; expected branch '" + requestedBranch + "'.");
    }

    String currentBranch = fullBranch.substring(Constants.R_HEADS.length());
    if (!currentBranch.equals(requestedBranch)) {
      throw new GitStateException(
          "Branch mismatch: requested '" + requestedBranch + "' but current branch is '"
              + currentBranch + "'.");
    }
  }

  /**
   * Validates the current HEAD commit against a requested commit.
   *
   * @param headSha current HEAD SHA
   * @param requestedCommit requested commit SHA or prefix; {@code latest} or blank means skip
   * @throws GitStateException if commit mismatches
   */
  private void validateCommit(String headSha, String requestedCommit) {
    if (requestedCommit == null || requestedCommit.isBlank() || "latest".equals(requestedCommit)) {
      return;
    }

    if (!headSha.startsWith(requestedCommit)) {
      throw new GitStateException(
          "Commit mismatch: requested '" + requestedCommit + "' but current HEAD is '" + headSha
              + "'.");
    }
  }
}
