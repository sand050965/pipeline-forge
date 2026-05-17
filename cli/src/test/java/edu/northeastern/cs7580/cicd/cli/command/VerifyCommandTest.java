package edu.northeastern.cs7580.cicd.cli.command;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.cli.core.DefaultPathResolver;
import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.core.PathPolicy;
import edu.northeastern.cs7580.cicd.cli.core.RepoRootDetector;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifyCommandTest {

  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  private VerifyCommand createCommand(
      DefaultPathResolver resolver,
      RepoRootDetector detector,
      PathPolicy policy) {
    return new VerifyCommand(resolver, detector, policy, mock(PipelineService.class));
  }

  @Test
  void verifyFailsWhenNotRunFromRepoRoot() {
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

    VerifyCommand cmd = createCommand(
        new DefaultPathResolver(),
        new FakeRepoRootDetector(null),
        new NoOpPathPolicy());

    int exitCode = cmd.call();

    assertEquals(ExitCodes.GIT_REPOSITORY_ERROR, exitCode);
    String stderr = err.toString(StandardCharsets.UTF_8);
    assertTrue(stderr.contains("must be run from within a Git repository"), stderr);
  }

  @Test
  void verifyUsesDefaultPathAndSucceeds(@TempDir Path tempDir) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

    Path yamlFile = tempDir.resolve("pipeline.yaml");
    Files.writeString(yamlFile, validYaml(), StandardCharsets.UTF_8);

    CapturingPathPolicy policy = new CapturingPathPolicy(yamlFile);
    VerifyCommand cmd = createCommand(
        new DefaultPathResolver(),
        new FakeRepoRootDetector(tempDir),
        policy);

    int exitCode = cmd.call();

    assertEquals(ExitCodes.OK, exitCode);
    assertEquals(".pipelines/pipeline.yaml", policy.getLastUserInput());

    String stdout = out.toString(StandardCharsets.UTF_8);
    assertTrue(stdout.contains("OK: "), stdout);
    assertTrue(stdout.contains(yamlFile.toAbsolutePath().normalize().toString()), stdout);
  }

  @Test
  void verifyReturnsInvalidArgsWhenPolicyThrows() {
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

    VerifyCommand cmd = createCommand(
        new DefaultPathResolver(),
        new FakeRepoRootDetector(createTestRepoRoot()),
        new ThrowingPathPolicy("configuration file not found: X"));

    int exitCode = cmd.call();

    assertEquals(ExitCodes.INVALID_CLI_ARGUMENTS, exitCode);
    String stderr = err.toString(StandardCharsets.UTF_8);
    assertTrue(stderr.contains("Error: configuration file not found: X"), stderr);
  }

  @Test
  void verifyRejectsAbsolutePathsViaPolicy() {
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

    VerifyCommand cmd = createCommand(
        new DefaultPathResolver(),
        new FakeRepoRootDetector(createTestRepoRoot()),
        new RejectAbsolutePathPolicy());

    cmd.setRelativePathForTest("/tmp/abs.yaml");
    int exitCode = cmd.call();

    assertEquals(ExitCodes.INVALID_CLI_ARGUMENTS, exitCode);
    String stderr = err.toString(StandardCharsets.UTF_8);
    assertTrue(stderr.contains("absolute paths are not allowed: /tmp/abs.yaml"), stderr);
  }

  @Test
  void verifyReturnsConfigValidationErrorWhenYamlInvalid(@TempDir Path tempDir) throws Exception {
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

    Path yamlFile = tempDir.resolve("bad.yaml");
    Files.writeString(yamlFile, "pipeline:\n  name: bad\nstages: []\n", StandardCharsets.UTF_8);

    // Create mock service that throws validation exception
    PipelineService mockService = mock(PipelineService.class);
    when(mockService.validatePipeline(any())).thenThrow(
        new ValidationException(
            yamlFile.toAbsolutePath().normalize().toString()
                + ":3:1: ERROR, empty stages not allowed"
        )
    );

    VerifyCommand cmd = new VerifyCommand(
        new DefaultPathResolver(),
        new FakeRepoRootDetector(tempDir),
        new CapturingPathPolicy(yamlFile),
        mockService
    );

    cmd.setRelativePathForTest(".pipelines/bad.yaml");
    int exitCode = cmd.call();

    assertEquals(ExitCodes.CONFIG_VALIDATION_ERROR, exitCode);
    String stderr = err.toString(StandardCharsets.UTF_8);
    assertTrue(stderr.contains(yamlFile.toAbsolutePath().normalize().toString()), stderr);
    assertTrue(stderr.contains(": ERROR,"), stderr);
  }

  @Test
  void shouldCallValidateDirectoryWhenInputIsDirectory(@TempDir Path tempDir) throws Exception {
    DefaultPathResolver resolver = mock(DefaultPathResolver.class);
    RepoRootDetector repoRootDetector = mock(RepoRootDetector.class);
    PathPolicy pathPolicy = mock(PathPolicy.class);
    PipelineService pipelineService = mock(PipelineService.class);

    when(repoRootDetector.findRepoRoot(any())).thenReturn(tempDir);
    when(resolver.resolve(any())).thenReturn("test-dir");
    when(pathPolicy.parseAndRejectAbsolute(any())).thenReturn(Path.of("test-dir"));
    when(pathPolicy.resolveUnderRepoRoot(any(), any())).thenReturn(tempDir);
    doNothing().when(pathPolicy).enforceUnderPipelines(any(), any());

    VerifyCommand command = new VerifyCommand(
        resolver,
        repoRootDetector,
        pathPolicy,
        pipelineService
    );

    command.setRelativePathForTest("test-dir");

    command.call();

    verify(pipelineService, times(1)).validateDirectory(tempDir);
    verify(pipelineService, never()).validatePipeline(any());
  }

  private static String validYaml() {
    return ""
        + "pipeline:\n"
        + "  name: good-pipeline\n"
        + "stages:\n"
        + "  - build\n"
        + "\n"
        + "J1:\n"
        + "  - stage: build\n"
        + "  - image: alpine:latest\n"
        + "  - script: echo \"hi\"\n";
  }

  private static final class FakeRepoRootDetector extends RepoRootDetector {
    private final Path repoRoot;

    private FakeRepoRootDetector(Path repoRoot) {
      this.repoRoot = repoRoot;
    }

    @Override
    public Path findRepoRoot(Path startPath) {
      return repoRoot;
    }

    @Override
    public boolean isRepoRoot(Path path) {
      return repoRoot != null && repoRoot.equals(path);
    }
  }

  private static class NoOpPathPolicy extends PathPolicy {
    @Override
    public Path parseAndRejectAbsolute(String userInput) {
      return Path.of(userInput);
    }

    @Override
    public Path resolveUnderRepoRoot(Path repoRoot, Path userPath) {
      return repoRoot.resolve(userPath).toAbsolutePath().normalize();
    }

    @Override
    public void enforceUnderPipelines(Path repoRoot, Path resolvedPath) {
    }

    @Override
    public void enforceExistingRegularFile(Path resolvedPath) {
    }
  }

  private static final class CapturingPathPolicy extends PathPolicy {
    private final Path resolvedPath;
    private String lastUserInput;

    private CapturingPathPolicy(Path resolvedPath) {
      this.resolvedPath = resolvedPath;
    }

    @Override
    public Path parseAndRejectAbsolute(String userInput) {
      this.lastUserInput = userInput;
      return Path.of(userInput);
    }

    @Override
    public Path resolveUnderRepoRoot(Path repoRoot, Path userPath) {
      return resolvedPath.toAbsolutePath().normalize();
    }

    @Override
    public void enforceUnderPipelines(Path repoRoot, Path resolvedPath) {
    }

    @Override
    public void enforceExistingRegularFile(Path resolvedPath) {
    }

    private String getLastUserInput() {
      return lastUserInput;
    }
  }

  private static final class ThrowingPathPolicy extends PathPolicy {
    private final String message;

    private ThrowingPathPolicy(String message) {
      this.message = message;
    }

    @Override
    public Path parseAndRejectAbsolute(String userInput) {
      throw new IllegalArgumentException(message);
    }
  }

  private static class RejectAbsolutePathPolicy extends NoOpPathPolicy {
    @Override
    public Path parseAndRejectAbsolute(String userInput) {
      if (Path.of(userInput).isAbsolute()) {
        throw new IllegalArgumentException("absolute paths are not allowed: " + userInput);
      }
      return super.parseAndRejectAbsolute(userInput);
    }
  }

  private Path createTestRepoRoot() {
    return Path.of("fake/repo/");
  }
}