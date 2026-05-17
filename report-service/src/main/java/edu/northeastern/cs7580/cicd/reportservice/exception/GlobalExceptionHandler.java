package edu.northeastern.cs7580.cicd.reportservice.exception;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the Report Service.
 *
 * <p>This handler intercepts exceptions thrown by controllers and translates
 * them into appropriate HTTP error responses with consistent JSON formatting.
 * It follows the same error response pattern used by the API Gateway to
 * ensure uniform error structures across the CI/CD system.
 *
 * <p>Handles two categories of exceptions:
 * <ul>
 *   <li>{@link ResourceNotFoundException} — the requested pipeline, run,
 *       stage, or job does not exist in the database. Returns HTTP 404
 *       with a descriptive message.</li>
 *   <li>{@link Exception} — any unexpected error during request processing.
 *       Returns HTTP 500 with error details for diagnostics.</li>
 * </ul>
 *
 * <p>Error response format:
 * <pre>
 * {
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Pipeline 'nonexistent' not found"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log =
      LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Handles resource-not-found exceptions.
   *
   * <p>Returns an HTTP 404 response with a descriptive error message
   * identifying the missing resource.
   *
   * @param ex the resource-not-found exception
   * @return a 404 response with error details
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleResourceNotFound(
      ResourceNotFoundException ex) {

    log.error("Resource not found: {}", ex.getMessage());

    Map<String, Object> error = new HashMap<>();
    error.put("status", HttpStatus.NOT_FOUND.value());
    error.put("error", "Not Found");
    error.put("message", ex.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  /**
   * Handles unexpected exceptions that are not specifically handled elsewhere.
   *
   * <p>This is the catch-all handler for any unhandled exception. It returns
   * HTTP 500 with error details to help diagnose issues.
   *
   * @param ex any unhandled exception
   * @return a 500 response with error details
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGenericException(
      Exception ex) {

    log.error("Unexpected error: ", ex);

    Map<String, Object> error = new HashMap<>();
    error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
    error.put("error", "Internal Server Error");
    error.put("message", ex.getMessage());

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}
