package edu.northeastern.cs7580.cicd.cli.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.cs7580.cicd.cli.config.CliConfig;
import edu.northeastern.cs7580.cicd.cli.exception.ReportNotFoundException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * HTTP client for retrieving pipeline execution reports from the API Gateway.
 *
 * <p>Sends GET requests to the API Gateway's report endpoints and deserializes
 * the JSON response into a {@code Map<String, Object>} for direct YAML rendering.
 *
 * <p>Throws {@link ReportNotFoundException} on HTTP 404 so that {@code ReportCommand}
 * can print a user-friendly message instead of a stack trace.
 *
 * @see PipelineReportClient
 * @see edu.northeastern.cs7580.cicd.cli.command.ReportCommand
 */
public final class ApiGatewayReportClient implements PipelineReportClient {

  private static final String REPORT_BASE = "/api/v1/pipelines/report";
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final String baseUrl;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;


  /**
   * Creates a client using the API Gateway base URL resolved by {@link CliConfig}.
   *
   * <p>The base URL is resolved in the following priority order:
   * <ol>
   *   <li>Command-line option {@code --api-gateway <url>} (highest priority)</li>
   *   <li>Environment variable {@code CICD_API_GATEWAY_URL}</li>
   *   <li>Default value {@code http://localhost:8080}</li>
   * </ol>
   *
   * <p>This is the constructor used in normal CLI operation. The overloaded
   * constructor {@link #ApiGatewayReportClient(String)} exists for unit testing,
   * where a mock server URL is injected directly.
   */
  public ApiGatewayReportClient() {
    this(CliConfig.apiGatewayBaseUrl());
  }

  /**
   * Creates a client using the provided base URL.
   *
   * <p>Trailing slashes are stripped from {@code baseUrl} so that path segments
   * can always be appended with a leading {@code /} without producing double slashes
   * (e.g. {@code http://localhost:8080/} becomes {@code http://localhost:8080}).
   *
   * <p>A new {@link HttpClient} and {@link ObjectMapper} are created per instance.
   * For CLI usage this is acceptable because the client is constructed once per
   * command invocation and then discarded.
   *
   * @param baseUrl the base URL of the API Gateway, e.g. {@code http://localhost:8080};
   *                must not be {@code null} or blank
   * @throws IllegalArgumentException if {@code baseUrl} is {@code null} or blank
   */
  public ApiGatewayReportClient(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be null/blank");
    }
    this.baseUrl = trimTrailingSlash(baseUrl);
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Returns all recorded runs for the named pipeline.
   *
   * <p>Sends {@code GET /api/v1/report/pipelines/{pipeline}} to the API Gateway
   * and returns the response as a map. The map structure matches the spec's
   * pipeline-level YAML shape:
   * <pre>
   * pipeline:
   *   name: default
   *   runs:
   *     - run-no: 1
   *       status: success
   *       ...
   * </pre>
   *
   * @param pipeline the pipeline name as defined in {@code pipeline.name} in the
   *                 YAML configuration file; must match exactly (case-sensitive)
   * @return a map representing all runs for the pipeline, ready for YAML rendering
   * @throws ReportNotFoundException if no pipeline with the given name exists (HTTP 404)
   * @throws RuntimeException        if the API Gateway returns a non-2xx status other than 404
   * @throws Exception               if a network error or JSON deserialization failure occurs
   */
  @Override
  public Map<String, Object> getPipelineReport(String pipeline) throws Exception {
    return get("/pipelines/%s".formatted(pipeline));
  }

  /**
   * Returns the details of a specific run of the named pipeline.
   *
   * <p>Sends {@code GET /api/v1/report/pipelines/{pipeline}/runs/{run}} to the
   * API Gateway. The returned map includes pipeline-level metadata as well as
   * a summary of each stage in the run:
   * <pre>
   * pipeline:
   *   name: default
   *   run-no: 1
   *   status: success
   *   stages:
   *     - name: build
   *       status: success
   *       ...
   * </pre>
   *
   * @param pipeline the pipeline name; must match exactly (case-sensitive)
   * @param run      the run number as returned in a previous pipeline report;
   *                 run numbers start at 1 and increment with each execution
   * @return a map representing the specified run, ready for YAML rendering
   * @throws ReportNotFoundException if the pipeline or run number does not exist (HTTP 404)
   * @throws RuntimeException        if the API Gateway returns a non-2xx status other than 404
   * @throws Exception               if a network error or JSON deserialization failure occurs
   */
  @Override
  public Map<String, Object> getRunReport(String pipeline, int run) throws Exception {
    return get("/pipelines/%s/runs/%d".formatted(pipeline, run));
  }

  /**
   * Returns the details of a specific stage within a pipeline run, including
   * a summary of every job that executed in that stage.
   *
   * <p>Sends {@code GET /api/v1/report/pipelines/{pipeline}/runs/{run}/stages/{stage}}
   * to the API Gateway. The returned map nests job summaries under the stage:
   * <pre>
   * pipeline:
   *   name: default
   *   run-no: 1
   *   stage:
   *     - name: build
   *       status: success
   *       jobs:
   *         - name: compile
   *           status: success
   *           ...
   * </pre>
   *
   * @param pipeline the pipeline name; must match exactly (case-sensitive)
   * @param run      the run number
   * @param stage    the stage name as defined in the pipeline YAML configuration;
   *                 must match exactly (case-sensitive)
   * @return a map representing the specified stage and its jobs, ready for YAML rendering
   * @throws ReportNotFoundException if the pipeline, run, or stage does not exist (HTTP 404)
   * @throws RuntimeException        if the API Gateway returns a non-2xx status other than 404
   * @throws Exception               if a network error or JSON deserialization failure occurs
   */
  @Override
  public Map<String, Object> getStageReport(String pipeline, int run, String stage)
      throws Exception {
    return get("/pipelines/%s/runs/%d/stages/%s".formatted(pipeline, run, stage));
  }

  /**
   * Returns the details of a specific job within a stage of a pipeline run.
   *
   * <p>Sends {@code GET /api/v1/report/pipelines/{pipeline}/runs/{run}/stages/{stage}/jobs/{job}}
   * to the API Gateway. This is the most granular report level, returning timing
   * and status for a single job:
   * <pre>
   * pipeline:
   *   name: default
   *   run-no: 1
   *   stage:
   *     - name: build
   *       status: success
   *       job:
   *         - name: compile
   *           status: success
   *           ...
   * </pre>
   *
   * @param pipeline the pipeline name; must match exactly (case-sensitive)
   * @param run      the run number
   * @param stage    the stage name; must match exactly (case-sensitive)
   * @param job      the job name as defined in the pipeline YAML configuration;
   *                 must match exactly (case-sensitive)
   * @return a map representing the specified job, ready for YAML rendering
   * @throws ReportNotFoundException if the pipeline, run, stage, or job does not exist (HTTP 404)
   * @throws RuntimeException        if the API Gateway returns a non-2xx status other than 404
   * @throws Exception               if a network error or JSON deserialization failure occurs
   */
  @Override
  public Map<String, Object> getJobReport(String pipeline, int run, String stage, String job)
      throws Exception {
    return get("/pipelines/%s/runs/%d/stages/%s/jobs/%s".formatted(pipeline, run, stage, job));
  }

  /**
   * Performs an HTTP GET request to the given path and deserializes the JSON
   * response body into a {@code Map<String, Object>}.
   *
   * <p>The full request URL is assembled as:
   * <pre>{@code baseUrl + REPORT_BASE + path}</pre>
   * For example, with {@code baseUrl = "http://localhost:8080"} and
   * {@code path = "/pipelines/default/runs/1"}, the request is sent to:
   * <pre>{@code http://localhost:8080/api/v1/report/pipelines/default/runs/1}</pre>
   *
   * <p>Response handling:
   * <ul>
   *   <li>{@code 200 OK} — body is deserialized into a map and returned.</li>
   *   <li>{@code 404 Not Found} — a {@link ReportNotFoundException} is thrown
   *       with the full URL so {@code ReportCommand} can print a clear message.</li>
   *   <li>Any other status — a {@link RuntimeException} is thrown with the status
   *       code and, if present, the raw response body for diagnostic context.</li>
   * </ul>
   *
   * @param path the path segment to append to {@code REPORT_BASE}, starting with
   *             {@code /} (e.g. {@code /pipelines/default})
   * @return the deserialized response body as a map
   * @throws ReportNotFoundException if the server returns HTTP 404
   * @throws RuntimeException        if the server returns any other non-2xx status
   * @throws Exception               if a network failure or JSON deserialization error occurs
   */
  private Map<String, Object> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + REPORT_BASE + path))
            .GET()
            .build();

    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    return switch (response.statusCode()) {
      case 200 -> objectMapper.readValue(response.body(), MAP_TYPE);
      case 404 -> throw new ReportNotFoundException(
          "Not found: " + baseUrl + REPORT_BASE + path);
      default  -> throw new RuntimeException(
          "Report request failed: HTTP " + response.statusCode()
              + (response.body().isBlank() ? "" : " — " + response.body()));
    };
  }

  /**
   * Removes a trailing slash from a URL string, if present.
   *
   * <p>This ensures that path segments appended later with a leading {@code /}
   * do not produce malformed double-slash URLs like
   * {@code http://localhost:8080//api/v1/report/pipelines/default}.
   *
   * @param url the URL string to normalize
   * @return the URL with any trailing slash removed
   */
  private static String trimTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}


