package edu.northeastern.cs7580.cicd.cli.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunRequestDto;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunResponseDto;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ApiGatewayClient}.
 */
class ApiGatewayClientTest {

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void executePipeline_success_parsesResponse() throws Exception {
    server.createContext("/api/v1/pipelines/execute", exchange -> {
      assertEquals("POST", exchange.getRequestMethod());
      String body = readUtf8(exchange.getRequestBody());
      // Verify the request body uses the API Gateway format.
      if (!body.contains("\"pipelineName\":\"default\"")
          || !body.contains("\"pipelineFilePath\":")
          || !body.contains("\"gitMetadata\":{")) {
        write(exchange, 400, "{\"message\":\"bad request\"}");
        return;
      }
      write(exchange, 200,
          "{\"executionId\":\"exec-123\",\"pipelineName\":\"default\","
              + "\"runNumber\":1,\"status\":\"SUCCESS\",\"message\":\"ok\"}");
    });
    server.start();

    ApiGatewayClient client = new ApiGatewayClient(baseUrl);
    RunRequestDto req = createRequest();

    RunResponseDto resp = client.executePipeline(req);

    assertEquals("exec-123", resp.getExecutionId());
    assertEquals("SUCCESS", resp.getStatus());
    assertEquals("ok", resp.getMessage());
  }

  @Test
  void executePipeline_sendsCorrectGitMetadata() throws Exception {
    server.createContext("/api/v1/pipelines/execute", exchange -> {
      String body = readUtf8(exchange.getRequestBody());
      assertTrue(body.contains("\"repositoryUrl\":\"https://github.com/test/repo.git\""));
      assertTrue(body.contains("\"branch\":\"main\""));
      assertTrue(body.contains("\"commitHash\":\"abc123\""));
      write(exchange, 200,
          "{\"executionId\":\"exec-1\",\"status\":\"SUCCESS\"}");
    });
    server.start();

    ApiGatewayClient client = new ApiGatewayClient(baseUrl);
    RunRequestDto req = createRequest();

    client.executePipeline(req);
  }

  @Test
  void executePipeline_non2xx_throws() throws Exception {
    server.createContext("/api/v1/pipelines/execute", exchange -> write(exchange, 500, "boom"));
    server.start();

    ApiGatewayClient client = new ApiGatewayClient(baseUrl);
    RunRequestDto req = createRequest();

    RuntimeException ex = assertThrows(RuntimeException.class, () -> client.executePipeline(req));
    assertTrue(ex.getMessage().contains("HTTP 500"));
  }

  private static RunRequestDto createRequest() {
    RunRequestDto req = new RunRequestDto("default", null, "main", "abc123");
    req.setPipelineFilePath(".pipelines/default.yaml");
    req.setRepositoryUrl("https://github.com/test/repo.git");
    req.setResolvedCommitHash("abc123");
    return req;
  }

  private static String readUtf8(InputStream in) throws IOException {
    byte[] bytes = in.readAllBytes();
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static void write(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
