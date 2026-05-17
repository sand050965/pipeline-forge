package edu.northeastern.cs7580.cicd.cli.core;

/**
 * Resolves the default CI/CD pipeline configuration path when no explicit
 * path is provided by the user.
 *
 * <p>This class encapsulates the policy for determining which pipeline
 * configuration file should be verified when the {@code verify} CLI
 * subcommand is invoked without a path argument.
 *
 * <p>The {@code DefaultPathResolver} enforces the following behavior:
 * <ul>
 *   <li>If the user does not provide a path, or provides a blank value,
 *       a predefined default path is returned.</li>
 *   <li>If the user provides a non-blank path, the value is returned
 *       unchanged aside from trimming leading and trailing whitespace.</li>
 * </ul>
 *
 * <p>The resolved path is always repo-relative and does not perform any
 * filesystem access or validation. Path correctness and policy enforcement
 * are handled by higher-level components.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to methods in
 * this class is supported and treated as an omitted path.
 *
 * @implNote This class is stateless and deterministic. It exists to centralize
 *     default-path policy so that CLI behavior remains consistent and
 *     testable as defaults evolve.
 */
public class DefaultPathResolver {

  private static final String DEFAULT_PATH = ".pipelines/pipeline.yaml";

  /**
   * Creates a new default path resolver.
   */
  public DefaultPathResolver() {
    // Default constructor.
  }

  /**
   * Resolves the path to verify.
   *
   * @param userProvidedPath repo-relative path provided by the user; may be null or blank
   * @return repo-relative path to verify
   */
  public String resolve(String userProvidedPath) {
    if (userProvidedPath == null || userProvidedPath.trim().isEmpty()) {
      return DEFAULT_PATH;
    }
    return userProvidedPath.trim();
  }
}
