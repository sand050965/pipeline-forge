package edu.northeastern.cs7580.cicd.executionservice.executor;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import edu.northeastern.cs7580.cicd.executionservice.exception.DockerException;
import edu.northeastern.cs7580.cicd.executionservice.model.JobResult;
import edu.northeastern.cs7580.cicd.executionservice.model.JobStatus;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Executes pipeline jobs inside Docker containers.
 *
 * <p>This component is responsible for executing an individual {@link Job} in an isolated
 * Docker container using the job's configured image. It bind-mounts a prepared workspace into
 * the container, runs all script steps sequentially within the same container, captures
 * combined stdout/stderr output, and always cleans up the container resources.
 *
 * <p>The {@code DockerExecutor} enforces the following rules:
 * <ul>
 *   <li>A container is created using the job's {@code image} specification.</li>
 *   <li>The workspace directory is bind-mounted into the container at {@code /workspace}.</li>
 *   <li>All job script steps execute in order within the same running container.</li>
 *   <li>Output is captured from each step and appended to the job output.</li>
 *   <li>The container is removed after execution, whether success or failure.</li>
 * </ul>
 */
@Component
public class DockerExecutor {
  private static final Logger logger = LoggerFactory.getLogger(DockerExecutor.class);

  private static final String WORKSPACE_CONTAINER_PATH = "/workspace";
  private static final Volume WORKSPACE_VOLUME = new Volume(WORKSPACE_CONTAINER_PATH);

  private DockerClient docker;

  /**
   * Initializes the Docker client using environment defaults (e.g., DOCKER_HOST).
   */
  @PostConstruct
  public void init() {
    DefaultDockerClientConfig config =
        DefaultDockerClientConfig.createDefaultConfigBuilder().build();
    DockerHttpClient httpClient =
        new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .build();
    docker = DockerClientImpl.getInstance(config, httpClient);
  }

  /**
   * Executes a single job in a Docker container with the provided workspace mounted.
   *
   * @param job the job to execute
   * @param workspacePath the workspace directory to mount into the container
   * @return the job execution result
   */
  public JobResult executeJob(Job job, Path workspacePath) {
    Objects.requireNonNull(job, "job");
    Objects.requireNonNull(workspacePath, "workspacePath");

    Instant start = Instant.now();
    String containerId = null;
    StringBuilder output = new StringBuilder();
    int lastExitCode = 0;

    try {
      String image = requireNonBlank(job.getImage(), "job.image");
      pullImageIfNeeded(image, output);

      containerId = createAndStartContainer(image, workspacePath);

      List<String> commands = job.getScriptCommands();
      if (commands == null || commands.isEmpty()) {
        throw new DockerException("Job has no script steps: " + safe(job.getName()));
      }

      for (String command : commands) {
        if (command == null || command.isBlank()) {
          continue;
        }
        StepResult stepResult = execStep(containerId, command);
        output.append(stepResult.output());
        lastExitCode = stepResult.exitCode();
        if (lastExitCode != 0) {
          break;
        }
      }

      Duration duration = Duration.between(start, Instant.now());
      JobStatus status = (lastExitCode == 0) ? JobStatus.COMPLETED : JobStatus.FAILED;

      if (status == JobStatus.FAILED) {
        logger.error("Job '{}' failed with exit code: {}", job.getName(), lastExitCode);
        logger.error("Job '{}' output:\n{}", job.getName(), output.toString());
      } else {
        logger.info("Job '{}' completed successfully", job.getName());
        logger.debug("Job '{}' output:\n{}", job.getName(), output.toString());
      }

      return JobResult.builder()
          .jobName(job.getName())
          .status(status)
          .output(output.toString())
          .exitCode(lastExitCode)
          .executionTime(duration)
          .build();

    } catch (NotFoundException e) {
      output.append("\nError: Docker image not found or pull failed: ").append(e.getMessage());
      Duration duration = Duration.between(start, Instant.now());
      logger.error("Job '{}' failed - image not found: {}", job.getName(), e.getMessage());
      logger.error("Job '{}' output:\n{}", job.getName(), output.toString());
      return JobResult.builder()
          .jobName(job.getName())
          .status(JobStatus.FAILED)
          .output(output.toString())
          .exitCode(1)
          .executionTime(duration)
          .build();

    } catch (DockerClientException | DockerException e) {
      output.append("\nError: ").append(e.getMessage());
      Duration duration = Duration.between(start, Instant.now());
      logger.error("Job '{}' failed - Docker error: {}", job.getName(), e.getMessage());
      logger.error("Job '{}' output:\n{}", job.getName(), output.toString());
      return JobResult.builder()
          .jobName(job.getName())
          .status(JobStatus.FAILED)
          .output(output.toString())
          .exitCode(1)
          .executionTime(duration)
          .build();

    } catch (Exception e) {
      output.append("\nError: ").append(e.getMessage());
      Duration duration = Duration.between(start, Instant.now());
      logger.error("Job '{}' failed - unexpected error: {}", job.getName(), e.getMessage(), e);
      logger.error("Job '{}' output:\n{}", job.getName(), output.toString());
      return JobResult.builder()
          .jobName(job.getName())
          .status(JobStatus.FAILED)
          .output(output.toString())
          .exitCode(1)
          .executionTime(duration)
          .build();

    } finally {
      cleanupContainer(containerId);
    }
  }

  /**
   * Pulls the given image if it is not present locally.
   *
   * @param image the image name
   * @param output output sink for diagnostic messages
   */
  private void pullImageIfNeeded(String image, StringBuilder output) throws InterruptedException {
    try {
      docker.inspectImageCmd(image).exec();
      return;
    } catch (NotFoundException ignored) {
      // Fall through to pull.
    }

    output.append("Pulling image: ").append(image).append("\n");
    docker.pullImageCmd(image)
        .exec(new ResultCallback.Adapter<PullResponseItem>() {})
        .awaitCompletion();
  }

  /**
   * Creates and starts a container for a job image and mounts the workspace.
   *
   * @param image the docker image
   * @param workspacePath the host workspace path
   * @return the started container ID
   */
  private String createAndStartContainer(String image, Path workspacePath) {
    HostConfig hostConfig =
        HostConfig.newHostConfig()
            .withBinds(new Bind(workspacePath.toAbsolutePath().toString(), WORKSPACE_VOLUME));

    CreateContainerResponse container =
        docker.createContainerCmd(image)
            .withHostConfig(hostConfig)
            .withWorkingDir(WORKSPACE_CONTAINER_PATH)
            .withEntrypoint("/bin/sh")
            .withCmd("-c", "while true; do sleep 3600; done")
            .withTty(true)
            .exec();

    String containerId = container.getId();
    docker.startContainerCmd(containerId).exec();

    // Wait a moment for container to be ready
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Verify container is running
    InspectContainerResponse inspect = docker.inspectContainerCmd(containerId).exec();
    Boolean running = inspect.getState().getRunning();
    logger.debug("Container {} running: {}", containerId, running);

    if (running == null || !running) {
      String error = inspect.getState().getError();
      throw new DockerException("Container failed to start. Error: " + error);
    }

    return containerId;
  }

  /**
   * Executes a single shell command inside the running container and captures output.
   *
   * @param containerId the container ID
   * @param command the shell command
   * @return step output and exit code
   */
  private StepResult execStep(String containerId, String command) throws InterruptedException {
    String header = "$ " + command.replace("\n", "\\n") + "\n";

    String execId =
        docker.execCreateCmd(containerId)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withCmd("sh", "-c", command)
            .exec()
            .getId();

    OutputCollector callback = new OutputCollector();
    docker.execStartCmd(execId).exec(callback).awaitCompletion(10, TimeUnit.MINUTES);

    InspectExecResponse inspected = docker.inspectExecCmd(execId).exec();
    Long exit = inspected.getExitCodeLong();
    int exitCode = (exit == null) ? 1 : exit.intValue();

    String body = callback.getCombinedOutput();
    String full = header + body + (body.endsWith("\n") || body.isEmpty() ? "" : "\n");

    return new StepResult(full, exitCode);
  }

  /**
   * Cleans up a container by stopping and removing it.
   *
   * @param containerId the container ID, may be null
   */
  private void cleanupContainer(String containerId) {
    if (containerId == null || containerId.isBlank()) {
      return;
    }
    try {
      docker.stopContainerCmd(containerId).withTimeout(1).exec();
    } catch (Exception ignored) {
      // Best-effort cleanup.
    }
    try {
      docker.removeContainerCmd(containerId).withForce(true).exec();
    } catch (Exception ignored) {
      // Best-effort cleanup.
    }
  }

  /**
   * Returns the provided value if non-blank; otherwise throws.
   *
   * @param value the value to check
   * @param fieldName field name for error message
   * @return the original value
   */
  private static String requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new DockerException("Missing required field: " + fieldName);
    }
    return value;
  }

  private static String safe(String value) {
    return value == null ? "<unknown>" : value;
  }

  /**
   * Value object for a single step execution result.
   *
   * @param output combined stdout/stderr output
   * @param exitCode exit code
   */
  private record StepResult(String output, int exitCode) { }

  /**
   * Collects Docker exec output frames into a string and streams each line to SLF4J
   * in real-time. The MDC context from the calling thread (pipeline, run_no, stage,
   * job) is captured at construction time and restored in {@code onNext()} so that
   * every log event carries the full job context even though Docker SDK callbacks
   * run on a separate thread. A {@code source=container} field is added to
   * distinguish container output from service logs.
   */
  private final class OutputCollector
      extends com.github.dockerjava.api.async.ResultCallbackTemplate<OutputCollector,
      com.github.dockerjava.api.model.Frame> {

    private final StringBuilder sb = new StringBuilder();
    private final java.util.Map<String, String> callerMdc;

    OutputCollector() {
      java.util.Map<String, String> ctx = MDC.getCopyOfContextMap();
      this.callerMdc = (ctx != null) ? ctx : java.util.Collections.emptyMap();
    }

    @Override
    public void onNext(com.github.dockerjava.api.model.Frame frame) {
      if (frame == null || frame.getPayload() == null) {
        return;
      }
      String text = new String(frame.getPayload(), StandardCharsets.UTF_8);
      sb.append(text);

      MDC.setContextMap(callerMdc);
      MDC.put("source", "container");
      try {
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
          if (!line.isEmpty()) {
            logger.info("{}", line);
          }
        }
      } finally {
        MDC.clear();
      }
    }

    /**
     * Returns the combined output captured so far.
     *
     * @return combined output
     */
    public String getCombinedOutput() {
      return sb.toString();
    }
  }
}
