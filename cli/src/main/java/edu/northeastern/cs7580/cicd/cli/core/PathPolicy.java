package edu.northeastern.cs7580.cicd.cli.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Defines and enforces path validation rules for CI/CD pipeline configuration files.
 *
 * <p>This class centralizes all filesystem-related policy checks applied to
 * user-provided paths in the CLI. It ensures that configuration file paths are
 * safe, repository-scoped, and compliant with the required project layout.
 *
 * <p>The {@code PathPolicy} enforces the following constraints:
 * <ul>
 *   <li>User-provided paths must be relative; absolute paths are rejected.</li>
 *   <li>Resolved paths must not escape the Git repository root (no path traversal).</li>
 *   <li>Configuration files must reside under the {@code .pipelines/} directory
 *       at the repository root.</li>
 *   <li>The resolved path must exist and refer to a regular file.</li>
 * </ul>
 *
 * <p>This class performs no CLI argument parsing and does not interact with
 * command execution logic. It is solely responsible for validating and
 * enforcing filesystem path rules.
 *
 * <p>All validation failures are reported via {@link IllegalArgumentException}
 * with human-readable messages suitable for direct CLI error output.
 *
 * <p>Unless otherwise noted, passing {@code null} arguments to methods in this
 * class will result in a {@link NullPointerException}.
 *
 * @implNote This class is stateless and deterministic. It is designed to be
 *     reused across commands and easily unit-tested in isolation from the
 *     filesystem-dependent CLI flow.
 */
public class PathPolicy {

  /**
   * Creates a new path policy.
   */
  public PathPolicy() {
    // Default constructor.
  }

  /**
   * Parses the user input as a path and rejects absolute paths.
   *
   * @param userInput repo-relative path provided by the user
   * @return parsed {@link Path} for further validation
   * @throws IllegalArgumentException if the input is an absolute path
   */
  public Path parseAndRejectAbsolute(String userInput) {
    Path inputPath = Paths.get(userInput);
    if (inputPath.isAbsolute()) {
      throw new IllegalArgumentException("absolute paths are not allowed: " + userInput);
    }
    return inputPath;
  }

  /**
   * Resolves the input path against the repository root and rejects path traversal.
   *
   * <p>This prevents escaping the repository root via {@code ..} segments.
   *
   * @param repoRoot absolute path to the repository root directory
   * @param userPath parsed user input path (must be relative)
   * @return normalized absolute path to the target file
   * @throws IllegalArgumentException if the resolved path escapes the repository root
   */
  public Path resolveUnderRepoRoot(Path repoRoot, Path userPath) {
    Path normalizedRepoRoot = repoRoot.toAbsolutePath().normalize();
    Path resolved = normalizedRepoRoot.resolve(userPath).normalize();

    if (!resolved.startsWith(normalizedRepoRoot)) {
      throw new IllegalArgumentException("path traversal is not allowed: " + userPath);
    }
    return resolved;
  }

  /**
   * Ensures that the resolved path is located under the {@code .pipelines/} directory.
   *
   * @param repoRoot     absolute path to the repository root directory
   * @param resolvedPath normalized absolute path to the target file
   * @throws IllegalArgumentException if the path is not under {@code .pipelines/}
   */
  public void enforceUnderPipelines(Path repoRoot, Path resolvedPath) {
    Path pipelinesRoot = repoRoot.resolve(".pipelines").toAbsolutePath().normalize();
    if (!resolvedPath.startsWith(pipelinesRoot)) {
      throw new IllegalArgumentException(
          "configuration files must be located under .pipelines/: " + resolvedPath);
    }
  }

  /**
   * Ensures that the resolved path exists and is a regular file.
   *
   * @param resolvedPath normalized absolute path to the target file
   * @throws IllegalArgumentException if the path does not exist or is not a regular file
   */
  public void enforceExistingRegularFile(Path resolvedPath) {
    if (!Files.exists(resolvedPath)) {
      throw new IllegalArgumentException("configuration file not found: " + resolvedPath);
    }
    if (!Files.isRegularFile(resolvedPath)) {
      throw new IllegalArgumentException("configuration path is not a file: " + resolvedPath);
    }
  }
}

