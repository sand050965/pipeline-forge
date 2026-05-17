package edu.northeastern.cs7580.cicd.pipelinelib.internal.service;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a single validation error with precise location information.
 *
 * <p>This immutable class encapsulates all details about a validation error
 * including the file name, line number, column number, and error message.
 * Instances are typically created through the builder pattern and collected
 * by {@link ValidationErrorCollector} before being formatted and reported.
 *
 * <p>The error format follows IDE-friendly conventions, allowing developers
 * to click on error messages in their terminal to jump directly to the
 * problematic location in their configuration files:
 * {@code filename:line:column: ERROR, message}
 *
 * <p>Example usage:
 * <blockquote><pre>
 *     ValidationError error = ValidationError.builder()
 *         .filename(".pipelines/default.yaml")
 *         .line(5)
 *         .column(10)
 *         .message("Missing required field 'stage'")
 *         .build();
 *
 *     String formatted = String.format("%s:%d:%d: ERROR, %s",
 *         error.getFilename(), error.getLine(),
 *         error.getColumn(), error.getMessage());
 *     // Output: .pipelines/default.yaml:5:10: ERROR, Missing required field 'stage'
 * </pre></blockquote>
 *
 * <p>Line and column numbers are 1-indexed to match standard editor conventions.
 * A value of 0 for line or column indicates the position is unknown or not
 * applicable to the specific error.
 *
 * <p>This class uses Lombok's {@code @Data} and {@code @Builder} annotations
 * to provide immutability, builder pattern construction, and standard object
 * methods (equals, hashCode, toString).
 *
 * @implNote All fields are final, making instances immutable after construction.
 *     This immutability is important for thread-safety when errors are collected
 *     and processed. The class provides no setters, only getters and builder methods.
 * @see ValidationErrorCollector
 * @see ValidationException
 * @see PositionAwareYamlParser.Position
 */
@Data
@Builder
public class ValidationError {
  private String filename;
  private int line;
  private int column;
  private String message;

}
