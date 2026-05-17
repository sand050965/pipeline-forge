package edu.northeastern.cs7580.cicd.apigateway.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  @Test
  void handleWebClientException_NotFound_Returns404() {
    WebClientResponseException ex = WebClientResponseException.create(
        404,
        "Not Found",
        null,
        "Resource not found".getBytes(StandardCharsets.UTF_8),
        StandardCharsets.UTF_8
    );

    ResponseEntity<Map<String, Object>> response = handler.handleWebClientException(ex);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals(404, body.get("status"));
    assertTrue(body.get("message").toString().contains("Resource not found"));
  }

  @Test
  void handleWebClientException_InternalServerError_Returns500() {
    WebClientResponseException ex = WebClientResponseException.create(
        500, "Internal Server Error", null,
        "Server crashed".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
    );

    ResponseEntity<Map<String, Object>> response = handler.handleWebClientException(ex);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals(500, body.get("status"));
    assertTrue(body.get("message").toString().contains("Server crashed"));
  }

  @Test
  void handleWebClientException_BadRequest_Returns400() {
    WebClientResponseException ex = WebClientResponseException.create(
        400, "Bad Request", null,
        "Invalid input".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
    );

    ResponseEntity<Map<String, Object>> response = handler.handleWebClientException(ex);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.get("status"));
    assertTrue(body.get("message").toString().contains("Invalid input"));
  }

  @Test
  void handleWebClientException_ServiceUnavailable_Returns503() {
    WebClientResponseException ex = WebClientResponseException.create(
        503, "Service Unavailable", null,
        "Service down".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
    );

    ResponseEntity<Map<String, Object>> response = handler.handleWebClientException(ex);

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals(503, body.get("status"));
    assertTrue(body.get("message").toString().contains("Service down"));
  }

  @Test
  void handleWebClientException_UnknownStatusCode_HandlesGracefully() {
    WebClientResponseException ex = WebClientResponseException.create(
        999, "Unknown", null,
        "Unknown error".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
    );

    ResponseEntity<Map<String, Object>> response = handler.handleWebClientException(ex);

    assertEquals(999, response.getStatusCode().value());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals(999, body.get("status"));
    assertTrue(body.get("message").toString().contains("Unknown error"));
  }


  @Test
  void handleWebClientException_ResponseBody_IncludedInLog() {
    String responseBody = "{\"error\": \"detailed backend error\"}";
    WebClientResponseException ex = WebClientResponseException.create(
        500, "Internal Server Error", null,
        responseBody.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
    );

    ResponseEntity<Map<String, Object>> response = handler.handleWebClientException(ex);

    assertNotNull(response.getBody());
    assertEquals("detailed backend error", response.getBody().get("error"));
  }


  @Test
  void handleGenericException_ReturnsInternalServerError() {
    Exception ex = new RuntimeException("Something went wrong");

    ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals(500, body.get("status"));
    assertEquals("Internal Server Error", body.get("error"));
    assertEquals("Something went wrong", body.get("message"));
  }

  @Test
  void handleGenericException_NullPointerException_Returns500() {
    Exception ex = new NullPointerException("Null pointer occurred");

    ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals("Null pointer occurred", body.get("message"));
  }

  @Test
  void handleGenericException_IllegalArgumentException_Returns500() {
    Exception ex = new IllegalArgumentException("Invalid argument");

    ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals("Invalid argument", body.get("message"));
  }

  @Test
  void handleGenericException_WithNullMessage_HandlesGracefully() {
    Exception ex = new RuntimeException((String) null);

    ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertNull(body.get("message"));
  }

  @Test
  void errorResponse_ContainsRequiredFields() {
    Exception ex = new RuntimeException("Test error");

    ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertTrue(body.containsKey("status"));
    assertTrue(body.containsKey("error"));
    assertTrue(body.containsKey("message"));
  }

  @Test
  void webClientException_StatusCodePropagated() {
    int[] statusCodes = {400, 401, 403, 404, 500, 502, 503};

    for (int statusCode : statusCodes) {
      WebClientResponseException ex = WebClientResponseException.create(
          statusCode,
          "Status " + statusCode,
          null,
          new byte[0],
          StandardCharsets.UTF_8
      );

      ResponseEntity<Map<String, Object>> response = handler.handleWebClientException(ex);

      assertEquals(statusCode, response.getStatusCode().value());
      assertEquals(statusCode, response.getBody().get("status"));
    }
  }

  @Test
  void handleWebClientException_JsonBody_PassedThroughAsIs() {
    String responseBody = "{\"status\":\"VALIDATION_FAILED\","
        + "\"message\":\"tmp/file.yaml:5:3: ERROR, Cycle detected\"}";
    WebClientResponseException ex = WebClientResponseException.create(
        400, "Bad Request", null,
        responseBody.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
    );

    ResponseEntity<Map<String, Object>> response = handler.handleWebClientException(ex);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals("VALIDATION_FAILED", body.get("status"));
    assertTrue(body.get("message").toString().contains("Cycle detected"));
  }
}