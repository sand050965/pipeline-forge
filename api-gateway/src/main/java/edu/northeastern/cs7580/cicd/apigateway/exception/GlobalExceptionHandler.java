package edu.northeastern.cs7580.cicd.apigateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Global exception handler for the API Gateway.
 *
 * <p>Intercepts exceptions thrown during request processing and translates
 * them into consistent, structured HTTP error responses. This ensures all
 * error responses follow a uniform JSON shape regardless of which controller
 * or service client produced the exception.
 *
 * <p><b>Handled exception categories:</b>
 * <ul>
 *   <li>{@link WebClientResponseException} — errors returned by downstream
 *       microservices (Execution Service, Report Service). The original HTTP
 *       status code and response body are preserved and forwarded to the caller
 *       so that structured error details (e.g. validation failure messages)
 *       reach the CLI intact.</li>
 *   <li>{@link Exception} — all other unhandled exceptions. Translated to
 *       HTTP 500 with the exception message included for diagnostics.</li>
 * </ul>
 *
 * <p><b>Response body shape:</b>
 * <pre>
 * {
 *   "status":  &lt;HTTP status code&gt;,
 *   "error":   &lt;short description, 500 only&gt;,
 *   "message": &lt;human-readable detail&gt;
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Handles HTTP errors returned by downstream microservices.
   *
   * <p>Attempts to deserialize the backend response body as a JSON object and
   * forward it to the caller with the original HTTP status code. This preserves
   * structured error details — for example, pipeline validation failure messages
   * from the Execution Service — so they propagate intact to the CLI.
   *
   * <p>If the response body is not valid JSON (e.g. a plain-text error from a
   * proxy or load balancer), falls back to a generic error map containing the
   * raw body string as the {@code message} value.
   *
   * <p><b>Response status:</b> mirrors the status code returned by the backend
   * service (e.g. HTTP 400 for validation errors, HTTP 503 if the backend is
   * temporarily unavailable).
   *
   * @param ex the {@link WebClientResponseException} thrown by the reactive
   *           HTTP client when a backend service returns a 4xx or 5xx response
   * @return a {@link ResponseEntity} whose status code and body match the
   *         backend error response; falls back to a generic map if the body
   *         could not be parsed as JSON
   */
  @ExceptionHandler(WebClientResponseException.class)
  public ResponseEntity<Map<String, Object>> handleWebClientException(
      WebClientResponseException ex) {

    log.error("Backend service error: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());

    try {
      Map<String, Object> originalBody = objectMapper.readValue(
          ex.getResponseBodyAsString(),
          new TypeReference<Map<String, Object>>() {}
      );
      return ResponseEntity.status(ex.getStatusCode()).body(originalBody);
    } catch (JsonProcessingException e) {
      // Body wasn't JSON, fall back to generic error
    }

    Map<String, Object> error = new HashMap<>();
    error.put("status", ex.getStatusCode().value());
    error.put("message", ex.getResponseBodyAsString());

    return ResponseEntity.status(ex.getStatusCode()).body(error);
  }

  /**
   * Catch-all handler for any exception not handled by a more specific handler.
   *
   * <p>Covers unexpected runtime errors such as network failures, null pointer
   * exceptions, or misconfigured beans that surface during request processing.
   * Always returns HTTP 500 so the CLI receives a deterministic error shape
   * rather than an empty or malformed response.
   *
   * <p><b>Response body fields:</b>
   * <ul>
   *   <li>{@code status} — {@code 500}</li>
   *   <li>{@code error} — {@code "Internal Server Error"}</li>
   *   <li>{@code message} — {@link Exception#getMessage()} of the thrown exception</li>
   * </ul>
   *
   * @param ex the unhandled exception
   * @return HTTP 500 response with a JSON error body containing the exception message
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
    log.error("Unexpected error: ", ex);

    Map<String, Object> error = new HashMap<>();
    error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
    error.put("error", "Internal Server Error");
    error.put("message", ex.getMessage());

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}