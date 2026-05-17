package edu.northeastern.cs7580.cicd.executionservice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JobResultTest {

  @Test
  void testBuilder_completedJob() {
    // Arrange & Act
    JobResult result = JobResult.builder()
        .jobName("build")
        .status(JobStatus.COMPLETED)
        .output("Build successful")
        .exitCode(0)
        .executionTime(Duration.ofSeconds(45))
        .build();

    // Assert
    assertEquals("build", result.getJobName());
    assertEquals(JobStatus.COMPLETED, result.getStatus());
    assertEquals("Build successful", result.getOutput());
    assertEquals(0, result.getExitCode());
    assertEquals(Duration.ofSeconds(45), result.getExecutionTime());
  }

  @Test
  void testBuilder_failedJob() {
    // Arrange & Act
    JobResult result = JobResult.builder()
        .jobName("test")
        .status(JobStatus.FAILED)
        .output("Tests failed: 3 failures")
        .exitCode(1)
        .executionTime(Duration.ofMinutes(2))
        .build();

    // Assert
    assertEquals("test", result.getJobName());
    assertEquals(JobStatus.FAILED, result.getStatus());
    assertEquals("Tests failed: 3 failures", result.getOutput());
    assertEquals(1, result.getExitCode());
    assertEquals(Duration.ofMinutes(2), result.getExecutionTime());
  }

  @Test
  void testBuilder_skippedJob() {
    // Arrange & Act
    JobResult result = JobResult.builder()
        .jobName("deploy")
        .status(JobStatus.SKIPPED)
        .output("Job skipped due to previous job failure")
        .exitCode(0)
        .executionTime(null)
        .build();

    // Assert
    assertEquals("deploy", result.getJobName());
    assertEquals(JobStatus.SKIPPED, result.getStatus());
    assertEquals("Job skipped due to previous job failure", result.getOutput());
    assertEquals(0, result.getExitCode());
    assertNull(result.getExecutionTime());
  }

  @Test
  void testNoArgsConstructor() {
    // Act
    JobResult result = new JobResult();

    // Assert
    assertNull(result.getJobName());
    assertNull(result.getStatus());
    assertNull(result.getOutput());
    assertEquals(0, result.getExitCode());
    assertNull(result.getExecutionTime());
  }

  @Test
  void testAllArgsConstructor() {
    // Act
    JobResult result = new JobResult(
        "compile",
        JobStatus.COMPLETED,
        "Compilation successful",
        0,
        Duration.ofSeconds(30)
    );

    // Assert
    assertEquals("compile", result.getJobName());
    assertEquals(JobStatus.COMPLETED, result.getStatus());
    assertEquals("Compilation successful", result.getOutput());
    assertEquals(0, result.getExitCode());
    assertEquals(Duration.ofSeconds(30), result.getExecutionTime());
  }

  @Test
  void testSetters() {
    // Arrange
    JobResult result = new JobResult();

    // Act
    result.setJobName("lint");
    result.setStatus(JobStatus.COMPLETED);
    result.setOutput("Linting passed");
    result.setExitCode(0);
    result.setExecutionTime(Duration.ofSeconds(10));

    // Assert
    assertEquals("lint", result.getJobName());
    assertEquals(JobStatus.COMPLETED, result.getStatus());
    assertEquals("Linting passed", result.getOutput());
    assertEquals(0, result.getExitCode());
    assertEquals(Duration.ofSeconds(10), result.getExecutionTime());
  }

  @Test
  void testEquals_sameValues() {
    // Arrange
    JobResult result1 = JobResult.builder()
        .jobName("test")
        .status(JobStatus.COMPLETED)
        .output("Success")
        .exitCode(0)
        .executionTime(Duration.ofSeconds(5))
        .build();

    JobResult result2 = JobResult.builder()
        .jobName("test")
        .status(JobStatus.COMPLETED)
        .output("Success")
        .exitCode(0)
        .executionTime(Duration.ofSeconds(5))
        .build();

    // Assert
    assertEquals(result1, result2);
    assertEquals(result1.hashCode(), result2.hashCode());
  }

  @Test
  void testEquals_differentValues() {
    // Arrange
    JobResult result1 = JobResult.builder()
        .jobName("test")
        .status(JobStatus.COMPLETED)
        .exitCode(0)
        .build();

    JobResult result2 = JobResult.builder()
        .jobName("build")
        .status(JobStatus.FAILED)
        .exitCode(1)
        .build();

    // Assert
    assertNotEquals(result1, result2);
  }

  @Test
  void testToString() {
    // Arrange
    JobResult result = JobResult.builder()
        .jobName("build")
        .status(JobStatus.COMPLETED)
        .output("Success")
        .exitCode(0)
        .executionTime(Duration.ofSeconds(45))
        .build();

    // Act
    String toString = result.toString();

    // Assert
    assertTrue(toString.contains("build"));
    assertTrue(toString.contains("COMPLETED"));
    assertTrue(toString.contains("Success"));
  }

  @Test
  void testBuilder_withNullExecutionTime() {
    // Arrange & Act
    JobResult result = JobResult.builder()
        .jobName("test")
        .status(JobStatus.COMPLETED)
        .output("Success")
        .exitCode(0)
        .executionTime(null)
        .build();

    // Assert
    assertNull(result.getExecutionTime());
  }

  @Test
  void testBuilder_withEmptyOutput() {
    // Arrange & Act
    JobResult result = JobResult.builder()
        .jobName("test")
        .status(JobStatus.COMPLETED)
        .output("")
        .exitCode(0)
        .build();

    // Assert
    assertEquals("", result.getOutput());
  }

  @Test
  void testBuilder_withMultilineOutput() {
    // Arrange
    String multilineOutput = "Line 1\nLine 2\nLine 3";

    // Act
    JobResult result = JobResult.builder()
        .jobName("test")
        .status(JobStatus.COMPLETED)
        .output(multilineOutput)
        .exitCode(0)
        .build();

    // Assert
    assertEquals(multilineOutput, result.getOutput());
    assertTrue(result.getOutput().contains("\n"));
  }

  @Test
  void testBuilder_withNonZeroExitCode() {
    // Arrange & Act
    JobResult result = JobResult.builder()
        .jobName("test")
        .status(JobStatus.FAILED)
        .exitCode(127)
        .build();

    // Assert
    assertEquals(127, result.getExitCode());
    assertEquals(JobStatus.FAILED, result.getStatus());
  }
}