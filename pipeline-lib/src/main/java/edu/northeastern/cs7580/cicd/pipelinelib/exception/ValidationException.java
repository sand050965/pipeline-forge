package edu.northeastern.cs7580.cicd.pipelinelib.exception;

/**
 * The {@code ValidationException} class represents errors that occur during
 * CI/CD pipeline configuration validation. All validation failures in the
 * system are signaled by throwing instances of this exception.
 *
 * <p>Validation exceptions are runtime exceptions that indicate problems with
 * pipeline configuration files, such as missing required fields, incorrect types,
 * circular dependencies, or structural violations. These exceptions contain
 * detailed error messages that follow IDE-friendly formatting conventions.
 *
 * <p>Here are some examples of how validation exceptions are used:
 * <blockquote><pre>
 *     throw new ValidationException(
 *         "test.yaml:5:10: ERROR, Missing required 'pipeline.name'"
 *     );
 *
 *     throw new ValidationException(
 *         "test.yaml:0:0: ERROR, Duplicate stage names found"
 *     );
 *
 *     try {
 *         parseYaml(file);
 *     } catch (IOException e) {
 *         throw new ValidationException(
 *             "Failed to read file: " + file,
 *             e
 *         );
 *     }
 * </pre></blockquote>
 *
 * <p>The class {@code ValidationException} extends {@link RuntimeException},
 * meaning it is an unchecked exception that does not require explicit handling
 * in method signatures. This design choice simplifies validation code while
 * still providing detailed error information.
 *
 * <p>Error messages follow the format:
 * {@code filename:line:column: ERROR, message}
 * which allows IDEs to parse and navigate directly to the error location.
 * For example:
 * <blockquote><pre>
 * .pipelines/default.yaml:3:10: ERROR, wrong type of value given for
 * pipeline.name. Expected String, given Integer
 * </pre></blockquote>
 *
 * <p>Unless otherwise noted, passing a {@code null} message to a constructor
 * is permitted but not recommended, as it reduces error clarity.
 *
 * @implNote This exception is designed to halt pipeline processing immediately
 *     upon detection of configuration errors. The detailed messages help users
 *     quickly identify and fix configuration issues. When wrapping other exceptions,
 *     the original cause is preserved for debugging purposes.
 * @see RuntimeException
 */
public class ValidationException extends RuntimeException {

  /**
   * Constructs a new validation exception with the specified detail message.
   *
   * <p>The message should follow the IDE-friendly format including filename,
   * line number, column number, and a descriptive error message. This format
   * enables IDEs and editors to parse the error and navigate to the problematic
   * location in the configuration file.
   *
   * <p>Examples of properly formatted messages:
   * <blockquote><pre>
   *     "test.yaml:5:10: ERROR, Missing required 'pipeline.name'"
   *     "test.yaml:0:0: ERROR, At least 1 stage must be defined"
   *     "test.yaml:15:3: ERROR, Job 'test-job' assigned to undefined stage 'deploy'"
   * </pre></blockquote>
   *
   * <p>For validation errors that wrap other exceptions, use
   * {@link #ValidationException(String, Throwable)} to preserve the original cause.
   *
   * @param message the detail message explaining the validation failure.
   *     The message should include file location information when applicable.
   * @see #ValidationException(String, Throwable)
   */
  public ValidationException(String message) {
    super(message);
  }

  /**
   * Constructs a new validation exception with the specified detail message
   * and cause.
   *
   * <p>This constructor is used when a validation error is triggered by another
   * exception, such as I/O errors during file reading or YAML parsing failures.
   * The cause is preserved to maintain the complete stack trace for debugging.
   *
   * <p>Examples of usage:
   * <blockquote><pre>
   *     try {
   *         Files.readString(path);
   *     } catch (IOException e) {
   *         throw new ValidationException(
   *             path + ":0:0: ERROR, Failed to read file",
   *             e
   *         );
   *     }
   *
   *     try {
   *         yaml.load(input);
   *     } catch (YAMLException e) {
   *         throw new ValidationException(
   *             "test.yaml:0:0: ERROR, Failed to parse YAML",
   *             e
   *         );
   *     }
   * </pre></blockquote>
   *
   * <p>The cause can be retrieved later using {@link #getCause()} for
   * detailed error analysis and logging.
   *
   * @param message the detail message explaining the validation failure
   * @param cause the underlying exception that triggered this validation error.
   *     A {@code null} value is permitted and indicates that the cause is
   *     nonexistent or unknown.
   * @see #ValidationException(String)
   * @see #getCause()
   */
  public ValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}