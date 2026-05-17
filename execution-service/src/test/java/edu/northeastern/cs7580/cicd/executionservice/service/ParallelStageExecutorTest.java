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
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParallelStageExecutorTest {

  @Mock private DockerExecutor dockerExecutor;
  @Mock private ExecutionPersistenceService persistenceService;
  @Mock private Tracer tracer;
  @Mock private Span span;
  @Mock private Tracer.SpanInScope spanInScope;

  private ParallelStageExecutor executor;
  private MeterRegistry meterRegistry;

  private static final Path WORKSPACE = Path.of("/tmp/workspace");

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    executor = new ParallelStageExecutor(dockerExecutor, persistenceService, tracer, meterRegistry);

    when(tracer.nextSpan()).thenReturn(span);
    when(span.name(any())).thenReturn(span);
    when(span.tag(any(), any())).thenReturn(span);
    when(span.start()).thenReturn(span);
    when(tracer.withSpan(span)).thenReturn(spanInScope);

    when(persistenceService.markJobRunning(any())).thenReturn(Mono.empty());
    when(persistenceService.updateJobRun(any(), any())).thenReturn(Mono.empty());
  }

  // ---------------------------------------------------------------------------
  // computeWaves — pure unit tests, no mocks needed
  // ---------------------------------------------------------------------------

  @Test
  void computeWaves_singleJobNoDeps_returnsOneWaveWithThatJob() {
    Job a = job("a", null);

    List<List<Job>> waves = executor.computeWaves(List.of(a), Map.of("a", List.of()));

    assertThat(waves).hasSize(1);
    assertThat(waves.get(0)).containsExactly(a);
  }

  @Test
  void computeWaves_threeIndependentJobs_returnsOneWaveWithAllThree() {
    Job a = job("a", null);
    Job b = job("b", null);
    Job c = job("c", null);
    Map<String, List<String>> deps = Map.of("a", List.of(), "b", List.of(), "c", List.of());

    List<List<Job>> waves = executor.computeWaves(List.of(a, b, c), deps);

    assertThat(waves).hasSize(1);
    assertThat(waves.get(0)).containsExactlyInAnyOrder(a, b, c);
  }

  @Test
  void computeWaves_chain_eachJobInSeparateWave() {
    // A → B → C: wave 0 = [A], wave 1 = [B], wave 2 = [C]
    Job a = job("a", null);
    Job b = job("b", List.of("a"));
    Job c = job("c", List.of("b"));
    Map<String, List<String>> deps = Map.of(
        "a", List.of(),
        "b", List.of("a"),
        "c", List.of("b"));

    List<List<Job>> waves = executor.computeWaves(List.of(a, b, c), deps);

    assertThat(waves).hasSize(3);
    assertThat(waves.get(0)).containsExactly(a);
    assertThat(waves.get(1)).containsExactly(b);
    assertThat(waves.get(2)).containsExactly(c);
  }

  @Test
  void computeWaves_diamond_middleJobsInSameWave() {
    // A → B, A → C, B+C → D: wave 0 = [A], wave 1 = [B, C], wave 2 = [D]
    Job a = job("a", null);
    Job b = job("b", List.of("a"));
    Job c = job("c", List.of("a"));
    Job d = job("d", List.of("b", "c"));
    Map<String, List<String>> deps = Map.of(
        "a", List.of(),
        "b", List.of("a"),
        "c", List.of("a"),
        "d", List.of("b", "c"));

    List<List<Job>> waves = executor.computeWaves(List.of(a, b, c, d), deps);

    assertThat(waves).hasSize(3);
    assertThat(waves.get(0)).containsExactly(a);
    assertThat(waves.get(1)).containsExactlyInAnyOrder(b, c);
    assertThat(waves.get(2)).containsExactly(d);
  }

  @Test
  void computeWaves_mixedDepsAndIndependent_correctWaves() {
    // E has no deps, A → B, so: wave 0 = [E, A], wave 1 = [B]
    Job e = job("e", null);
    Job a = job("a", null);
    Job b = job("b", List.of("a"));
    Map<String, List<String>> deps = Map.of(
        "e", List.of(),
        "a", List.of(),
        "b", List.of("a"));

    List<List<Job>> waves = executor.computeWaves(List.of(e, a, b), deps);

    assertThat(waves).hasSize(2);
    assertThat(waves.get(0)).containsExactlyInAnyOrder(e, a);
    assertThat(waves.get(1)).containsExactly(b);
  }

  @Test
  void computeWaves_nullDependencyMap_allJobsInWaveZero() {
    Job a = job("a", null);
    Job b = job("b", List.of("a")); // has needs but dep map is null

    List<List<Job>> waves = executor.computeWaves(List.of(a, b), null);

    // null dep map → no in-stage deps recognised → both in wave 0
    assertThat(waves).hasSize(1);
    assertThat(waves.get(0)).containsExactlyInAnyOrder(a, b);
  }

  @Test
  void computeWaves_emptyJobList_returnsEmptyList() {
    List<List<Job>> waves = executor.computeWaves(List.of(), Map.of());

    assertThat(waves).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // executeStage — integration-style tests with mocked DockerExecutor
  // ---------------------------------------------------------------------------

  @Test
  void executeStage_singleJobSucceeds_returnsCompletedResult() {
    Job a = job("a", null);
    when(dockerExecutor.executeJob(eq(a), eq(WORKSPACE))).thenReturn(completed("a"));

    List<JobResult> results = executor.executeStage(
        stage("build", a),
        Map.of("a", List.of()),
        WORKSPACE,
        ids(a),
        new ConcurrentHashMap<>(),
        "my-pipeline", 1);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(results.get(0).getJobName()).isEqualTo("a");
  }

  @Test
  void executeStage_threeIndependentJobs_allComplete() {
    Job a = job("a", null);
    Job b = job("b", null);
    Job c = job("c", null);
    when(dockerExecutor.executeJob(eq(a), eq(WORKSPACE))).thenReturn(completed("a"));
    when(dockerExecutor.executeJob(eq(b), eq(WORKSPACE))).thenReturn(completed("b"));
    when(dockerExecutor.executeJob(eq(c), eq(WORKSPACE))).thenReturn(completed("c"));

    List<JobResult> results = executor.executeStage(
        stage("build", a, b, c),
        Map.of("a", List.of(), "b", List.of(), "c", List.of()),
        WORKSPACE,
        ids(a, b, c),
        new ConcurrentHashMap<>(),
        "my-pipeline", 1);

    assertThat(results).hasSize(3);
    assertThat(results).extracting(JobResult::getStatus)
        .containsOnly(JobStatus.COMPLETED);
    verify(dockerExecutor).executeJob(eq(a), eq(WORKSPACE));
    verify(dockerExecutor).executeJob(eq(b), eq(WORKSPACE));
    verify(dockerExecutor).executeJob(eq(c), eq(WORKSPACE));
  }

  @Test
  void executeStage_failedJobSkipsDependentButNotIndependent() {
    // A fails; B depends on A (should be skipped); C is independent (should run)
    Job a = job("a", null);
    Job b = job("b", List.of("a"));
    Job c = job("c", null);
    when(dockerExecutor.executeJob(eq(a), eq(WORKSPACE))).thenReturn(failed("a"));
    when(dockerExecutor.executeJob(eq(c), eq(WORKSPACE))).thenReturn(completed("c"));

    List<JobResult> results = executor.executeStage(
        stage("build", a, b, c),
        Map.of("a", List.of(), "b", List.of("a"), "c", List.of()),
        WORKSPACE,
        ids(a, b, c),
        new ConcurrentHashMap<>(),
        "my-pipeline", 1);

    assertThat(results).hasSize(3);
    assertThat(jobStatus(results, "a")).isEqualTo(JobStatus.FAILED);
    assertThat(jobStatus(results, "b")).isEqualTo(JobStatus.SKIPPED);
    assertThat(jobStatus(results, "c")).isEqualTo(JobStatus.COMPLETED);
    verify(dockerExecutor, never()).executeJob(eq(b), any());
    verify(dockerExecutor).executeJob(eq(c), eq(WORKSPACE));
  }

  @Test
  void executeStage_failurePropagatesThroughMultipleWaves() {
    // A → B → C: A fails, B skipped, C skipped
    Job a = job("a", null);
    Job b = job("b", List.of("a"));
    Job c = job("c", List.of("b"));
    when(dockerExecutor.executeJob(eq(a), eq(WORKSPACE))).thenReturn(failed("a"));

    List<JobResult> results = executor.executeStage(
        stage("build", a, b, c),
        Map.of("a", List.of(), "b", List.of("a"), "c", List.of("b")),
        WORKSPACE,
        ids(a, b, c),
        new ConcurrentHashMap<>(),
        "my-pipeline", 1);

    assertThat(results).hasSize(3);
    assertThat(jobStatus(results, "a")).isEqualTo(JobStatus.FAILED);
    assertThat(jobStatus(results, "b")).isEqualTo(JobStatus.SKIPPED);
    assertThat(jobStatus(results, "c")).isEqualTo(JobStatus.SKIPPED);
    verify(dockerExecutor, never()).executeJob(eq(b), any());
    verify(dockerExecutor, never()).executeJob(eq(c), any());
  }

  @Test
  void executeStage_failuresTrue_dependentJobStillRuns() {
    // A has failures: true and fails; B depends on A and should still run
    Job a = jobWithFailuresTrue("a", null);
    Job b = job("b", List.of("a"));
    when(dockerExecutor.executeJob(eq(a), eq(WORKSPACE))).thenReturn(failed("a"));
    when(dockerExecutor.executeJob(eq(b), eq(WORKSPACE))).thenReturn(completed("b"));

    List<JobResult> results = executor.executeStage(
        stage("build", a, b),
        Map.of("a", List.of(), "b", List.of("a")),
        WORKSPACE,
        ids(a, b),
        new ConcurrentHashMap<>(),
        "my-pipeline", 1);

    assertThat(results).hasSize(2);
    assertThat(jobStatus(results, "a")).isEqualTo(JobStatus.FAILED);
    assertThat(jobStatus(results, "b")).isEqualTo(JobStatus.COMPLETED);
    verify(dockerExecutor).executeJob(eq(b), eq(WORKSPACE));
  }

  @Test
  void executeStage_failuresTrue_doesNotBlockFurtherDownstream() {
    // A (failures:true) fails; B needs A and runs; C needs B and should run if B succeeds
    Job a = jobWithFailuresTrue("a", null);
    Job b = job("b", List.of("a"));
    Job c = job("c", List.of("b"));
    when(dockerExecutor.executeJob(eq(a), eq(WORKSPACE))).thenReturn(failed("a"));
    when(dockerExecutor.executeJob(eq(b), eq(WORKSPACE))).thenReturn(completed("b"));
    when(dockerExecutor.executeJob(eq(c), eq(WORKSPACE))).thenReturn(completed("c"));

    List<JobResult> results = executor.executeStage(
        stage("build", a, b, c),
        Map.of("a", List.of(), "b", List.of("a"), "c", List.of("b")),
        WORKSPACE,
        ids(a, b, c),
        new ConcurrentHashMap<>(),
        "my-pipeline", 1);

    assertThat(results).hasSize(3);
    assertThat(jobStatus(results, "a")).isEqualTo(JobStatus.FAILED);
    assertThat(jobStatus(results, "b")).isEqualTo(JobStatus.COMPLETED);
    assertThat(jobStatus(results, "c")).isEqualTo(JobStatus.COMPLETED);
  }

  @Test
  void executeStage_diamond_allJobsComplete() {
    // A → B+C (parallel) → D
    Job a = job("a", null);
    Job b = job("b", List.of("a"));
    Job c = job("c", List.of("a"));
    Job d = job("d", List.of("b", "c"));
    when(dockerExecutor.executeJob(eq(a), eq(WORKSPACE))).thenReturn(completed("a"));
    when(dockerExecutor.executeJob(eq(b), eq(WORKSPACE))).thenReturn(completed("b"));
    when(dockerExecutor.executeJob(eq(c), eq(WORKSPACE))).thenReturn(completed("c"));
    when(dockerExecutor.executeJob(eq(d), eq(WORKSPACE))).thenReturn(completed("d"));

    List<JobResult> results = executor.executeStage(
        stage("build", a, b, c, d),
        Map.of("a", List.of(), "b", List.of("a"), "c", List.of("a"), "d", List.of("b", "c")),
        WORKSPACE,
        ids(a, b, c, d),
        new ConcurrentHashMap<>(),
        "my-pipeline", 1);

    assertThat(results).hasSize(4);
    assertThat(results).extracting(JobResult::getStatus)
        .containsOnly(JobStatus.COMPLETED);
    verify(dockerExecutor).executeJob(eq(d), eq(WORKSPACE));
  }

  @Test
  void executeStage_diamond_oneMiddleJobFails_dSkipped_otherMiddleJobRuns() {
    // A → B+C; B fails; C succeeds; D (needs B and C) should be skipped
    Job a = job("a", null);
    Job b = job("b", List.of("a"));
    Job c = job("c", List.of("a"));
    Job d = job("d", List.of("b", "c"));
    when(dockerExecutor.executeJob(eq(a), eq(WORKSPACE))).thenReturn(completed("a"));
    when(dockerExecutor.executeJob(eq(b), eq(WORKSPACE))).thenReturn(failed("b"));
    when(dockerExecutor.executeJob(eq(c), eq(WORKSPACE))).thenReturn(completed("c"));

    List<JobResult> results = executor.executeStage(
        stage("build", a, b, c, d),
        Map.of("a", List.of(), "b", List.of("a"), "c", List.of("a"), "d", List.of("b", "c")),
        WORKSPACE,
        ids(a, b, c, d),
        new ConcurrentHashMap<>(),
        "my-pipeline", 1);

    assertThat(results).hasSize(4);
    assertThat(jobStatus(results, "a")).isEqualTo(JobStatus.COMPLETED);
    assertThat(jobStatus(results, "b")).isEqualTo(JobStatus.FAILED);
    assertThat(jobStatus(results, "c")).isEqualTo(JobStatus.COMPLETED);
    assertThat(jobStatus(results, "d")).isEqualTo(JobStatus.SKIPPED);
    verify(dockerExecutor).executeJob(eq(c), eq(WORKSPACE));
    verify(dockerExecutor, never()).executeJob(eq(d), any());
  }

  @Test
  void executeStage_statusMapUpdatedWithResults() {
    // Verify statusMap is populated after execution so cross-stage logic can use it
    Job a = job("a", null);
    Job b = job("b", null);
    when(dockerExecutor.executeJob(eq(a), eq(WORKSPACE))).thenReturn(completed("a"));
    when(dockerExecutor.executeJob(eq(b), eq(WORKSPACE))).thenReturn(failed("b"));

    Map<String, JobStatus> statusMap = new ConcurrentHashMap<>();
    executor.executeStage(
        stage("build", a, b),
        Map.of("a", List.of(), "b", List.of()),
        WORKSPACE,
        ids(a, b),
        statusMap,
        "my-pipeline", 1);

    assertThat(statusMap).containsEntry("a", JobStatus.COMPLETED);
    assertThat(statusMap).containsEntry("b", JobStatus.FAILED);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Job job(String name, List<String> needs) {
    return Job.builder()
        .name(name)
        .stage("build")
        .image("alpine:latest")
        .script("echo " + name)
        .needs(needs)
        .build();
  }

  private Job jobWithFailuresTrue(String name, List<String> needs) {
    return Job.builder()
        .name(name)
        .stage("build")
        .image("alpine:latest")
        .script("echo " + name)
        .failures(true)
        .needs(needs)
        .build();
  }

  private StageExecution stage(String stageName, Job... jobs) {
    return StageExecution.builder()
        .stageName(stageName)
        .jobs(List.of(jobs))
        .build();
  }

  private Map<String, Long> ids(Job... jobs) {
    Map<String, Long> map = new HashMap<>();
    map.put("__stageRunId__", 99L);
    long id = 1L;
    for (Job job : jobs) {
      map.put(job.getName(), id++);
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

  private JobStatus jobStatus(List<JobResult> results, String jobName) {
    return results.stream()
        .filter(r -> r.getJobName().equals(jobName))
        .findFirst()
        .map(JobResult::getStatus)
        .orElseThrow(() -> new AssertionError("No result found for job: " + jobName));
  }
}
