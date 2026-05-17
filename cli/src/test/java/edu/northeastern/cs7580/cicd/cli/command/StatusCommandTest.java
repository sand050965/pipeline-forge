package edu.northeastern.cs7580.cicd.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.cs7580.cicd.cli.client.PipelineStatusClient;
import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.exception.ReportNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Unit tests for {@link StatusCommand}.
 *
 * <p>Verifies option validation, client dispatch, YAML output format, and exit
 * code handling. A {@link FakeStatusClient} is injected to avoid real HTTP calls.
 */
class StatusCommandTest {

  // ── Constructor ──────────────────────────────────────────────────────────────

  @Test
  void defaultConstructor_shouldCreateInstance() {
    assertNotNull(new StatusCommand());
  }

  // ── Option validation ─────────────────────────────────────────────────────────

  @Test
  void call_neitherRepoNorPipeline_returnsInvalidCliArguments() {
    int exitCode = new CommandLine(new StatusCommand(new FakeStatusClient())).execute();
    assertEquals(ExitCodes.INVALID_CLI_ARGUMENTS, exitCode);
  }

  @Test
  void call_bothRepoAndPipeline_returnsInvalidCliArguments() {
    int exitCode = new CommandLine(new StatusCommand(new FakeStatusClient()))
        .execute("--repo", "https://github.com/org/repo", "--pipeline", "default", "--run", "1");
    assertEquals(ExitCodes.INVALID_CLI_ARGUMENTS, exitCode);
  }

  @Test
  void call_pipelineWithoutRun_returnsInvalidCliArguments() {
    int exitCode = new CommandLine(new StatusCommand(new FakeStatusClient()))
        .execute("--pipeline", "default");
    assertEquals(ExitCodes.INVALID_CLI_ARGUMENTS, exitCode);
  }

  @Test
  void call_repoWithRun_returnsInvalidCliArguments() {
    int exitCode = new CommandLine(new StatusCommand(new FakeStatusClient()))
        .execute("--repo", "https://github.com/org/repo", "--run", "1");
    assertEquals(ExitCodes.INVALID_CLI_ARGUMENTS, exitCode);
  }

  // ── Client dispatch ───────────────────────────────────────────────────────────

  @Test
  void call_repoMode_dispatchesToGetStatusByRepo() {
    FakeStatusClient fake = new FakeStatusClient();
    int exitCode = new CommandLine(new StatusCommand(fake))
        .execute("--repo", "https://github.com/org/repo");

    assertEquals(ExitCodes.OK, exitCode);
    assertEquals("getStatusByRepo", fake.lastCalledMethod);
    assertEquals("https://github.com/org/repo", fake.lastRepo);
  }

  @Test
  void call_pipelineAndRunMode_dispatchesToGetStatusByRun() {
    FakeStatusClient fake = new FakeStatusClient();
    int exitCode = new CommandLine(new StatusCommand(fake))
        .execute("--pipeline", "default", "--run", "3");

    assertEquals(ExitCodes.OK, exitCode);
    assertEquals("getStatusByRun", fake.lastCalledMethod);
    assertEquals("default", fake.lastPipeline);
    assertEquals(3, fake.lastRun);
  }

  // ── Output formatting ─────────────────────────────────────────────────────────

  @Test
  void call_repoMode_outputContainsStageAndJobStatus() {
    FakeStatusClient fake = new FakeStatusClient();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

    new CommandLine(new StatusCommand(fake)).execute("--repo", "https://github.com/org/repo");

    System.setOut(System.out);
    String yaml = out.toString(StandardCharsets.UTF_8);
    assertTrue(yaml.contains("build"));
    assertTrue(yaml.contains("SUCCESS"));
    assertTrue(yaml.contains("compile"));
  }

  @Test
  void call_emptyStages_outputIsEmpty() {
    FakeStatusClient fake = new FakeStatusClient();
    fake.returnEmptyStages = true;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

    new CommandLine(new StatusCommand(fake)).execute("--repo", "https://github.com/org/repo");

    System.setOut(System.out);
    String yaml = out.toString(StandardCharsets.UTF_8);
    assertTrue(!yaml.contains("build") && !yaml.contains("compile"));
  }

  // ── Error handling ────────────────────────────────────────────────────────────

  @Test
  void call_notFound_returnsConfigValidationError() {
    FakeStatusClient fake = new FakeStatusClient();
    fake.throwNotFound = true;

    int exitCode = new CommandLine(new StatusCommand(fake))
        .execute("--repo", "https://github.com/org/repo");
    assertEquals(ExitCodes.CONFIG_VALIDATION_ERROR, exitCode);
  }

  @Test
  void call_connectException_returnsRuntimeExecutionError() {
    FakeStatusClient fake = new FakeStatusClient();
    fake.throwConnect = true;

    int exitCode = new CommandLine(new StatusCommand(fake))
        .execute("--repo", "https://github.com/org/repo");
    assertEquals(ExitCodes.RUNTIME_EXECUTION_ERROR, exitCode);
  }

  @Test
  void call_genericException_returnsRuntimeExecutionError() {
    FakeStatusClient fake = new FakeStatusClient();
    fake.throwGeneric = true;

    int exitCode = new CommandLine(new StatusCommand(fake))
        .execute("--repo", "https://github.com/org/repo");
    assertEquals(ExitCodes.RUNTIME_EXECUTION_ERROR, exitCode);
  }

  @Test
  void call_genericExceptionWithNullMessage_returnsRuntimeExecutionError() {
    FakeStatusClient fake = new FakeStatusClient();
    fake.throwNullMessage = true;

    int exitCode = new CommandLine(new StatusCommand(fake))
        .execute("--repo", "https://github.com/org/repo");
    assertEquals(ExitCodes.RUNTIME_EXECUTION_ERROR, exitCode);
  }

  // ── Fake client ───────────────────────────────────────────────────────────────

  private static final class FakeStatusClient implements PipelineStatusClient {

    String lastCalledMethod;
    String lastRepo;
    String lastPipeline;
    int lastRun;

    boolean throwNotFound = false;
    boolean throwConnect = false;
    boolean throwGeneric = false;
    boolean throwNullMessage = false;
    boolean returnEmptyStages = false;

    @Override
    public Map<String, Object> getStatusByRepo(String repoUrl) throws Exception {
      maybeThrow();
      lastCalledMethod = "getStatusByRepo";
      lastRepo = repoUrl;
      return buildResponse();
    }

    @Override
    public Map<String, Object> getStatusByRun(String pipeline, int runNo) throws Exception {
      maybeThrow();
      lastCalledMethod = "getStatusByRun";
      lastPipeline = pipeline;
      lastRun = runNo;
      return buildResponse();
    }

    private Map<String, Object> buildResponse() {
      if (returnEmptyStages) {
        return Map.of("pipeline-name", "default", "run-no", 1, "status", "SUCCESS",
            "stages", List.of());
      }
      return Map.of(
          "pipeline-name", "default",
          "run-no", 1,
          "status", "SUCCESS",
          "stages", List.of(
              Map.of("name", "build", "status", "SUCCESS",
                  "jobs", List.of(
                      Map.of("name", "compile", "status", "SUCCESS")
                  ))
          )
      );
    }

    private void maybeThrow() throws Exception {
      if (throwNotFound) {
        throw new ReportNotFoundException("Not found");
      }
      if (throwConnect) {
        throw new java.net.ConnectException("Connection refused");
      }
      if (throwGeneric) {
        throw new RuntimeException("Unexpected error");
      }
      if (throwNullMessage) {
        throw new RuntimeException((String) null);
      }
    }
  }
}
