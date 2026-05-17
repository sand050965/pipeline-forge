package edu.northeastern.cs7580.cicd.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.core.PathPolicy;
import edu.northeastern.cs7580.cicd.cli.core.RepoRootDetector;
import edu.northeastern.cs7580.cicd.cli.formatter.DefaultDryrunFormatter;
import edu.northeastern.cs7580.cicd.cli.formatter.DryrunFormatter;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.PipelineMetadata;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class DryrunCommandTest {
  private PrintStream originalOut;
  private PrintStream originalErr;
  private ByteArrayOutputStream outBuffer;
  private ByteArrayOutputStream errBuffer;

  @BeforeEach
  void setUpStreams() {
    originalOut = System.out;
    originalErr = System.err;
    outBuffer = new ByteArrayOutputStream();
    errBuffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(outBuffer, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  void executeReturnsOneAndPrintsErrorWhenValidationFails() throws Exception {
    // Mock PipelineService to throw validation exception
    PipelineService service = mock(PipelineService.class);
    when(service.validatePipeline(any(Path.class)))
        .thenThrow(new ValidationException(".pipelines/missing.yaml:0:0: ERROR, fake"));

    // Configure RepoRootDetector to return a valid repo root
    RepoRootDetector repoRootDetector = mock(RepoRootDetector.class);
    Path fakeRepoRoot = createTestRepoRoot();
    when(repoRootDetector.findRepoRoot(any())).thenReturn(fakeRepoRoot);

    // Configure PathPolicy to pass validation
    PathPolicy pathPolicy = mock(PathPolicy.class);
    Path inputPath = Path.of(".pipelines/missing.yaml");
    Path resolvedPath = fakeRepoRoot.resolve(".pipelines/missing.yaml");
    when(pathPolicy.parseAndRejectAbsolute(anyString())).thenReturn(inputPath);
    when(pathPolicy.resolveUnderRepoRoot(any(), any())).thenReturn(resolvedPath);
    doNothing().when(pathPolicy).enforceUnderPipelines(any(), any());
    doNothing().when(pathPolicy).enforceExistingRegularFile(any());

    DryrunFormatter formatter = (pipeline, plan) -> "";

    DryrunCommand cmd = new DryrunCommand(
        service,
        repoRootDetector,
        pathPolicy,
        formatter
    );

    int code = new CommandLine(cmd).execute(".pipelines/missing.yaml");

    assertEquals(ExitCodes.CONFIG_VALIDATION_ERROR, code);
    assertTrue(errBuffer.toString(StandardCharsets.UTF_8).contains("ERROR"));
  }

  @Test
  void executeReturnsZeroAndPrintsFormattedOutputWhenValidationSucceeds() throws Exception {
    // Create fake pipeline and execution plan
    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").description("").build())
        .stages(List.of("build"))
        .jobs(new HashMap<>())
        .build();

    Job job = Job.builder()
        .name("compile")
        .stage("build")
        .image("gradle:8.12-jdk21")
        .script(List.of("./gradlew classes"))
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(List.of(job))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(stage))
        .jobDependencies(Map.of("compile", List.of()))
        .build();

    // Mock PipelineService to return fake data
    PipelineService service = mock(PipelineService.class);
    when(service.validatePipeline(any(Path.class))).thenReturn(pipeline);
    when(service.createExecutionPlan(any(Path.class))).thenReturn(plan);

    // Configure RepoRootDetector to return a valid repo root
    RepoRootDetector repoRootDetector = mock(RepoRootDetector.class);
    Path fakeRepoRoot = createTestRepoRoot();
    when(repoRootDetector.findRepoRoot(any())).thenReturn(fakeRepoRoot);

    // Configure PathPolicy to pass validation
    PathPolicy pathPolicy = mock(PathPolicy.class);
    Path inputPath = Path.of(".pipelines/min.yaml");
    Path resolvedPath = fakeRepoRoot.resolve(".pipelines/min.yaml");
    when(pathPolicy.parseAndRejectAbsolute(anyString())).thenReturn(inputPath);
    when(pathPolicy.resolveUnderRepoRoot(any(), any())).thenReturn(resolvedPath);
    doNothing().when(pathPolicy).enforceUnderPipelines(any(), any());
    doNothing().when(pathPolicy).enforceExistingRegularFile(any());

    DryrunFormatter formatter = (p, pl) -> "build:\n";

    DryrunCommand cmd = new DryrunCommand(
        service,
        repoRootDetector,
        pathPolicy,
        formatter
    );

    int code = new CommandLine(cmd).execute(".pipelines/min.yaml");

    assertEquals(0, code);
    assertEquals(
        "OK: dryrun validation succeeded\nbuild:\n",
        outBuffer.toString(StandardCharsets.UTF_8)
    );
  }

  private Path createTestRepoRoot() {
    return Path.of("fake-repo");
  }


  @Test
  void defaultConstructor_initializesDependencies() throws Exception {
    DryrunCommand cmd = new DryrunCommand();

    Object pipelineService = getField(cmd, "pipelineService");
    Object repoRootDetector = getField(cmd, "repoRootDetector");
    final Object pathPolicy = getField(cmd, "pathPolicy");
    final Object formatter = getField(cmd, "formatter");

    assertNotNull(pipelineService);
    assertTrue(pipelineService instanceof PipelineService);

    assertNotNull(repoRootDetector);
    assertTrue(repoRootDetector instanceof RepoRootDetector);

    assertNotNull(pathPolicy);
    assertTrue(pathPolicy instanceof PathPolicy);

    assertNotNull(formatter);
    assertTrue(formatter instanceof DefaultDryrunFormatter);
    assertTrue(formatter instanceof DryrunFormatter);
  }

  private static Object getField(Object target, String fieldName) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    return f.get(target);
  }

  @Test
  void call_whenNotInGitRepo_returnsGitRepositoryError() throws Exception {
    final RepoRootDetector fakeDetector = new NullRepoRootDetector();
    final PathPolicy pathPolicy = new PathPolicy();
    final DefaultDryrunFormatter formatter = new DefaultDryrunFormatter();

    DryrunCommand cmd =
        new DryrunCommand(null, fakeDetector, pathPolicy, formatter);

    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(errContent, true, StandardCharsets.UTF_8));

    int exitCode = cmd.call();

    System.setErr(originalErr);

    assertEquals(ExitCodes.GIT_REPOSITORY_ERROR, exitCode);
    assertTrue(
        errContent.toString(StandardCharsets.UTF_8)
            .contains("cicd must be run from within a Git repository"));
  }

  /**
   * Fake RepoRootDetector that simulates not being inside a Git repository.
   */
  private static final class NullRepoRootDetector extends RepoRootDetector {
    @Override
    public Path findRepoRoot(Path start) {
      return null;
    }
  }

  @Test
  void executeOutputsAllowFailuresTrueWhenJobHasAllowFailuresSet() throws Exception {
    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").description("").build())
        .stages(List.of("test"))
        .jobs(new HashMap<>())
        .build();

    Job flakyJob = Job.builder()
        .name("flaky-check")
        .stage("test")
        .image("alpine:latest")
        .script(List.of("echo flaky"))
        .failures(true)
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("test")
        .jobs(List.of(flakyJob))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(stage))
        .jobDependencies(Map.of("flaky-check", List.of()))
        .build();

    PipelineService service = mock(PipelineService.class);
    when(service.validatePipeline(any(Path.class))).thenReturn(pipeline);
    when(service.createExecutionPlan(any(Path.class))).thenReturn(plan);

    RepoRootDetector repoRootDetector = mock(RepoRootDetector.class);
    Path fakeRepoRoot = createTestRepoRoot();
    when(repoRootDetector.findRepoRoot(any())).thenReturn(fakeRepoRoot);

    PathPolicy pathPolicy = mock(PathPolicy.class);
    Path inputPath = Path.of(".pipelines/allow-failures.yaml");
    Path resolvedPath = fakeRepoRoot.resolve(".pipelines/allow-failures.yaml");
    when(pathPolicy.parseAndRejectAbsolute(anyString())).thenReturn(inputPath);
    when(pathPolicy.resolveUnderRepoRoot(any(), any())).thenReturn(resolvedPath);
    doNothing().when(pathPolicy).enforceUnderPipelines(any(), any());
    doNothing().when(pathPolicy).enforceExistingRegularFile(any());

    DryrunCommand cmd =
        new DryrunCommand(service, repoRootDetector, pathPolicy, new DefaultDryrunFormatter());

    int code = new CommandLine(cmd).execute(".pipelines/allow-failures.yaml");

    assertEquals(0, code);
    String output = outBuffer.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("failures: true"));
  }

  @Test
  void getFilePath_returnsParsedArgument() {
    DryrunCommand cmd = new DryrunCommand();
    CommandLine line = new CommandLine(cmd);

    line.parseArgs(".pipelines/test.yaml");

    assertEquals(".pipelines/test.yaml", cmd.getFilePath());
  }
}
