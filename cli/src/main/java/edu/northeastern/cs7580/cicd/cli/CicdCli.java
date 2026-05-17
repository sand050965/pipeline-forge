package edu.northeastern.cs7580.cicd.cli;

import edu.northeastern.cs7580.cicd.cli.command.DryrunCommand;
import edu.northeastern.cs7580.cicd.cli.command.ReportCommand;
import edu.northeastern.cs7580.cicd.cli.command.RunCommand;
import edu.northeastern.cs7580.cicd.cli.command.StatusCommand;
import edu.northeastern.cs7580.cicd.cli.command.VerifyCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Defines the root entry point for the Custom CI/CD command line interface.
 *
 * <p>This class declares the top-level {@code cicd} command and registers all
 * supported subcommands using Picocli. It serves as the central routing
 * component that delegates execution to the appropriate subcommand based on
 * user input.
 *
 * <p>The {@code CicdCli} supports the following subcommands:
 * <ul>
 *   <li>{@code verify} - Validate pipeline configuration (local operation)</li>
 *   <li>{@code dryrun} - Generate execution plan preview (local operation)</li>
 *   <li>{@code run} - Execute pipeline remotely (calls API Gateway)</li>
 *   <li>{@code status} - Check execution status (calls API Gateway)</li>
 *   <li>{@code report} - Query execution reports (calls API Gateway)</li>
 * </ul>
 *
 * <p>Local operations ({@code verify}, {@code dryrun}) use the pipeline-library
 * directly and work offline. Remote operations ({@code run}, {@code status},
 * {@code report}) communicate with the API Gateway via HTTP.
 *
 * <p>The process exit code returned by the executed subcommand is propagated
 * directly to the operating system to allow external tools and CI systems to
 * reliably detect success or failure.
 *
 * @implNote This class is intentionally minimal and acts purely as a
 *     composition and bootstrap point for the CLI. All functional behavior
 *     is implemented in subcommand classes.
 */
@Command(
    name = "cicd",
    description = "Custom CI/CD command line tool",
    mixinStandardHelpOptions = true,
    version = "cicd 0.1.0-beta",
    subcommands = {
        VerifyCommand.class,
        DryrunCommand.class,
        RunCommand.class,
        StatusCommand.class,
        ReportCommand.class
    }
)
public class CicdCli {

  /**
   * Creates a new CLI entry point.
   */
  public CicdCli() {
    // Default constructor.
  }

  /**
   * Application entry point.
   *
   * <p>Delegates command-line argument parsing and execution to Picocli and
   * exits with the returned status code so that CI/CD systems can correctly
   * determine success or failure.
   *
   * @param args command-line arguments passed to the CLI
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new CicdCli()).execute(args);
    System.exit(exitCode);
  }
}