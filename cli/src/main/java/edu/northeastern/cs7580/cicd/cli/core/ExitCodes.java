package edu.northeastern.cs7580.cicd.cli.core;

/**
 * Defines the process exit codes returned by CI/CD CLI commands.
 *
 * <p>Exit codes provide a stable, machine-readable contract between the CLI
 * and calling environments such as shell scripts, CI systems, and automation
 * tools. Each exit code represents a distinct category of success or failure
 * that can be interpreted without parsing human-readable output.
 *
 * <p>The {@code ExitCodes} class centralizes all CLI exit status values to
 * ensure consistency across subcommands and to avoid duplication of
 * numeric literals throughout the codebase.
 *
 * <p>The defined exit codes follow these principles:
 * <ul>
 *   <li>{@code 0} indicates successful execution.</li>
 *   <li>Non-zero values indicate failure conditions.</li>
 *   <li>Each failure code maps to a single, well-defined error category.</li>
 * </ul>
 *
 * <p>This class is not instantiable and is intended to be used only as a
 * namespace for constants.
 *
 * @implNote Exit code values are part of the CLI’s external contract and
 *     should remain stable once published. New error categories should be
 *     introduced using new codes rather than reusing or changing existing
 *     values.
 */
public final class ExitCodes {

  /**
   * Success.
   */
  public static final int OK = 0;

  /**
   * Configuration validation error.
   */
  public static final int CONFIG_VALIDATION_ERROR = 1;

  /**
   * Runtime execution error.
   */
  public static final int RUNTIME_EXECUTION_ERROR = 2;

  /**
   * Git repository error.
   */
  public static final int GIT_REPOSITORY_ERROR = 3;

  /**
   * Docker error.
   */
  public static final int DOCKER_ERROR = 4;

  /**
   * Database error.
   */
  public static final int DATABASE_ERROR = 5;

  /**
   * Invalid CLI arguments.
   */
  public static final int INVALID_CLI_ARGUMENTS = 10;

  private ExitCodes() {
    // Prevent instantiation.
  }
}
