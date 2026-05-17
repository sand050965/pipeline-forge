package edu.northeastern.cs7580.cicd.cli.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunRequestDto;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunResponseDto;
import edu.northeastern.cs7580.cicd.cli.config.CliConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP client for communicating with the remote API Gateway service.
 *
 * <p>This client handles remote operations such as executing pipelines,
 * checking status, and retrieving reports. Local operations like {@code verify}
 * and {@code dryrun} do not use this client.
 *
 * <p>The API Gateway URL can be configured via:
 * <ul>
 *   <li>Environment variable: {@code CICD_API_GATEWAY_URL}</li>
 *   <li>Command-line option: {@code --api-gateway <url>}</li>
 *   <li>Default: {@code http://localhost:8080}</li>
 * </ul>
 *
 * @see edu.northeastern.cs7580.cicd.cli.command.RunCommand
 * @see edu.northeastern.cs7580.cicd.cli.command.VerifyCommand
 * @see edu.northeastern.cs7580.cicd.cli.command.ReportCommand
 */
public final class ApiGatewayClient implements PipelineExecutionClient {

  private static final String EXECUTE_PATH = "/api/v1/pipelines/execute";

  private final String baseUrl;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  /** Creates a client using the base URL from {@link CliConfig}. */
  public ApiGatewayClient() {
    this(CliConfig.apiGatewayBaseUrl());
  }

  /**
   * Creates a client using the provided base URL.
   *
   * @param baseUrl API Gateway base URL
   */
  public ApiGatewayClient(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be null/blank");
    }
    this.baseUrl = trimTrailingSlash(baseUrl);
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Starts a pipeline execution via the API Gateway.
   *
   * <p>Transforms the CLI request DTO into the JSON format expected by
   * the API Gateway's {@code PipelineExecutionRequest}.
   *
   * @param request request payload
   * @return response payload
   * @throws IOException if a network or serialization error occurs
   * @throws InterruptedException if the request is interrupted
   */
  @Override
  public RunResponseDto executePipeline(RunRequestDto request)
      throws IOException, InterruptedException {
    String json = objectMapper.writeValueAsString(toGatewayPayload(request));

    HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + EXECUTE_PATH))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

    HttpResponse<String> response =
        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() >= 200 && response.statusCode() < 300
        || response.statusCode() == 400) {
      return objectMapper.readValue(response.body(), RunResponseDto.class);
    }

    try {
      RunResponseDto errorResponse = objectMapper.readValue(response.body(), RunResponseDto.class);
      if (errorResponse.getMessage() != null) {
        throw new RuntimeException(errorResponse.getMessage());
      }
    } catch (JsonProcessingException ignored) {
      // body wasn't valid JSON, fall through to generic error
    }
    throw new RuntimeException("Execution request failed: HTTP " + response.statusCode());
  }

  /**
   * Converts a CLI request DTO into the API Gateway payload format.
   *
   * @param request CLI request DTO
   * @return map matching the API Gateway's PipelineExecutionRequest schema
   */
  private Map<String, Object> toGatewayPayload(RunRequestDto request) {
    Map<String, Object> gitMetadata = new LinkedHashMap<>();
    gitMetadata.put("repositoryUrl", request.getRepositoryUrl());
    gitMetadata.put("branch", request.getGitBranch());
    gitMetadata.put("commitHash", request.getResolvedCommitHash());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("pipelineName", request.getName());
    payload.put("pipelineFilePath", request.getPipelineFilePath());
    payload.put("gitMetadata", gitMetadata);
    return payload;
  }

  private static String trimTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
