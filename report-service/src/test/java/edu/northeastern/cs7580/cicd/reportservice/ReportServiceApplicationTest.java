package edu.northeastern.cs7580.cicd.reportservice;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ReportServiceApplicationTest {

  @Test
  void main_classExists() {
    ReportServiceApplication app = new ReportServiceApplication();
    assertNotNull(app);
  }
}
