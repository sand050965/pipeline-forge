package edu.northeastern.cs7580.cicd.cli.core;

/**
 * Represents an error when the requested Git branch or commit does not match the
 * current repository state.
 *
 * <p>This exception is thrown when the CLI detects that execution would not run
 * against the code version the user requested. It is used to fail fast and
 * provide a clear, human-readable message describing the mismatch.
 *
 * <p>The {@code GitStateException} is typically raised by {@link DefaultGitStateValidator}
 * when validating:
 * <ul>
 *   <li>Current branch name against a requested branch</li>
 *   <li>Current HEAD commit SHA against a requested commit</li>
 *   <li>Detached HEAD and other Git edge cases</li>
 * </ul>
 */
public final class GitStateException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new exception with the provided message.
   *
   * @param message the error message
   */
  public GitStateException(String message) {
    super(message);
  }

  /**
   * Creates a new exception with the provided message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public GitStateException(String message, Throwable cause) {
    super(message, cause);
  }
}

