package edu.northeastern.cs7580.cicd.executionservice.exception;

/**
 * Exception thrown when workspace preparation fails.
 *
 * <p>This exception wraps errors that occur while preparing a workspace directory
 * for pipeline execution, including cloning a Git repository and checking out
 * a specific branch/commit.
 *
 * <p>Common scenarios include:
 * <ul>
 *   <li>Repository URL is invalid or unreachable</li>
 *   <li>Clone operation fails due to authentication or network issues</li>
 *   <li>Checkout fails because the branch or commit does not exist</li>
 *   <li>Filesystem errors when creating or deleting workspace directories</li>
 * </ul>
 */
public class WorkspaceException extends RuntimeException {

  /**
   * Constructs a new workspace exception with the specified message.
   *
   * @param message description of the error
   */
  public WorkspaceException(String message) {
    super(message);
  }

  /**
   * Constructs a new workspace exception with a message and cause.
   *
   * @param message description of the error
   * @param cause the underlying cause of the exception
   */
  public WorkspaceException(String message, Throwable cause) {
    super(message, cause);
  }
}

