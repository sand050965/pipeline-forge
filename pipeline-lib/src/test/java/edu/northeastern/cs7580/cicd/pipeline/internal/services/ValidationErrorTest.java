package edu.northeastern.cs7580.cicd.pipeline.internal.services;


import static org.assertj.core.api.Assertions.assertThat;

import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationError;
import org.junit.jupiter.api.Test;


class ValidationErrorTest {

  @Test
  void shouldCreateErrorWithAllFields() {
    ValidationError error = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    assertThat(error.getFilename()).isEqualTo("test.yaml");
    assertThat(error.getLine()).isEqualTo(5);
    assertThat(error.getColumn()).isEqualTo(10);
    assertThat(error.getMessage()).isEqualTo("Invalid value");
  }

  @Test
  void shouldFormatErrorCorrectly() {
    ValidationError error = ValidationError.builder()
        .filename("pipeline.yaml")
        .line(10)
        .column(20)
        .message("Missing required field")
        .build();

    String formatted = String.format("%s:%d:%d: ERROR, %s",
        error.getFilename(), error.getLine(), error.getColumn(), error.getMessage());

    assertThat(formatted).isEqualTo("pipeline.yaml:10:20: ERROR, Missing required field");
  }

  @Test
  void shouldHandleZeroLineAndColumn() {
    ValidationError error = ValidationError.builder()
        .filename("test.yaml")
        .line(0)
        .column(0)
        .message("General error")
        .build();

    String formatted = String.format("%s:%d:%d: ERROR, %s",
        error.getFilename(), error.getLine(), error.getColumn(), error.getMessage());

    assertThat(formatted).isEqualTo("test.yaml:0:0: ERROR, General error");
  }

  @Test
  void shouldHandlePathWithDirectories() {
    ValidationError error = ValidationError.builder()
        .filename(".pipelines/default.yaml")
        .line(15)
        .column(5)
        .message("Invalid stage name")
        .build();

    assertThat(error.getFilename()).isEqualTo(".pipelines/default.yaml");
  }

  @Test
  void shouldHandleLongErrorMessages() {
    String longMessage = "This is a very long error message that contains lots of details "
        + "about what went wrong in the validation process";

    ValidationError error = ValidationError.builder()
        .filename("test.yaml")
        .line(1)
        .column(1)
        .message(longMessage)
        .build();

    assertThat(error.getMessage()).isEqualTo(longMessage);
  }

  @Test
  void shouldHandleSpecialCharactersInMessage() {
    ValidationError error = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Expected 'String', got 'Integer'")
        .build();

    assertThat(error.getMessage()).contains("'String'");
    assertThat(error.getMessage()).contains("'Integer'");
  }

  @Test
  void shouldSupportBuilderPattern() {
    ValidationError error = ValidationError.builder()
        .filename("test.yaml")
        .line(1)
        .column(1)
        .message("test")
        .build();

    assertThat(error).isNotNull();
    assertThat(error.getFilename()).isNotNull();
    assertThat(error.getMessage()).isNotNull();
  }

  @Test
  void shouldBeEqualWhenAllFieldsMatch() {
    ValidationError error1 = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    ValidationError error2 = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    assertThat(error1).isEqualTo(error2);
    assertThat(error1.hashCode()).isEqualTo(error2.hashCode());
  }

  @Test
  void shouldNotBeEqualWhenFilenamesDiffer() {
    ValidationError error1 = ValidationError.builder()
        .filename("test1.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    ValidationError error2 = ValidationError.builder()
        .filename("test2.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    assertThat(error1).isNotEqualTo(error2);
  }

  @Test
  void shouldNotBeEqualWhenLinesDiffer() {
    ValidationError error1 = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    ValidationError error2 = ValidationError.builder()
        .filename("test.yaml")
        .line(10)
        .column(10)
        .message("Invalid value")
        .build();

    assertThat(error1).isNotEqualTo(error2);
  }

  @Test
  void shouldNotBeEqualWhenColumnsDiffer() {
    ValidationError error1 = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    ValidationError error2 = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(20)
        .message("Invalid value")
        .build();

    assertThat(error1).isNotEqualTo(error2);
  }

  @Test
  void shouldNotBeEqualWhenMessagesDiffer() {
    ValidationError error1 = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    ValidationError error2 = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Different message")
        .build();

    assertThat(error1).isNotEqualTo(error2);
  }

  @Test
  void shouldNotBeEqualToNull() {
    ValidationError error = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    assertThat(error).isNotEqualTo(null);
  }

  @Test
  void shouldNotBeEqualToDifferentType() {
    ValidationError error = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    assertThat(error).isNotEqualTo("not a ValidationError");
  }

  @Test
  void shouldBeEqualToItself() {
    ValidationError error = ValidationError.builder()
        .filename("test.yaml")
        .line(5)
        .column(10)
        .message("Invalid value")
        .build();

    assertThat(error).isEqualTo(error);
    assertThat(error.hashCode()).isEqualTo(error.hashCode());
  }
}