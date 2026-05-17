package edu.northeastern.cs7580.cicd.cli.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.cs7580.cicd.cli.config.CliConfig;
import edu.northeastern.cs7580.cicd.cli.exception.ReportNotFoundException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP client for querying live pipeline execution status from the API Gateway.
 *
 * <p>Sends GET requests to the {@code /api/v1/status} endpoints and deserializes
 * the JSON response into a {@code Map<String, Object>} for YAML rendering by
 * {@link edu.northeastern.cs7580.cicd.cli.command.StatusCommand}.
 *
 * @see PipelineStatusClient
 */
public final class ApiGatewayStatusClient implements PipelineStatusClient {

  private static final String STATUS_BASE = "/api/v1/status";
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final String baseUrl;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  /** Creates a client using the API Gateway base URL resolved by {@link CliConfig}. */
  public ApiGatewayStatusClient() {
    this(CliConfig.apiGatewayBaseUrl());
  }

  /**
   * Creates a client using the provided base URL.
   *
   * @param baseUrl the base URL of the API Gateway; must not be {@code null} or blank
   */
  public ApiGatewayStatusClient(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be null/blank");
    }
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public Map<String, Object> getStatusByRepo(String repoUrl) throws Exception {
    String encoded = URLEncoder.encode(repoUrl, StandardCharsets.UTF_8);
    return get(STATUS_BASE + "?repo=" + encoded);
  }

  @Override
  public Map<String, Object> getStatusByRun(String pipeline, int runNo) throws Exception {
    return get("%s/%s/runs/%d".formatted(STATUS_BASE, pipeline, runNo));
  }

  private Map<String, Object> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .GET()
            .build();

    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    return switch (response.statusCode()) {
      case 200 -> objectMapper.readValue(response.body(), MAP_TYPE);
      case 404 -> throw new ReportNotFoundException("Not found: " + baseUrl + path);
      default -> throw new RuntimeException(
          "Status request failed: HTTP " + response.statusCode()
              + (response.body().isBlank() ? "" : " — " + response.body()));
    };
  }
}
