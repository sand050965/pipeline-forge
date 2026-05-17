package edu.northeastern.cs7580.cicd.cli.core;

import java.nio.file.Path;
import picocli.CommandLine;

/**
 * Provides reusable CLI option validation utilities.
 *
 * <p>This class centralizes validation rules shared across CLI subcommands,
 * such as mutually exclusive option constraints.
 */
public final class OptionValidator {

  private OptionValidator() {}

  /**
   * Validates options for the {@code run} subcommand.
   *
   * <p>Exactly one of {@code --name} or {@code --file} must be provided.
   *
   * @param name pipeline name option value (may be null)
   * @param file pipeline file option value (may be null)
   * @param commandLine command line context for error reporting
   * @throws CommandLine.ParameterException if validation fails
   */
  public static void validateRunOptions(String name, Path file, CommandLine commandLine) {
    if (commandLine == null) {
      throw new IllegalArgumentException("commandLine must not be null");
    }
    boolean hasName = name != null && !name.isBlank();
    boolean hasFile = file != null;

    if (hasName && hasFile) {
      throw new CommandLine.ParameterException(
          commandLine, "Invalid options: --name and --file are mutually exclusive.");
    }
    if (!hasName && !hasFile) {
      throw new CommandLine.ParameterException(
          commandLine, "Missing required option: specify either --name or --file.");
    }
  }
}
