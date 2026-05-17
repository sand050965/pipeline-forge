package edu.northeastern.cs7580.cicd.executionservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.executionservice.executor.DockerExecutor;
import edu.northeastern.cs7580.cicd.executionservice.model.JobResult;
import edu.northeastern.cs7580.cicd.executionservice.model.JobStatus;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineServiceFactory;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

/**
 * Integration tests for parallel execution using realistic YAML pipeline fixtures.
 *
 * <p>Unlike {@link ParallelStageExecutorTest}, which builds {@link Job} and
 * {@link StageExecution} objects programmatically, these tests load pipeline
 * configurations from YAML files via {@link PipelineServiceFactory}, exercising
 * the full parse → validate → plan → execute path.
 *
 * <p>A mocked {@link DockerExecutor} is used so no running Docker daemon is required.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParallelExecutionIntegrationTest {

  @Mock private DockerExecutor dockerExecutor;
  @Mock private ExecutionPersistenceService persistenceService;
  @Mock private Tracer tracer;
  @Mock private Span span;
  @Mock private Tracer.SpanInScope spanInScope;

  private ParallelStageExecutor executor;
  private PipelineService pipelineService;

  private static final Path WORKSPACE = Path.of("/tmp/workspace");

  @BeforeEach
  void setUp() {
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    executor = new ParallelStageExecutor(dockerExecutor, persistenceService, tracer, meterRegistry);
    pipelineService = PipelineServiceFactory.create();

    when(tracer.nextSpan()).thenReturn(span);
    when(span.name(any())).thenReturn(span);
    when(span.tag(any(), any())).thenReturn(span);
    when(span.start()).thenReturn(span);
    when(tracer.withSpan(span)).thenReturn(spanInScope);

    when(persistenceService.markJobRunning(any())).thenReturn(Mono.empty());
    when(persistenceService.updateJobRun(any(), any())).thenReturn(Mono.empty());
  }

  // ---------------------------------------------------------------------------
  // parallel-independent.yaml — 3 jobs, no needs
  // ---------------------------------------------------------------------------

  @Test
  void parallelIndependent_allThreeJobsComplete() throws Exception {
    ExecutionPlan plan = loadPlan("pipelines/parallel-independent.yaml");
    StageExecution stage = plan.getStages().get(0);

    for (Job job : stage.getJobs()) {
      when(dockerExecutor.executeJob(eq(job), any())).thenReturn(completed(job.getName()));
    }

    List<JobResult> results = executor.executeStage(
        stage, plan.getJobDependencies(), WORKSPACE,
        ids(stage), new ConcurrentHashMap<>(), "parallel-independent", 1);

    assertThat(results).hasSize(3);
    assertThat(results).extracting(JobResult::getStatus).containsOnly(JobStatus.COMPLETED);
    for (Job job : stage.getJobs()) {
      verify(dockerExecutor).executeJob(eq(job), any());
    }
  }

  // ---------------------------------------------------------------------------
  // parallel-diamond.yaml — A → B+C (parallel) → D
  // ---------------------------------------------------------------------------

  @Test
  void parallelDiamond_allJobsComplete() throws Exception {
    ExecutionPlan plan = loadPlan("pipelines/parallel-diamond.yaml");
    StageExecution stage = plan.getStages().get(0);
    Map<String, Job> byName = byName(stage);

    when(dockerExecutor.executeJob(eq(byName.get("job-a")), any())).thenReturn(completed("job-a"));
    when(dockerExecutor.executeJob(eq(byName.get("job-b")), any())).thenReturn(completed("job-b"));
    when(dockerExecutor.executeJob(eq(byName.get("job-c")), any())).thenReturn(completed("job-c"));
    when(dockerExecutor.executeJob(eq(byName.get("job-d")), any())).thenReturn(completed("job-d"));

    List<JobResult> results = executor.executeStage(
        stage, plan.getJobDependencies(), WORKSPACE,
        ids(stage), new ConcurrentHashMap<>(), "parallel-diamond", 1);

    assertThat(results).hasSize(4);
    assertThat(results).extracting(JobResult::getStatus).containsOnly(JobStatus.COMPLETED);
    verify(dockerExecutor).executeJob(eq(byName.get("job-d")), any());
  }

  @Test
  void parallelDiamond_bFails_cCompletes_dSkipped() throws Exception {
    ExecutionPlan plan = loadPlan("pipelines/parallel-diamond.yaml");
    StageExecution stage = plan.getStages().get(0);
    Map<String, Job> byName = byName(stage);

    when(dockerExecutor.executeJob(eq(byName.get("job-a")), any())).thenReturn(completed("job-a"));
    when(dockerExecutor.executeJob(eq(byName.get("job-b")), any())).thenReturn(failed("job-b"));
    when(dockerExecutor.executeJob(eq(byName.get("job-c")), any())).thenReturn(completed("job-c"));

    List<JobResult> results = executor.executeStage(
        stage, plan.getJobDependencies(), WORKSPACE,
        ids(stage), new ConcurrentHashMap<>(), "parallel-diamond", 1);

    assertThat(results).hasSize(4);
    assertThat(status(results, "job-a")).isEqualTo(JobStatus.COMPLETED);
    assertThat(status(results, "job-b")).isEqualTo(JobStatus.FAILED);
    assertThat(status(results, "job-c")).isEqualTo(JobStatus.COMPLETED);
    assertThat(status(results, "job-d")).isEqualTo(JobStatus.SKIPPED);
    verify(dockerExecutor).executeJob(eq(byName.get("job-c")), any());
    verify(dockerExecutor, never()).executeJob(eq(byName.get("job-d")), any());
  }

  // ---------------------------------------------------------------------------
  // parallel-failure.yaml — B and C independent, B fails, D (needs B) skipped
  // ---------------------------------------------------------------------------

  @Test
  void parallelFailure_cCompletes_dSkipped() throws Exception {
    ExecutionPlan plan = loadPlan("pipelines/parallel-failure.yaml");
    StageExecution stage = plan.getStages().get(0);
    Map<String, Job> byName = byName(stage);

    when(dockerExecutor.executeJob(eq(byName.get("job-b")), any())).thenReturn(failed("job-b"));
    when(dockerExecutor.executeJob(eq(byName.get("job-c")), any())).thenReturn(completed("job-c"));

    List<JobResult> results = executor.executeStage(
        stage, plan.getJobDependencies(), WORKSPACE,
        ids(stage), new ConcurrentHashMap<>(), "parallel-failure", 1);

    assertThat(results).hasSize(3);
    assertThat(status(results, "job-b")).isEqualTo(JobStatus.FAILED);
    assertThat(status(results, "job-c")).isEqualTo(JobStatus.COMPLETED);
    assertThat(status(results, "job-d")).isEqualTo(JobStatus.SKIPPED);
    verify(dockerExecutor).executeJob(eq(byName.get("job-c")), any());
    verify(dockerExecutor, never()).executeJob(eq(byName.get("job-d")), any());
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ExecutionPlan loadPlan(String classpathResource) throws Exception {
    URL url = getClass().getClassLoader().getResource(classpathResource);
    assertThat(url).as("Test resource not found: " + classpathResource).isNotNull();
    return pipelineService.createExecutionPlan(Path.of(url.toURI()));
  }

  private Map<String, Long> ids(StageExecution stage) {
    Map<String, Long> map = new HashMap<>();
    map.put("__stageRunId__", 99L);
    long id = 1L;
    for (Job job : stage.getJobs()) {
      map.put(job.getName(), id++);
    }
    return map;
  }

  private Map<String, Job> byName(StageExecution stage) {
    Map<String, Job> map = new HashMap<>();
    for (Job job : stage.getJobs()) {
      map.put(job.getName(), job);
    }
    return map;
  }

  private JobResult completed(String name) {
    return JobResult.builder()
        .jobName(name)
        .status(JobStatus.COMPLETED)
        .output("ok")
        .exitCode(0)
        .executionTime(Duration.ofMillis(100))
        .build();
  }

  private JobResult failed(String name) {
    return JobResult.builder()
        .jobName(name)
        .status(JobStatus.FAILED)
        .output("error")
        .exitCode(1)
        .executionTime(Duration.ofMillis(100))
        .build();
  }

  private JobStatus status(List<JobResult> results, String jobName) {
    return results.stream()
        .filter(r -> r.getJobName().equals(jobName))
        .findFirst()
        .map(JobResult::getStatus)
        .orElseThrow(() -> new AssertionError("No result for job: " + jobName));
  }
}
