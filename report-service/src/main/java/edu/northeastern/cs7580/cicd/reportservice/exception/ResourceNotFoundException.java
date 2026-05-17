package edu.northeastern.cs7580.cicd.reportservice.exception;

/**
 * Exception thrown when a requested resource (pipeline, run, stage, or job)
 * cannot be found in the database.
 *
 * <p>This exception is caught by {@link GlobalExceptionHandler} and
 * translated into an HTTP 404 response with a descriptive error message.
 * The message should clearly identify what resource was not found and
 * what criteria were used in the lookup.
 *
 * <p>Example messages:
 * <ul>
 *   <li>{@code "Pipeline 'default' not found"}</li>
 *   <li>{@code "Run 5 not found for pipeline 'default'"}</li>
 *   <li>{@code "Stage 'build' not found in run 1 of pipeline 'default'"}</li>
 *   <li>{@code "Job 'compile' not found in stage 'build' of run 1
 *         of pipeline 'default'"}</li>
 * </ul>
 */
public class ResourceNotFoundException extends RuntimeException {

  /**
   * Creates a new resource-not-found exception with the given message.
   *
   * @param message a descriptive message identifying the missing resource
   */
  public ResourceNotFoundException(String message) {
    super(message);
  }
}
