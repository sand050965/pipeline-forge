package edu.northeastern.cs7580.cicd.executionservice.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import edu.northeastern.cs7580.cicd.executionservice.model.JobResult;
import edu.northeastern.cs7580.cicd.executionservice.model.JobStatus;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DockerExecutorTest {

  private static final Path WORKSPACE = Path.of("tmp-workspace");

  private DockerClient dockerClient;
  private DockerExecutor executor;

  @BeforeEach
  void setUp() {
    dockerClient = mock(DockerClient.class);
    executor = new DockerExecutor();
    executorTestInit(executor, dockerClient);
  }

  @Test
  void executeJob_success_singleStep_completesAndCleansUp() {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("build");
    when(job.getImage()).thenReturn("alpine:latest");
    when(job.getScriptCommands()).thenReturn(List.of("echo hello"));

    stubImageExists("alpine:latest");
    stubContainerLifecycle("container-1");
    stubExecForOneCommand("container-1", "exec-1", 0, "hello\n");

    JobResult result = executor.executeJob(job, WORKSPACE);

    assertEquals("build", result.getJobName());
    assertEquals(JobStatus.COMPLETED, result.getStatus());
    assertEquals(0, result.getExitCode());
    assertNotNull(result.getExecutionTime());
    assertTrue(result.getOutput().contains("$ echo hello"));
    assertTrue(result.getOutput().contains("hello"));

    verify(dockerClient, times(1)).startContainerCmd("container-1");
    verify(dockerClient, times(1)).stopContainerCmd("container-1");
    verify(dockerClient, times(1)).removeContainerCmd("container-1");
  }

  @Test
  void executeJob_stepFails_stopsFurtherSteps_andCleansUp() {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("lint");
    when(job.getImage()).thenReturn("alpine:latest");
    when(job.getScriptCommands()).thenReturn(List.of("cmd1", "cmd2"));

    stubImageExists("alpine:latest");
    stubContainerLifecycle("container-2");

    // First command fails.
    stubExecForOneCommand("container-2", "exec-2", 1, "bad\n");

    JobResult result = executor.executeJob(job, WORKSPACE);

    assertEquals("lint", result.getJobName());
    assertEquals(JobStatus.FAILED, result.getStatus());
    assertEquals(1, result.getExitCode());

    // Only one exec should be created/started because cmd1 fails.
    verify(dockerClient, times(1)).execCreateCmd("container-2");
    verify(dockerClient, times(1)).execStartCmd(any(String.class));
    verify(dockerClient, times(1)).stopContainerCmd("container-2");
    verify(dockerClient, times(1)).removeContainerCmd("container-2");
  }

  @Test
  void executeJob_imageNotFound_returnsFailed_andDoesNotCreateContainer() {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("build");
    when(job.getImage()).thenReturn("missing:latest");
    when(job.getScriptCommands()).thenReturn(List.of("echo hi"));

    InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
    when(dockerClient.inspectImageCmd(eq("missing:latest"))).thenReturn(inspectImageCmd);
    when(inspectImageCmd.exec()).thenThrow(new NotFoundException("not found"));

    // Pull will also throw NotFoundException to simulate missing image.
    when(dockerClient.pullImageCmd(eq("missing:latest"))).thenThrow(new NotFoundException("pull"));

    JobResult result = executor.executeJob(job, WORKSPACE);

    assertEquals("build", result.getJobName());
    assertEquals(JobStatus.FAILED, result.getStatus());
    assertEquals(1, result.getExitCode());
    assertTrue(result.getOutput().toLowerCase().contains("image"));

    verify(dockerClient, never()).createContainerCmd(any(String.class));
  }

  @Test
  void executeJob_nullJob_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> executor.executeJob(null, WORKSPACE));
  }

  @Test
  void executeJob_nullWorkspace_throwsNullPointerException() {
    Job job = mock(Job.class);
    assertThrows(NullPointerException.class, () -> executor.executeJob(job, null));
  }

  @Test
  void executeJob_nullImage_returnsFailed() {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("build");
    when(job.getImage()).thenReturn(null);
    when(job.getScriptCommands()).thenReturn(List.of("echo hi"));

    JobResult result = executor.executeJob(job, WORKSPACE);

    assertEquals("build", result.getJobName());
    assertEquals(JobStatus.FAILED, result.getStatus());
    assertEquals(1, result.getExitCode());
    assertTrue(result.getOutput().contains("Missing required field"));
  }

  @Test
  void executeJob_blankImage_returnsFailed() {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("build");
    when(job.getImage()).thenReturn("   ");
    when(job.getScriptCommands()).thenReturn(List.of("echo hi"));

    JobResult result = executor.executeJob(job, WORKSPACE);

    assertEquals("build", result.getJobName());
    assertEquals(JobStatus.FAILED, result.getStatus());
    assertEquals(1, result.getExitCode());
    assertTrue(result.getOutput().contains("Missing required field"));
  }

  @Test
  void executeJob_noScriptCommands_returnsFailed() {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn(null);
    when(job.getImage()).thenReturn("alpine:latest");
    when(job.getScriptCommands()).thenReturn(List.of());

    stubImageExists("alpine:latest");
    stubContainerLifecycle("container-empty");

    JobResult result = executor.executeJob(job, WORKSPACE);

    assertEquals(JobStatus.FAILED, result.getStatus());
    assertEquals(1, result.getExitCode());
    assertTrue(result.getOutput().contains("<unknown>"));
  }

  @Test
  void executeJob_nullScriptCommands_returnsFailed() {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("test-job");
    when(job.getImage()).thenReturn("alpine:latest");
    when(job.getScriptCommands()).thenReturn(null);

    stubImageExists("alpine:latest");
    stubContainerLifecycle("container-null-scripts");

    JobResult result = executor.executeJob(job, WORKSPACE);

    assertEquals("test-job", result.getJobName());
    assertEquals(JobStatus.FAILED, result.getStatus());
    assertEquals(1, result.getExitCode());
  }

  @Test
  void executeJob_dockerClientException_returnsFailed() {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("build");
    when(job.getImage()).thenReturn("alpine:latest");
    when(job.getScriptCommands()).thenReturn(List.of("echo hi"));

    stubImageExists("alpine:latest");

    CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
    when(dockerClient.createContainerCmd(any(String.class))).thenReturn(createContainerCmd);
    when(createContainerCmd.withHostConfig(any(HostConfig.class))).thenReturn(createContainerCmd);
    when(createContainerCmd.withWorkingDir(any(String.class))).thenReturn(createContainerCmd);
    when(createContainerCmd.withEntrypoint(any(String.class))).thenReturn(createContainerCmd);
    when(createContainerCmd.withCmd(any(String[].class))).thenReturn(createContainerCmd);
    when(createContainerCmd.withTty(anyBoolean())).thenReturn(createContainerCmd);
    when(createContainerCmd.exec()).thenThrow(new DockerClientException("connection refused"));

    JobResult result = executor.executeJob(job, WORKSPACE);

    assertEquals("build", result.getJobName());
    assertEquals(JobStatus.FAILED, result.getStatus());
    assertEquals(1, result.getExitCode());
    assertTrue(result.getOutput().contains("connection refused"));
  }

  @Test
  void executeJob_unexpectedRuntimeException_returnsFailed() {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("build");
    when(job.getImage()).thenReturn("alpine:latest");
    when(job.getScriptCommands()).thenReturn(List.of("echo hi"));

    stubImageExists("alpine:latest");

    CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
    when(dockerClient.createContainerCmd(any(String.class))).thenReturn(createContainerCmd);
    when(createContainerCmd.withHostConfig(any(HostConfig.class))).thenReturn(createContainerCmd);
    when(createContainerCmd.withWorkingDir(any(String.class))).thenReturn(createContainerCmd);
    when(createContainerCmd.withEntrypoint(any(String.class))).thenReturn(createContainerCmd);
    when(createContainerCmd.withCmd(any(String[].class))).thenReturn(createContainerCmd);
    when(createContainerCmd.withTty(anyBoolean())).thenReturn(createContainerCmd);
    when(createContainerCmd.exec()).thenThrow(new IllegalStateException("unexpected error"));

    JobResult result = executor.executeJob(job, WORKSPACE);

    assertEquals("build", result.getJobName());
    assertEquals(JobStatus.FAILED, result.getStatus());
    assertEquals(1, result.getExitCode());
    assertTrue(result.getOutput().contains("unexpected error"));
  }

  @Test
  void executeJob_blankCommandsSkipped_completesSuccessfully() {
    Job job = mock(Job.class);
    when(job.getName()).thenReturn("build");
    when(job.getImage()).thenReturn("alpine:latest");
    when(job.getScriptCommands()).thenReturn(List.of("", "  ", "echo done"));

    stubImageExists("alpine:latest");
    stubContainerLifecycle("container-blank");
    stubExecForOneCommand("container-blank", "exec-blank", 0, "done\n");

    JobResult result = executor.executeJob(job, WORKSPACE);

    assertEquals("build", result.getJobName());
    assertEquals(JobStatus.COMPLETED, result.getStatus());
    assertEquals(0, result.getExitCode());
    assertTrue(result.getOutput().contains("done"));
  }

  private void stubImageExists(String image) {
    InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
    when(dockerClient.inspectImageCmd(eq(image))).thenReturn(inspectImageCmd);

    InspectImageResponse response = mock(InspectImageResponse.class);
    when(inspectImageCmd.exec()).thenReturn(response);
  }

  private void stubContainerLifecycle(String containerId) {
    CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
    when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
    when(createCmd.withHostConfig(any())).thenReturn(createCmd);
    when(createCmd.withWorkingDir(anyString())).thenReturn(createCmd);
    when(createCmd.withEntrypoint(anyString())).thenReturn(createCmd);
    when(createCmd.withCmd(any(String[].class))).thenReturn(createCmd);
    when(createCmd.withTty(anyBoolean())).thenReturn(createCmd);

    CreateContainerResponse createResp = mock(CreateContainerResponse.class);
    when(createCmd.exec()).thenReturn(createResp);
    when(createResp.getId()).thenReturn(containerId);

    StartContainerCmd startCmd = mock(StartContainerCmd.class);
    when(dockerClient.startContainerCmd(containerId)).thenReturn(startCmd);
    doNothing().when(startCmd).exec();

    InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
    when(dockerClient.inspectContainerCmd(containerId)).thenReturn(inspectCmd);

    InspectContainerResponse inspectResp = mock(InspectContainerResponse.class);
    when(inspectCmd.exec()).thenReturn(inspectResp);

    InspectContainerResponse.ContainerState state = mock(
        InspectContainerResponse.ContainerState.class);
    when(inspectResp.getState()).thenReturn(state);
    when(state.getRunning()).thenReturn(true);

    StopContainerCmd stopCmd = mock(StopContainerCmd.class);
    when(dockerClient.stopContainerCmd(containerId)).thenReturn(stopCmd);
    when(stopCmd.withTimeout(anyInt())).thenReturn(stopCmd);
    doNothing().when(stopCmd).exec();

    RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
    when(dockerClient.removeContainerCmd(containerId)).thenReturn(removeCmd);
    when(removeCmd.withForce(anyBoolean())).thenReturn(removeCmd);
    doNothing().when(removeCmd).exec();
  }

  private void stubExecForOneCommand(
      String containerId, String execId, int exitCode, String stdout) {
    ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
    when(dockerClient.execCreateCmd(eq(containerId))).thenReturn(execCreateCmd);

    when(execCreateCmd.withAttachStdout(true)).thenReturn(execCreateCmd);
    when(execCreateCmd.withAttachStderr(true)).thenReturn(execCreateCmd);
    when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);

    ExecCreateCmdResponse execCreateResp = mock(ExecCreateCmdResponse.class);
    when(execCreateCmd.exec()).thenReturn(execCreateResp);
    when(execCreateResp.getId()).thenReturn(execId);

    ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
    when(dockerClient.execStartCmd(eq(execId))).thenReturn(execStartCmd);

    when(execStartCmd.exec(any())).thenAnswer(invocation -> {
      Object cb = invocation.getArgument(0);
      @SuppressWarnings("unchecked")
      var callback =
          (com.github.dockerjava.api.async.ResultCallbackTemplate<?, Frame>) cb;

      if (stdout != null && !stdout.isEmpty()) {
        callback.onNext(new Frame(StreamType.STDOUT, stdout.getBytes(StandardCharsets.UTF_8)));
      }
      callback.onComplete();
      return cb;
    });

    InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
    when(dockerClient.inspectExecCmd(eq(execId))).thenReturn(inspectExecCmd);

    InspectExecResponse inspectExecResp = mock(InspectExecResponse.class);
    when(inspectExecResp.getExitCodeLong()).thenReturn((long) exitCode);
    when(inspectExecCmd.exec()).thenReturn(inspectExecResp);
  }

  /**
   * Injects a mocked {@link DockerClient} into a {@link DockerExecutor} for testing.
   *
   * @param executor the executor under test
   * @param dockerClient the mock docker client
   */
  private static void executorTestInit(DockerExecutor executor, DockerClient dockerClient) {
    try {
      var field = DockerExecutor.class.getDeclaredField("docker");
      field.setAccessible(true);
      field.set(executor, dockerClient);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to inject DockerClient for test", e);
    }
  }
}

