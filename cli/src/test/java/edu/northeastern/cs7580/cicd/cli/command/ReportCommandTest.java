package edu.northeastern.cs7580.cicd.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.cs7580.cicd.cli.client.PipelineReportClient;
import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.exception.ReportNotFoundException;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Unit tests for {@link ReportCommand}.
 *
 * <p>Verifies option parsing, inter-option dependency validation, correct
 * client method dispatch, YAML output formatting, and exit code handling.
 * A {@link FakeReportClient} is injected to avoid real HTTP calls.
 */
class ReportCommandTest {

  // ── Constructor tests ────────────────────────────────────────────────────────

  @Test
  void defaultConstructor_shouldCreateInstance() {
    assertNotNull(new ReportCommand());
  }

  // ── Option parsing ───────────────────────────────────────────────────────────

  @Test
  void parse_pipelineOnly_setsFields() throws Exception {
    ReportCommand cmd = new ReportCommand(new FakeReportClient());
    new CommandLine(cmd).parseArgs("--pipeline", "default");

    assertEquals("default", getField(cmd, "pipeline", String.class));
    assertEquals(null, getField(cmd, "run", Integer.class));
    assertEquals(null, getField(cmd, "stage", String.class));
    assertEquals(null, getField(cmd, "job", String.class));
  }

  @Test
  void parse_pipelineAndRun_setsFields() throws Exception {
    ReportCommand cmd = new ReportCommand(new FakeReportClient());
    new CommandLine(cmd).parseArgs("--pipeline", "default", "--run", "2");

    assertEquals("default", getField(cmd, "pipeline", String.class));
    assertEquals(2, getField(cmd, "run", Integer.class));
    assertEquals(null, getField(cmd, "stage", String.class));
  }

  @Test
  void parse_pipelineRunAndStage_setsFields() throws Exception {
    ReportCommand cmd = new ReportCommand(new FakeReportClient());
    new CommandLine(cmd).parseArgs("--pipeline", "default", "--run", "1", "--stage", "build");

    assertEquals("default", getField(cmd, "pipeline", String.class));
    assertEquals(1, getField(cmd, "run", Integer.class));
    assertEquals("build", getField(cmd, "stage", String.class));
    assertEquals(null, getField(cmd, "job", String.class));
  }

  @Test
  void parse_allOptions_setsFields() throws Exception {
    ReportCommand cmd = new ReportCommand(new FakeReportClient());
    new CommandLine(cmd).parseArgs(
        "--pipeline", "default", "--run", "1", "--stage", "build", "--job", "compile");

    assertEquals("default", getField(cmd, "pipeline", String.class));
    assertEquals(1, getField(cmd, "run", Integer.class));
    assertEquals("build", getField(cmd, "stage", String.class));
    assertEquals("compile", getField(cmd, "job", String.class));
  }

  @Test
  void parse_missingPipeline_picocliRejectsIt() {
    ReportCommand cmd = new ReportCommand(new FakeReportClient());
    int exitCode = new CommandLine(cmd).execute("--run", "1");
    // Picocli returns 2 for missing required options, before our command code runs
    assertEquals(2, exitCode);
  }

  // ── Option dependency validation ─────────────────────────────────────────────

  @Test
  void call_stageWithoutRun_returnsInvalidCliArguments() {
    ReportCommand cmd = new ReportCommand(new FakeReportClient());
    int exitCode = new CommandLine(cmd).execute("--pipeline", "default", "--stage", "build");
    assertEquals(ExitCodes.INVALID_CLI_ARGUMENTS, exitCode);
  }

  @Test
  void call_jobWithoutStage_returnsInvalidCliArguments() {
    ReportCommand cmd = new ReportCommand(new FakeReportClient());
    int exitCode = new CommandLine(cmd).execute(
        "--pipeline", "default", "--run", "1", "--job", "compile");
    assertEquals(ExitCodes.INVALID_CLI_ARGUMENTS, exitCode);
  }

  @Test
  void call_jobWithoutRunOrStage_returnsInvalidCliArguments() {
    ReportCommand cmd = new ReportCommand(new FakeReportClient());
    int exitCode = new CommandLine(cmd).execute("--pipeline", "default", "--job", "compile");
    assertEquals(ExitCodes.INVALID_CLI_ARGUMENTS, exitCode);
  }

  // ── Client dispatch ──────────────────────────────────────────────────────────

  @Test
  void call_pipelineOnly_dispatchesToGetPipelineReport() {
    FakeReportClient fake = new FakeReportClient();
    int exitCode = new CommandLine(new ReportCommand(fake))
        .execute("--pipeline", "default");

    assertEquals(ExitCodes.OK, exitCode);
    assertEquals("getPipelineReport", fake.lastCalledMethod);
    assertEquals("default", fake.lastPipeline);
  }

  @Test
  void call_pipelineAndRun_dispatchesToGetRunReport() {
    FakeReportClient fake = new FakeReportClient();
    int exitCode = new CommandLine(new ReportCommand(fake))
        .execute("--pipeline", "default", "--run", "1");

    assertEquals(ExitCodes.OK, exitCode);
    assertEquals("getRunReport", fake.lastCalledMethod);
    assertEquals(1, fake.lastRun);
  }

  @Test
  void call_pipelineRunAndStage_dispatchesToGetStageReport() {
    FakeReportClient fake = new FakeReportClient();
    int exitCode = new CommandLine(new ReportCommand(fake))
        .execute("--pipeline", "default", "--run", "1", "--stage", "build");

    assertEquals(ExitCodes.OK, exitCode);
    assertEquals("getStageReport", fake.lastCalledMethod);
    assertEquals("build", fake.lastStage);
  }

  @Test
  void call_allOptions_dispatchesToGetJobReport() {
    FakeReportClient fake = new FakeReportClient();
    int exitCode = new CommandLine(new ReportCommand(fake))
        .execute("--pipeline", "default", "--run", "1", "--stage", "build", "--job", "compile");

    assertEquals(ExitCodes.OK, exitCode);
    assertEquals("getJobReport", fake.lastCalledMethod);
    assertEquals("compile", fake.lastJob);
  }

  // ── Output formatting ────────────────────────────────────────────────────────

  @Test
  void call_pipelineOnly_outputContainsPipelineKey() {
    FakeReportClient fake = new FakeReportClient();
    java.io.ByteArrayOutputStream out = captureStdout();

    new CommandLine(new ReportCommand(fake)).execute("--pipeline", "default");

    String yaml = out.toString(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(yaml.contains("pipeline"), "Output should contain 'pipeline' key");
    assertTrue(yaml.contains("default"), "Output should contain the pipeline name");
  }

  @Test
  void call_pipelineAndRun_outputContainsRunNo() {
    FakeReportClient fake = new FakeReportClient();
    java.io.ByteArrayOutputStream out = captureStdout();

    new CommandLine(new ReportCommand(fake)).execute("--pipeline", "default", "--run", "1");

    String yaml = out.toString(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(yaml.contains("run-no"), "Output should contain 'run-no' key");
  }

  // ── Error handling ───────────────────────────────────────────────────────────

  @Test
  void call_reportNotFound_returnsConfigValidationError() {
    FakeReportClient fake = new FakeReportClient();
    fake.throwNotFound = true;

    int exitCode = new CommandLine(new ReportCommand(fake)).execute("--pipeline", "missing");
    assertEquals(ExitCodes.CONFIG_VALIDATION_ERROR, exitCode);
  }

  @Test
  void call_connectException_returnsRuntimeExecutionError() {
    FakeReportClient fake = new FakeReportClient();
    fake.throwConnect = true;

    int exitCode = new CommandLine(new ReportCommand(fake)).execute("--pipeline", "default");
    assertEquals(ExitCodes.RUNTIME_EXECUTION_ERROR, exitCode);
  }

  @Test
  void call_genericException_returnsRuntimeExecutionError() {
    FakeReportClient fake = new FakeReportClient();
    fake.throwGeneric = true;

    int exitCode = new CommandLine(new ReportCommand(fake)).execute("--pipeline", "default");
    assertEquals(ExitCodes.RUNTIME_EXECUTION_ERROR, exitCode);
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private static <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  private static java.io.ByteArrayOutputStream captureStdout() {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    System.setOut(new java.io.PrintStream(out, true, java.nio.charset.StandardCharsets.UTF_8));
    return out;
  }

  /**
   * Fake {@link PipelineReportClient} that records which method was called
   * and the arguments passed, and optionally throws configured exceptions.
   */
  private static final class FakeReportClient implements PipelineReportClient {

    String lastCalledMethod;
    String lastPipeline;
    int lastRun;
    String lastStage;
    String lastJob;

    boolean throwNotFound = false;
    boolean throwConnect = false;
    boolean throwGeneric = false;

    @Override
    public Map<String, Object> getPipelineReport(String pipeline) throws Exception {
      maybeThrow(pipeline);
      lastCalledMethod = "getPipelineReport";
      lastPipeline = pipeline;
      return Map.of("pipeline", Map.of("name", pipeline, "runs", java.util.List.of()));
    }

    @Override
    public Map<String, Object> getRunReport(String pipeline, int run) throws Exception {
      maybeThrow(pipeline);
      lastCalledMethod = "getRunReport";
      lastPipeline = pipeline;
      lastRun = run;
      return Map.of("pipeline", Map.of("name", pipeline, "run-no", run, "stages",
          java.util.List.of()));
    }

    @Override
    public Map<String, Object> getStageReport(String pipeline, int run, String stage)
        throws Exception {
      maybeThrow(pipeline);
      lastCalledMethod = "getStageReport";
      lastPipeline = pipeline;
      lastRun = run;
      lastStage = stage;
      return Map.of("pipeline", Map.of("name", pipeline, "run-no", run, "stage",
          java.util.List.of(Map.of("name", stage, "jobs", java.util.List.of()))));
    }

    @Override
    public Map<String, Object> getJobReport(String pipeline, int run, String stage, String job)
        throws Exception {
      maybeThrow(pipeline);
      lastCalledMethod = "getJobReport";
      lastPipeline = pipeline;
      lastRun = run;
      lastStage = stage;
      lastJob = job;
      return Map.of("pipeline", Map.of("name", pipeline, "run-no", run, "stage",
          java.util.List.of(Map.of("name", stage, "job",
              java.util.List.of(Map.of("name", job))))));
    }

    private void maybeThrow(String pipeline) throws Exception {
      if (throwNotFound) {
        throw new ReportNotFoundException("Not found: " + pipeline);
      }
      if (throwConnect) {
        throw new java.net.ConnectException("Connection refused");
      }
      if (throwGeneric) {
        throw new RuntimeException("Unexpected error");
      }
    }
  }
}