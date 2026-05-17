package edu.northeastern.cs7580.cicd.executionservice.exception;

/**
 * Exception thrown when Docker operations fail.
 *
 * <p>This exception wraps errors that occur during Docker container
 * operations such as image pulling, container creation, command
 * execution, or container cleanup.
 *
 * <p>Common scenarios include:
 * <ul>
 *   <li>Docker image not found or cannot be pulled</li>
 *   <li>Container creation fails due to resource constraints</li>
 *   <li>Command execution fails inside the container</li>
 *   <li>Docker daemon is not running or unreachable</li>
 * </ul>
 */
public class DockerException extends RuntimeException {

  /**
   * Constructs a new Docker exception with the specified message.
   *
   * @param message description of the error
   */
  public DockerException(String message) {
    super(message);
  }

  /**
   * Constructs a new Docker exception with a message and cause.
   *
   * @param message description of the error
   * @param cause the underlying cause of the exception
   */
  public DockerException(String message, Throwable cause) {
    super(message, cause);
  }
}
