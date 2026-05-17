package edu.northeastern.cs7580.cicd.pipeline.internal.services;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser.Position;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationError;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationErrorCollector;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidationErrorCollectorTest {

  private Path testPath;
  private ValidationErrorCollector collector;

  @BeforeEach
  void setUp() {
    testPath = Path.of("test.yaml");
    collector = new ValidationErrorCollector(testPath);
  }

  @Test
  void shouldStartWithNoErrors() {
    assertThat(collector.hasErrors()).isFalse();
    assertThat(collector.getErrors()).isEmpty();
  }

  @Test
  void shouldAddErrorWithLineAndColumn() {
    collector.addError(5, 10, "Invalid value");

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.getErrors()).hasSize(1);

    ValidationError error = collector.getErrors().get(0);
    assertThat(error.getLine()).isEqualTo(5);
    assertThat(error.getColumn()).isEqualTo(10);
    assertThat(error.getMessage()).isEqualTo("Invalid value");
    assertThat(error.getFilename()).isEqualTo("test.yaml");
  }

  @Test
  void shouldAddErrorWithPosition() {
    Position position = new Position(15, 20);
    collector.addError(position, "Missing field");

    assertThat(collector.hasErrors()).isTrue();
    ValidationError error = collector.getErrors().get(0);
    assertThat(error.getLine()).isEqualTo(15);
    assertThat(error.getColumn()).isEqualTo(20);
    assertThat(error.getMessage()).isEqualTo("Missing field");
  }

  @Test
  void shouldHandleNullPosition() {
    collector.addError((Position) null, "Error without position");

    assertThat(collector.hasErrors()).isTrue();
    ValidationError error = collector.getErrors().get(0);
    assertThat(error.getLine()).isEqualTo(0);
    assertThat(error.getColumn()).isEqualTo(0);
    assertThat(error.getMessage()).isEqualTo("Error without position");
  }

  @Test
  void shouldAddErrorWithoutLocation() {
    collector.addError("General error");

    assertThat(collector.hasErrors()).isTrue();
    ValidationError error = collector.getErrors().get(0);
    assertThat(error.getLine()).isEqualTo(0);
    assertThat(error.getColumn()).isEqualTo(0);
    assertThat(error.getMessage()).isEqualTo("General error");
  }

  @Test
  void shouldCollectMultipleErrors() {
    collector.addError(5, 10, "First error");
    collector.addError(10, 20, "Second error");
    collector.addError(15, 30, "Third error");

    assertThat(collector.hasErrors()).isTrue();
    assertThat(collector.getErrors()).hasSize(3);
  }

  @Test
  void shouldNotThrowWhenNoErrors() {
    assertThat(collector.hasErrors()).isFalse();

    // Should not throw
    collector.throwIfHasErrors();
  }

  @Test
  void shouldThrowValidationExceptionWhenHasErrors() {
    collector.addError(5, 10, "Error 1");
    collector.addError(10, 20, "Error 2");

    assertThatThrownBy(() -> collector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("test.yaml:5:10: ERROR, Error 1")
        .hasMessageContaining("test.yaml:10:20: ERROR, Error 2");
  }

  @Test
  void shouldFormatErrorsCorrectly() {
    collector.addError(1, 1, "First");
    collector.addError(2, 2, "Second");

    assertThatThrownBy(() -> collector.throwIfHasErrors())
        .hasMessageContaining("test.yaml:1:1: ERROR, First")
        .hasMessageContaining("test.yaml:2:2: ERROR, Second");
  }

  @Test
  void shouldFormatMultipleErrorsOnSeparateLines() {
    collector.addError(1, 1, "Error 1");
    collector.addError(2, 2, "Error 2");
    collector.addError(3, 3, "Error 3");

    assertThatThrownBy(() -> collector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .satisfies(e -> {
          String message = e.getMessage();
          assertThat(message.split("\n")).hasSize(3);
        });
  }

  @Test
  void shouldUseFilePathInErrors() {
    Path customPath = Path.of(".pipelines/custom.yaml");
    ValidationErrorCollector customCollector = new ValidationErrorCollector(customPath);

    customCollector.addError(10, 5, "Custom error");

    assertThatThrownBy(() -> customCollector.throwIfHasErrors())
        .hasMessageContaining(".pipelines/custom.yaml:10:5: ERROR, Custom error");
  }

  @Test
  void shouldHandleErrorsWithZeroLineAndColumn() {
    collector.addError(0, 0, "Unknown location error");

    assertThatThrownBy(() -> collector.throwIfHasErrors())
        .hasMessageContaining("test.yaml:0:0: ERROR, Unknown location error");
  }

  @Test
  void shouldMaintainErrorOrder() {
    collector.addError(10, 1, "Third");
    collector.addError(5, 1, "First");
    collector.addError(7, 1, "Second");

    assertThat(collector.getErrors()).hasSize(3);
    assertThat(collector.getErrors().get(0).getMessage()).isEqualTo("Third");
    assertThat(collector.getErrors().get(1).getMessage()).isEqualTo("First");
    assertThat(collector.getErrors().get(2).getMessage()).isEqualTo("Second");
  }

  @Test
  void shouldAllowMixingErrorAdditionMethods() {
    collector.addError(5, 10, "With line/column");
    collector.addError(new Position(15, 20), "With position");
    collector.addError("Without location");

    assertThat(collector.getErrors()).hasSize(3);
    assertThat(collector.getErrors().get(0).getLine()).isEqualTo(5);
    assertThat(collector.getErrors().get(1).getLine()).isEqualTo(15);
    assertThat(collector.getErrors().get(2).getLine()).isEqualTo(0);
  }

  @Test
  void shouldReturnCorrectFilePath() {
    assertThat(collector.getFilePath()).isEqualTo(testPath);
  }

  @Test
  void shouldHandleComplexFilePaths() {
    Path complexPath = Path.of("some/nested/directory/pipeline.yaml");
    ValidationErrorCollector complexCollector = new ValidationErrorCollector(complexPath);

    complexCollector.addError(1, 1, "Error");

    assertThatThrownBy(() -> complexCollector.throwIfHasErrors())
        .hasMessageContaining("some/nested/directory/pipeline.yaml:1:1");
  }

  @Test
  void shouldHandleNullMessage() {
    collector.addError(5, 10, null);

    assertThat(collector.hasErrors()).isTrue();
    ValidationError error = collector.getErrors().get(0);
    assertThat(error.getMessage()).isNull();
  }

  @Test
  void shouldHandleEmptyMessage() {
    collector.addError(5, 10, "");

    assertThat(collector.hasErrors()).isTrue();
    ValidationError error = collector.getErrors().get(0);
    assertThat(error.getMessage()).isEmpty();
  }

  @Test
  void shouldHandleNegativeLineAndColumn() {
    collector.addError(-1, -5, "Negative position");

    assertThat(collector.hasErrors()).isTrue();
    ValidationError error = collector.getErrors().get(0);
    assertThat(error.getLine()).isEqualTo(-1);
    assertThat(error.getColumn()).isEqualTo(-5);
  }

  @Test
  void shouldHandleVeryLargeLineAndColumn() {
    collector.addError(Integer.MAX_VALUE, Integer.MAX_VALUE, "Large position");

    assertThat(collector.hasErrors()).isTrue();
    ValidationError error = collector.getErrors().get(0);
    assertThat(error.getLine()).isEqualTo(Integer.MAX_VALUE);
    assertThat(error.getColumn()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void shouldFormatErrorWithPositionZeroZero() {
    collector.addError(0, 0, "Error at unknown position");

    assertThatThrownBy(() -> collector.throwIfHasErrors())
        .hasMessageContaining("test.yaml:0:0: ERROR, Error at unknown position");
  }

  @Test
  void shouldHandleMultipleCallsToThrowIfHasErrors() {
    collector.addError(1, 1, "Error");

    assertThatThrownBy(() -> collector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class);

    // Calling again should still throw (errors not cleared)
    assertThatThrownBy(() -> collector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void shouldReturnDefensiveCopyOfErrors() {
    collector.addError(1, 1, "Error 1");

    List<ValidationError> errors1 = collector.getErrors();
    collector.addError(2, 2, "Error 2");
    List<ValidationError> errors2 = collector.getErrors();

    // If defensive copy is made, sizes should differ
    assertThat(errors1).hasSize(1);
    assertThat(errors2).hasSize(2);
  }
}