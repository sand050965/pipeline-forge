package edu.northeastern.cs7580.cicd.cli.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ReportNotFoundExceptionTest {

  @Test
  void constructor_shouldCreateInstance() {
    ReportNotFoundException ex = new ReportNotFoundException("not found");
    assertNotNull(ex);
  }

  @Test
  void getMessage_shouldReturnProvidedMessage() {
    String msg = "Not found: http://localhost:8080/api/v1/report/pipelines/default";
    ReportNotFoundException ex = new ReportNotFoundException(msg);
    assertEquals(msg, ex.getMessage());
  }

  @Test
  void shouldBeUnchecked() {
    ReportNotFoundException ex = new ReportNotFoundException("test");
    assertInstanceOf(RuntimeException.class, ex);
  }
}