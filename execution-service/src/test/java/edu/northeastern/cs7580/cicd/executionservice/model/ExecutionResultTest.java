package edu.northeastern.cs7580.cicd.executionservice.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionResultTest {

  @Test
  void builder_successfulExecution() {
    ExecutionResult result = ExecutionResult.builder()
        .jobResults(Arrays.asList(
            completedJob("build"), completedJob("test"), completedJob("deploy")))
        .success(true)
        .failedJobName(null)
        .runNumber(1)
        .build();

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getFailedJobName()).isNull();
    assertThat(result.getRunNumber()).isEqualTo(1);
    assertThat(result.getJobResults()).hasSize(3);
    assertThat(result.getJobResults())
        .allMatch(j -> j.getStatus() == JobStatus.COMPLETED);
  }

  @Test
  void builder_failedExecution() {
    ExecutionResult result = ExecutionResult.builder()
        .jobResults(Arrays.asList(
            completedJob("build"), failedJob("test"), skippedJob("deploy")))
        .success(false)
        .failedJobName("test")
        .runNumber(2)
        .build();

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getFailedJobName()).isEqualTo("test");
    assertThat(result.getRunNumber()).isEqualTo(2);
    assertThat(result.getJobResults().get(0).getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(result.getJobResults().get(1).getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(result.getJobResults().get(2).getStatus()).isEqualTo(JobStatus.SKIPPED);
  }

  @Test
  void builder_singleJobSuccess() {
    ExecutionResult result = ExecutionResult.builder()
        .jobResults(Collections.singletonList(completedJob("build")))
        .success(true)
        .failedJobName(null)
        .runNumber(1)
        .build();

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getJobResults()).hasSize(1);
  }

  @Test
  void builder_emptyJobList() {
    ExecutionResult result = ExecutionResult.builder()
        .jobResults(Collections.emptyList())
        .success(true)
        .failedJobName(null)
        .runNumber(1)
        .build();

    assertThat(result.getJobResults()).isNotNull().isEmpty();
  }

  @Test
  void builder_multipleSkippedJobs() {
    ExecutionResult result = ExecutionResult.builder()
        .jobResults(Arrays.asList(
            completedJob("build"), failedJob("test"),
            skippedJob("lint"), skippedJob("deploy")))
        .success(false)
        .failedJobName("test")
        .runNumber(3)
        .build();

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getFailedJobName()).isEqualTo("test");
    assertThat(result.getJobResults()).hasSize(4);
    assertThat(result.getJobResults().get(2).getStatus()).isEqualTo(JobStatus.SKIPPED);
    assertThat(result.getJobResults().get(3).getStatus()).isEqualTo(JobStatus.SKIPPED);
  }

  @Test
  void noArgsConstructor_shouldHaveDefaults() {
    ExecutionResult result = new ExecutionResult();

    assertThat(result.getJobResults()).isNull();
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getFailedJobName()).isNull();
    assertThat(result.getRunNumber()).isNull();
  }

  @Test
  void allArgsConstructor_shouldSetAllFields() {
    List<JobResult> jobs = Arrays.asList(completedJob("build"), completedJob("test"));

    // four args: jobResults, success, failedJobName, runNumber
    ExecutionResult result = new ExecutionResult(jobs, true, null, 5);

    assertThat(result.getJobResults()).isEqualTo(jobs);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getFailedJobName()).isNull();
    assertThat(result.getRunNumber()).isEqualTo(5);
  }

  @Test
  void setter_shouldUpdateFields() {
    ExecutionResult result = new ExecutionResult();
    List<JobResult> jobs = List.of(completedJob("test"));

    result.setJobResults(jobs);
    result.setSuccess(true);
    result.setFailedJobName(null);
    result.setRunNumber(10);

    assertThat(result.getJobResults()).isEqualTo(jobs);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getRunNumber()).isEqualTo(10);
  }

  @Test
  void equals_shouldBeEqualForSameValues() {
    List<JobResult> jobs = List.of(completedJob("test"));

    ExecutionResult result1 = ExecutionResult.builder()
        .jobResults(jobs).success(true).failedJobName(null).runNumber(1).build();
    ExecutionResult result2 = ExecutionResult.builder()
        .jobResults(jobs).success(true).failedJobName(null).runNumber(1).build();

    assertThat(result1).isEqualTo(result2);
    assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
  }

  @Test
  void equals_shouldNotBeEqualForDifferentRunNumber() {
    List<JobResult> jobs = List.of(completedJob("test"));

    ExecutionResult result1 = ExecutionResult.builder()
        .jobResults(jobs).success(true).runNumber(1).build();
    ExecutionResult result2 = ExecutionResult.builder()
        .jobResults(jobs).success(true).runNumber(2).build();

    assertThat(result1).isNotEqualTo(result2);
  }

  @Test
  void toString_shouldContainKeyFields() {
    ExecutionResult result = ExecutionResult.builder()
        .jobResults(List.of(completedJob("build")))
        .success(true).failedJobName(null).runNumber(1)
        .build();

    assertThat(result.toString()).contains("success=true");
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private JobResult completedJob(String name) {
    return JobResult.builder().jobName(name).status(JobStatus.COMPLETED)
        .output("Success").exitCode(0).executionTime(Duration.ofSeconds(10)).build();
  }

  private JobResult failedJob(String name) {
    return JobResult.builder().jobName(name).status(JobStatus.FAILED)
        .output("Failed").exitCode(1).executionTime(Duration.ofSeconds(5)).build();
  }

  private JobResult skippedJob(String name) {
    return JobResult.builder().jobName(name).status(JobStatus.SKIPPED)
        .output("Job skipped due to previous job failure").exitCode(0).build();
  }
}