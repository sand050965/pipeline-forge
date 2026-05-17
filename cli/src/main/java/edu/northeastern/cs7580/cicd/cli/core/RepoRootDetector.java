package edu.northeastern.cs7580.cicd.cli.core;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects whether a given directory represents the root of a Git repository.
 *
 * <p>This class encapsulates the logic used by the CLI to determine whether it
 * is being executed from the correct location. A directory is considered a Git
 * repository root if it contains a {@code .git/} directory.
 *
 * <p>The {@code RepoRootDetector} performs a lightweight filesystem check and
 * does not invoke Git commands or depend on any Git tooling being installed.
 * It is intended solely as a structural validation step prior to executing
 * repository-scoped CLI operations.
 *
 * <p>This check is typically performed before any configuration file parsing
 * or path resolution to ensure that subsequent operations are evaluated
 * relative to a valid repository root.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument will result in
 * a {@code false} return value rather than an exception.
 *
 * @implNote This class is stateless and side-effect free. It exists to isolate
 *     repository-root detection logic so that CLI commands remain simple and
 *     the behavior can be unit-tested independently of command execution.
 */
public class RepoRootDetector {

  /**
   * Creates a new repository root detector.
   */
  public RepoRootDetector() {
    // Default constructor.
  }

  /**
   * Traverses upward from the given path to find the Git repository root.
   *
   * @param startPath the path to start searching from
   * @return the repository root path, or null if no repository is found
   */
  public Path findRepoRoot(Path startPath) {
    Path current = startPath;
    while (current != null) {
      if (isRepoRoot(current)) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  /**
   * Returns true if the given directory contains a {@code .git/} folder.
   *
   * @param path the path to check
   * @return true if path appears to be a Git repository root
   */
  public boolean isRepoRoot(Path path) {
    if (path == null) {
      return false;
    }
    return Files.isDirectory(path.resolve(".git"));
  }
}