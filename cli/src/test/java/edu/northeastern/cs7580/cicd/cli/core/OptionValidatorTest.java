package edu.northeastern.cs7580.cicd.cli.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class OptionValidatorTest {

  @Test
  void validateRunOptions_onlyName_ok() {
    CommandLine cmd = new CommandLine(new DummyCommand());
    assertDoesNotThrow(() -> OptionValidator.validateRunOptions("default", null, cmd));
  }

  @Test
  void validateRunOptions_onlyFile_ok() {
    CommandLine cmd = new CommandLine(new DummyCommand());
    assertDoesNotThrow(() -> OptionValidator.validateRunOptions(null,
        Path.of(".pipelines/a.yaml"), cmd));
  }

  @Test
  void validateRunOptions_bothProvided_throws() {
    CommandLine cmd = new CommandLine(new DummyCommand());
    assertThrows(
        CommandLine.ParameterException.class,
        () -> OptionValidator.validateRunOptions("default",
            Path.of(".pipelines/a.yaml"), cmd));
  }

  @Test
  void validateRunOptions_neitherProvided_throws() {
    CommandLine cmd = new CommandLine(new DummyCommand());
    assertThrows(
        CommandLine.ParameterException.class,
        () -> OptionValidator.validateRunOptions(null, null, cmd));
  }

  @CommandLine.Command(name = "dummy")
  private static final class DummyCommand {}
}
