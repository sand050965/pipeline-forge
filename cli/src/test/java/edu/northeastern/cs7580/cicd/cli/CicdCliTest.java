package edu.northeastern.cs7580.cicd.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class CicdCliTest {

  @Test
  void commandLine_hasCorrectName() {
    CommandLine cmd = new CommandLine(new CicdCli());
    assertEquals("cicd", cmd.getCommandName());
  }

  @Test
  void commandLine_hasExpectedSubcommands() {
    CommandLine cmd = new CommandLine(new CicdCli());
    assertNotNull(cmd.getSubcommands().get("verify"));
    assertNotNull(cmd.getSubcommands().get("dryrun"));
    assertNotNull(cmd.getSubcommands().get("run"));
  }

  @Test
  void helpOption_returnsExitCode0() {
    int exitCode = new CommandLine(new CicdCli()).execute("--help");
    assertEquals(0, exitCode);
  }

  @Test
  void unknownCommand_returnsNonZeroExitCode() {
    int exitCode = new CommandLine(new CicdCli()).execute("invalid-command");
    assertEquals(2, exitCode);
  }
}