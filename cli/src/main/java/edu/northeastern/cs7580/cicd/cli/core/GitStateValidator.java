package edu.northeastern.cs7580.cicd.cli.core;

import java.nio.file.Path;

/**
 * Defines Git repository state validation behavior for CLI commands.
 *
 * <p>This interface abstracts branch and commit validation logic so that
 * implementations can verify whether the current repository state matches
 * requested execution parameters.
 *
 * <p>Implementations must throw {@link GitStateException} when validation fails.
 */
public interface GitStateValidator {

  /**
   * Validates that the repository at {@code repoRoot} matches the requested
   * branch and commit.
   *
   * @param repoRoot repository root directory containing {@code .git}
   * @param requestedBranch requested branch name
   * @param requestedCommit requested commit SHA or {@code latest}
   * @throws GitStateException if validation fails
   */
  void validate(Path repoRoot, String requestedBranch, String requestedCommit);
}
