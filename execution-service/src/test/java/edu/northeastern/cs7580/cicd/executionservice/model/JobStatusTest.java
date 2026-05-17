package edu.northeastern.cs7580.cicd.executionservice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JobStatus} enum.
 */
class JobStatusTest {

  @Test
  void testEnumValues() {
    // Assert all enum values exist
    JobStatus[] values = JobStatus.values();

    assertEquals(5, values.length);
    assertEquals(JobStatus.PENDING, values[0]);
    assertEquals(JobStatus.RUNNING, values[1]);
    assertEquals(JobStatus.COMPLETED, values[2]);
    assertEquals(JobStatus.FAILED, values[3]);
    assertEquals(JobStatus.SKIPPED, values[4]);
  }

  @Test
  void testValueOf() {
    // Test valueOf for each status
    assertEquals(JobStatus.PENDING, JobStatus.valueOf("PENDING"));
    assertEquals(JobStatus.RUNNING, JobStatus.valueOf("RUNNING"));
    assertEquals(JobStatus.COMPLETED, JobStatus.valueOf("COMPLETED"));
    assertEquals(JobStatus.FAILED, JobStatus.valueOf("FAILED"));
    assertEquals(JobStatus.SKIPPED, JobStatus.valueOf("SKIPPED"));
  }

  @Test
  void testValueOf_invalidValue() {
    // Assert that invalid value throws exception
    assertThrows(IllegalArgumentException.class, () -> {
      JobStatus.valueOf("INVALID");
    });
  }

  @Test
  void testEnumEquality() {
    // Test enum equality
    JobStatus status1 = JobStatus.COMPLETED;
    JobStatus status2 = JobStatus.COMPLETED;
    JobStatus status3 = JobStatus.FAILED;

    assertEquals(status1, status2);
    assertNotEquals(status1, status3);
    assertSame(status1, status2); // Enums are singletons
  }

  @Test
  void testEnumToString() {
    // Test toString returns enum name
    assertEquals("COMPLETED", JobStatus.COMPLETED.toString());
    assertEquals("FAILED", JobStatus.FAILED.toString());
    assertEquals("SKIPPED", JobStatus.SKIPPED.toString());
    assertEquals("PENDING", JobStatus.PENDING.toString());
    assertEquals("RUNNING", JobStatus.RUNNING.toString());
  }

  @Test
  void testEnumInSwitch() {
    // Test that enum can be used in switch statements
    JobStatus status = JobStatus.COMPLETED;
    String result;

    switch (status) {
      case PENDING:
        result = "pending";
        break;
      case RUNNING:
        result = "running";
        break;
      case COMPLETED:
        result = "completed";
        break;
      case FAILED:
        result = "failed";
        break;
      case SKIPPED:
        result = "skipped";
        break;
      default:
        result = "unknown";
    }

    assertEquals("completed", result);
  }

  @Test
  void testEnumComparison() {
    // Test ordinal comparison
    assertTrue(JobStatus.PENDING.ordinal() < JobStatus.RUNNING.ordinal());
    assertTrue(JobStatus.RUNNING.ordinal() < JobStatus.COMPLETED.ordinal());
    assertTrue(JobStatus.COMPLETED.ordinal() < JobStatus.FAILED.ordinal());
    assertTrue(JobStatus.FAILED.ordinal() < JobStatus.SKIPPED.ordinal());
  }
}