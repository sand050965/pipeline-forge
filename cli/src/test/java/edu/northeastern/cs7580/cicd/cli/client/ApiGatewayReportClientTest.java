package edu.northeastern.cs7580.cicd.cli.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.cli.exception.ReportNotFoundException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ApiGatewayReportClient}.
 *
 * <p>The {@link HttpClient} is mocked via Mockito and injected via reflection
 * to avoid real network calls, following the same pattern as
 * {@code RunCommandHttpTest}.
 */
class ApiGatewayReportClientTest {

  private HttpClient mockHttpClient;
  private ApiGatewayReportClient client;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws Exception {
    mockHttpClient = mock(HttpClient.class);
    client = new ApiGatewayReportClient("http://localhost:8080");
    injectField(client, "httpClient", mockHttpClient);
  }

  // ── Constructor tests ────────────────────────────────────────────────────────

  @Test
  void constructor_shouldCreateInstance() {
    assertNotNull(new ApiGatewayReportClient("http://localhost:8080"));
  }

  @Test
  void constructor_shouldThrow_whenBaseUrlIsNull() {
    assertThrows(IllegalArgumentException.class, () -> new ApiGatewayReportClient(null));
  }

  @Test
  void constructor_shouldThrow_whenBaseUrlIsBlank() {
    assertThrows(IllegalArgumentException.class, () -> new ApiGatewayReportClient("   "));
  }

  @Test
  void constructor_shouldStripTrailingSlash() throws Exception {
    ApiGatewayReportClient c = new ApiGatewayReportClient("http://localhost:8080/");
    String baseUrl = getField(c, "baseUrl", String.class);
    assertEquals("http://localhost:8080", baseUrl);
  }

  // ── getPipelineReport ────────────────────────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void getPipelineReport_shouldReturnDeserializedMap_on200() throws Exception {
    String json = "{\"pipeline\":{\"name\":\"default\",\"runs\":[]}}";
    stubResponse(200, json);

    Map<String, Object> result = client.getPipelineReport("default");

    assertNotNull(result);
    assertEquals("default", ((Map<?, ?>) result.get("pipeline")).get("name"));
  }

  @Test
  void getPipelineReport_shouldThrowReportNotFoundException_on404() throws Exception {
    stubResponse(404, "");
    assertThrows(ReportNotFoundException.class, () -> client.getPipelineReport("missing"));
  }

  @Test
  void getPipelineReport_shouldThrowRuntimeException_on500() throws Exception {
    stubResponse(500, "Internal Server Error");
    assertThrows(RuntimeException.class, () -> client.getPipelineReport("default"));
  }

  // ── getRunReport ─────────────────────────────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void getRunReport_shouldReturnDeserializedMap_on200() throws Exception {
    String json = "{\"pipeline\":{\"name\":\"default\",\"run-no\":1,\"stages\":[]}}";
    stubResponse(200, json);

    Map<String, Object> result = client.getRunReport("default", 1);

    assertNotNull(result);
    assertEquals(1, ((Map<?, ?>) result.get("pipeline")).get("run-no"));
  }

  @Test
  void getRunReport_shouldThrowReportNotFoundException_on404() throws Exception {
    stubResponse(404, "");
    assertThrows(ReportNotFoundException.class, () -> client.getRunReport("default", 99));
  }

  // ── getStageReport ───────────────────────────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void getStageReport_shouldReturnDeserializedMap_on200() throws Exception {
    String json = "{\"pipeline\":{\"name\":\"default\",\"run-no\":1,"
        + "\"stage\":[{\"name\":\"build\",\"jobs\":[]}]}}";
    stubResponse(200, json);

    Map<String, Object> result = client.getStageReport("default", 1, "build");

    assertNotNull(result);
    assertNotNull(((Map<?, ?>) result.get("pipeline")).get("stage"));
  }

  @Test
  void getStageReport_shouldThrowReportNotFoundException_on404() throws Exception {
    stubResponse(404, "");
    assertThrows(ReportNotFoundException.class,
        () -> client.getStageReport("default", 1, "ghost"));
  }

  // ── getJobReport ─────────────────────────────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void getJobReport_shouldReturnDeserializedMap_on200() throws Exception {
    String json = "{\"pipeline\":{\"name\":\"default\",\"run-no\":1,"
        + "\"stage\":[{\"name\":\"build\",\"job\":[{\"name\":\"compile\"}]}]}}";
    stubResponse(200, json);

    Map<String, Object> result = client.getJobReport("default", 1, "build", "compile");

    assertNotNull(result);
    assertNotNull(((Map<?, ?>) result.get("pipeline")).get("stage"));
  }

  @Test
  void getJobReport_shouldThrowReportNotFoundException_on404() throws Exception {
    stubResponse(404, "");
    assertThrows(ReportNotFoundException.class,
        () -> client.getJobReport("default", 1, "build", "phantom"));
  }

  // ── Error message ────────────────────────────────────────────────────────────

  @Test
  void nonSuccessResponse_shouldIncludeStatusCodeInMessage() throws Exception {
    stubResponse(503, "Service Unavailable");
    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> client.getPipelineReport("default"));
    assertNotNull(ex.getMessage());
    assertEquals(true, ex.getMessage().contains("503"));
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private void stubResponse(int statusCode, String body) throws Exception {
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockResponse.statusCode()).thenReturn(statusCode);
    when(mockResponse.body()).thenReturn(body);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(mockResponse);
  }

  private static void injectField(Object target, String fieldName, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }
}