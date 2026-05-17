package edu.northeastern.cs7580.cicd.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.cs7580.cicd.cli.client.PipelineExecutionClient;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunRequestDto;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunResponseDto;
import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.core.GitStateValidator;
import edu.northeastern.cs7580.cicd.cli.core.RepoRootDetector;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Tests for the HTTP execution path of {@link RunCommand}.
 */
class RunCommandHttpTest {

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() throws Exception {
    // Initialize a bare-minimum Git repo with a commit and remote URL.
    Git git = Git.init().setDirectory(tempDir.toFile()).call();
    git.getRepository().getConfig().setString("remote", "origin", "url",
        "https://github.com/test/repo.git");
    git.getRepository().getConfig().save();

    Path pipelinesDir = tempDir.resolve(".pipelines");
    Files.createDirectories(pipelinesDir);
    Files.writeString(pipelinesDir.resolve("default.yaml"),
        "pipeline:\n  name: default\nstages:\n  - build\n");
    Files.writeString(pipelinesDir.resolve("release.yaml"),
        "pipeline:\n  name: release\nstages:\n  - deploy\n");

    // Create an initial commit so HEAD is resolvable.
    git.add().addFilepattern(".").call();
    git.commit().setMessage("initial commit").call();
    git.close();
  }

  @Test
  void run_success_returnsOk() throws Exception {
    FakeExecutionClient fakeClient = new FakeExecutionClient();
    fakeClient.nextResponse = createResponse("exec-7", "SUCCESS", "ok");

    RunCommand cmd = createCommand(fakeClient);
    CommandLine line = new CommandLine(cmd);

    int exitCode = line.execute("--name", "default");
    assertEquals(ExitCodes.OK, exitCode);

    RunRequestDto sent = fakeClient.lastRequest;
    assertNotNull(sent);
    assertEquals("default", sent.getName());
    assertNotNull(sent.getPipelineFilePath());
    assertNotNull(sent.getRepositoryUrl());
    assertNotNull(sent.getResolvedCommitHash());
  }

  @Test
  void run_httpFailure_returnsRuntimeExecutionError() {
    FakeExecutionClient fakeClient = new FakeExecutionClient();
    fakeClient.throwOnExecute = new RuntimeException("boom");

    RunCommand cmd = createCommand(fakeClient);
    CommandLine line = new CommandLine(cmd);

    // Use --file since it avoids scanning by name.
    int exitCode = line.execute("--file", ".pipelines/default.yaml");
    assertEquals(ExitCodes.RUNTIME_EXECUTION_ERROR, exitCode);
  }

  @Test
  void run_missingNameAndFile_returnsInvalidCliArguments() {
    FakeExecutionClient fakeClient = new FakeExecutionClient();
    RunCommand cmd = createCommand(fakeClient);
    CommandLine line = new CommandLine(cmd);

    int exitCode = line.execute();
    assertEquals(ExitCodes.INVALID_CLI_ARGUMENTS, exitCode);
  }

  @Test
  void run_withFile_sendsFilePathToRequest() throws Exception {
    FakeExecutionClient fakeClient = new FakeExecutionClient();
    fakeClient.nextResponse = createResponse("exec-10", "SUCCESS", "ok");

    RunCommand cmd = createCommand(fakeClient);
    CommandLine line = new CommandLine(cmd);

    int exitCode = line.execute("--file", ".pipelines/release.yaml");
    assertEquals(ExitCodes.OK, exitCode);

    RunRequestDto sent = fakeClient.lastRequest;
    assertNotNull(sent);
    assertEquals(".pipelines/release.yaml", sent.getFile());
    assertNotNull(sent.getPipelineFilePath());
  }

  @Test
  void run_success_sendsRemoteUrlByDefault() throws Exception {
    FakeExecutionClient fakeClient = new FakeExecutionClient();
    fakeClient.nextResponse = createResponse("exec-8", "SUCCESS", "ok");

    RunCommand cmd = createCommand(fakeClient);
    new CommandLine(cmd).execute("--name", "default");

    assertEquals("https://github.com/test/repo.git", fakeClient.lastRequest.getRepositoryUrl());
  }

  @Test
  void run_noRemoteOrigin_fallsBackToFilePath() throws Exception {
    // Remove the origin remote so the fallback path is exercised.
    Git git = Git.open(tempDir.toFile());
    git.getRepository().getConfig().unsetSection("remote", "origin");
    git.getRepository().getConfig().save();
    git.close();

    FakeExecutionClient fakeClient = new FakeExecutionClient();
    fakeClient.nextResponse = createResponse("exec-9", "SUCCESS", "ok");

    RunCommand cmd = createCommand(fakeClient);
    new CommandLine(cmd).execute("--name", "default");

    assertEquals(tempDir.toAbsolutePath().toString(), fakeClient.lastRequest.getRepositoryUrl());
  }

  @Test
  void run_sshRemoteUrl_isNormalizedToHttps() throws Exception {
    Git git = Git.open(tempDir.toFile());
    git.getRepository().getConfig().setString("remote", "origin", "url",
        "git@github.com:test/ssh-repo.git");
    git.getRepository().getConfig().save();
    git.close();

    FakeExecutionClient fakeClient = new FakeExecutionClient();
    fakeClient.nextResponse = createResponse("exec-12", "SUCCESS", "ok");

    RunCommand cmd = createCommand(fakeClient);
    new CommandLine(cmd).execute("--name", "default");

    assertEquals("https://github.com/test/ssh-repo.git",
        fakeClient.lastRequest.getRepositoryUrl());
  }

  @Test
  void run_localFlag_sendsLocalPath() throws Exception {
    FakeExecutionClient fakeClient = new FakeExecutionClient();
    fakeClient.nextResponse = createResponse("exec-11", "SUCCESS", "ok");

    RunCommand cmd = createCommand(fakeClient);
    new CommandLine(cmd).execute("--local", "--name", "default");

    assertEquals(tempDir.toAbsolutePath().toString(), fakeClient.lastRequest.getRepositoryUrl());
  }

  @Test
  void run_success_printsRunMessage() throws Exception {
    FakeExecutionClient fakeClient = new FakeExecutionClient();
    fakeClient.nextResponse = createResponse("exec-1", "PENDING", "CI run: 5");

    RunCommand cmd = createCommand(fakeClient);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(out, false, StandardCharsets.UTF_8));
    try {
      int exitCode = new CommandLine(cmd).execute("--name", "default");
      assertEquals(ExitCodes.OK, exitCode);
    } finally {
      System.setOut(originalOut);
    }

    assertTrue(out.toString(StandardCharsets.UTF_8).contains("CI run: 5"));
  }

  @Test
  void run_validationFailed_printsToStderrAndReturnsConfigValidationError() throws Exception {
    FakeExecutionClient fakeClient = new FakeExecutionClient();
    fakeClient.nextResponse = createResponse("exec-2", "VALIDATION_FAILED",
        "Pipeline validation failed: missing required field 'image'");

    RunCommand cmd = createCommand(fakeClient);

    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(err, false, StandardCharsets.UTF_8));
    try {
      int exitCode = new CommandLine(cmd).execute("--name", "default");
      assertEquals(ExitCodes.CONFIG_VALIDATION_ERROR, exitCode);
    } finally {
      System.setErr(originalErr);
    }

    assertTrue(err.toString(StandardCharsets.UTF_8).contains("Pipeline validation failed"));
  }

  @Test
  void run_internalError_printsMessageToStderrAndReturnsError() {
    FakeExecutionClient fakeClient = new FakeExecutionClient();
    fakeClient.throwOnExecute = new RuntimeException(
        "Internal error: your request was valid but the system encountered an unexpected error."
            + " Please file an issue.");

    RunCommand cmd = createCommand(fakeClient);

    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(err, false, StandardCharsets.UTF_8));
    try {
      int exitCode = new CommandLine(cmd).execute("--name", "default");
      assertEquals(ExitCodes.RUNTIME_EXECUTION_ERROR, exitCode);
    } finally {
      System.setErr(originalErr);
    }

    assertTrue(err.toString(StandardCharsets.UTF_8).contains("Please file an issue"));
  }

  /**
   * Creates a {@link RunCommand} with Git validation bypassed.
   *
   * @param client execution client to inject
   * @return configured run command
   */
  private RunCommand createCommand(PipelineExecutionClient client) {
    RepoRootDetector fixedRepoRootDetector = new FixedRepoRootDetector(tempDir);
    GitStateValidator noOpValidator = new NoOpGitStateValidator();
    return new RunCommand(client, fixedRepoRootDetector, noOpValidator);
  }

  private static RunResponseDto createResponse(
      String executionId, String status, String message) throws Exception {
    RunResponseDto resp = new RunResponseDto();
    setField(resp, "executionId", executionId);
    setField(resp, "status", status);
    setField(resp, "message", message);
    return resp;
  }

  private static final class FakeExecutionClient implements PipelineExecutionClient {
    private RunRequestDto lastRequest;
    private RunResponseDto nextResponse;
    private RuntimeException throwOnExecute;

    @Override
    public RunResponseDto executePipeline(RunRequestDto request) {
      this.lastRequest = request;
      if (throwOnExecute != null) {
        throw throwOnExecute;
      }
      return nextResponse;
    }
  }

  /**
   * Implements a repository root detector that always returns a fixed path.
   */
  private static final class FixedRepoRootDetector extends RepoRootDetector {

    private final Path repoRoot;

    /**
     * Creates a detector that always returns the given repository root.
     *
     * @param repoRoot repository root
     */
    private FixedRepoRootDetector(Path repoRoot) {
      this.repoRoot = repoRoot;
    }

    @Override
    public Path findRepoRoot(Path start) {
      return repoRoot;
    }
  }

  /**
   * Implements a Git state validator that always passes.
   */
  private static final class NoOpGitStateValidator implements GitStateValidator {

    @Override
    public void validate(Path repoRoot, String requestedBranch, String requestedCommit) {
      // No-op for HTTP path tests.
    }
  }

  private static <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }
}
