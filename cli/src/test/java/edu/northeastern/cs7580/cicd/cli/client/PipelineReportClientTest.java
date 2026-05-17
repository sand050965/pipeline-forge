package edu.northeastern.cs7580.cicd.cli.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.cs7580.cicd.cli.exception.ReportNotFoundException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineReportClientTest {

  /**
   * Minimal stub that returns an empty map for every method, used to verify
   * the interface contract without HTTP calls.
   */
  private static final class StubReportClient implements PipelineReportClient {

    @Override
    public Map<String, Object> getPipelineReport(String pipeline) {
      return Map.of("pipeline", Map.of("name", pipeline));
    }

    @Override
    public Map<String, Object> getRunReport(String pipeline, int run) {
      return Map.of("pipeline", Map.of("name", pipeline, "run-no", run));
    }

    @Override
    public Map<String, Object> getStageReport(String pipeline, int run, String stage) {
      return Map.of("pipeline", Map.of("name", pipeline, "run-no", run, "stage", stage));
    }

    @Override
    public Map<String, Object> getJobReport(String pipeline, int run, String stage, String job) {
      return Map.of("pipeline", Map.of("name", pipeline, "run-no", run, "stage", stage,
          "job", job));
    }
  }

  @Test
  void getPipelineReport_shouldReturnNonNullMap() throws Exception {
    PipelineReportClient client = new StubReportClient();
    Map<String, Object> result = client.getPipelineReport("default");
    assertNotNull(result);
    assertTrue(result.containsKey("pipeline"));
  }

  @Test
  void getRunReport_shouldReturnNonNullMap() throws Exception {
    PipelineReportClient client = new StubReportClient();
    Map<String, Object> result = client.getRunReport("default", 1);
    assertNotNull(result);
    assertTrue(result.containsKey("pipeline"));
  }

  @Test
  void getStageReport_shouldReturnNonNullMap() throws Exception {
    PipelineReportClient client = new StubReportClient();
    Map<String, Object> result = client.getStageReport("default", 1, "build");
    assertNotNull(result);
    assertTrue(result.containsKey("pipeline"));
  }

  @Test
  void getJobReport_shouldReturnNonNullMap() throws Exception {
    PipelineReportClient client = new StubReportClient();
    Map<String, Object> result = client.getJobReport("default", 1, "build", "compile");
    assertNotNull(result);
    assertTrue(result.containsKey("pipeline"));
  }

  /**
   * Stub that throws {@link ReportNotFoundException} to verify the interface
   * allows implementations to signal 404.
   */
  private static final class NotFoundReportClient implements PipelineReportClient {

    @Override
    public Map<String, Object> getPipelineReport(String pipeline) {
      throw new ReportNotFoundException("Not found: " + pipeline);
    }

    @Override
    public Map<String, Object> getRunReport(String pipeline, int run) {
      throw new ReportNotFoundException("Not found: " + pipeline + "/" + run);
    }

    @Override
    public Map<String, Object> getStageReport(String pipeline, int run, String stage) {
      throw new ReportNotFoundException("Not found: " + pipeline + "/" + run + "/" + stage);
    }

    @Override
    public Map<String, Object> getJobReport(String pipeline, int run, String stage, String job) {
      throw new ReportNotFoundException(
          "Not found: " + pipeline + "/" + run + "/" + stage + "/" + job);
    }
  }

  @Test
  void getPipelineReport_shouldAllowReportNotFoundException() {
    PipelineReportClient client = new NotFoundReportClient();
    assertThrows(ReportNotFoundException.class, () -> client.getPipelineReport("missing"));
  }

  @Test
  void getRunReport_shouldAllowReportNotFoundException() {
    PipelineReportClient client = new NotFoundReportClient();
    assertThrows(ReportNotFoundException.class, () -> client.getRunReport("missing", 99));
  }

  @Test
  void getStageReport_shouldAllowReportNotFoundException() {
    PipelineReportClient client = new NotFoundReportClient();
    assertThrows(ReportNotFoundException.class,
        () -> client.getStageReport("missing", 1, "ghost"));
  }

  @Test
  void getJobReport_shouldAllowReportNotFoundException() {
    PipelineReportClient client = new NotFoundReportClient();
    assertThrows(ReportNotFoundException.class,
        () -> client.getJobReport("missing", 1, "ghost", "phantom"));
  }
}