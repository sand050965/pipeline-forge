package edu.northeastern.cs7580.cicd.pipeline.exceptions;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import org.junit.jupiter.api.Test;

class ValidationExceptionTest {

  @Test
  void shouldCreateExceptionWithMessage() {
    String message = "Validation failed";

    ValidationException exception = new ValidationException(message);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  void shouldCreateExceptionWithMessageAndCause() {
    String message = "Validation failed";
    Throwable cause = new IllegalArgumentException("Invalid argument");

    ValidationException exception = new ValidationException(message, cause);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isEqualTo(cause);
    assertThat(exception.getCause().getMessage()).isEqualTo("Invalid argument");
  }

  @Test
  void shouldBeRuntimeException() {
    ValidationException exception = new ValidationException("test");

    assertThat(exception).isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldBeThrowable() {
    String message = "test.yaml:5:10: ERROR, Missing required field";

    assertThatThrownBy(() -> {
      throw new ValidationException(message);
    })
        .isInstanceOf(ValidationException.class)
        .hasMessage(message);
  }

  @Test
  void shouldPreserveCauseStackTrace() {
    Throwable cause = new NullPointerException("Null value encountered");
    ValidationException exception = new ValidationException("Validation error", cause);

    assertThat(exception.getCause()).isNotNull();
    assertThat(exception.getCause()).isInstanceOf(NullPointerException.class);
    assertThat(exception.getCause().getMessage()).isEqualTo("Null value encountered");
  }

  @Test
  void shouldHandleNullMessage() {
    ValidationException exception = new ValidationException(null);

    assertThat(exception.getMessage()).isNull();
  }

  @Test
  void shouldHandleEmptyMessage() {
    String message = "";

    ValidationException exception = new ValidationException(message);

    assertThat(exception.getMessage()).isEmpty();
  }

  @Test
  void shouldHandleNullCause() {
    String message = "Validation failed";

    ValidationException exception = new ValidationException(message, null);

    assertThat(exception.getMessage()).isEqualTo(message);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  void shouldSupportTypicalValidationErrorFormat() {
    String errorMessage = "test.yaml:10:5: ERROR, wrong type of value given for "
        + "pipeline.name. Expected String, given Integer";

    ValidationException exception = new ValidationException(errorMessage);

    assertThat(exception.getMessage()).contains("test.yaml");
    assertThat(exception.getMessage()).contains("10:5");
    assertThat(exception.getMessage()).contains("ERROR");
    assertThat(exception.getMessage()).contains("Expected String, given Integer");
  }

  @Test
  void shouldChainMultipleExceptions() {
    Throwable rootCause = new IllegalStateException("Root cause");
    Throwable intermediateCause = new ValidationException("Intermediate error", rootCause);
    ValidationException topException = new ValidationException("Top level error",
        intermediateCause);

    assertThat(topException.getCause()).isEqualTo(intermediateCause);
    assertThat(topException.getCause().getCause()).isEqualTo(rootCause);
  }
}
