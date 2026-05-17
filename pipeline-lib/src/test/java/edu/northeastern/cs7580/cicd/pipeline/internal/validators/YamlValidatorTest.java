package edu.northeastern.cs7580.cicd.pipeline.internal.validators;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationErrorCollector;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.YamlValidator;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class YamlValidatorTest {

  private YamlValidator validator;
  private Path testPath;
  private PositionAwareYamlParser parser;
  private ValidationErrorCollector errorCollector;

  @BeforeEach
  void setUp() {
    validator = new YamlValidator();
    testPath = Path.of("test.yaml");
    parser = new PositionAwareYamlParser();
    errorCollector = new ValidationErrorCollector(testPath);
  }

  @Test
  void shouldPassWithValidBasicStructure() {
    Map<String, Object> yamlData = createValidYamlData("test-pipeline", "Test description");

    assertThatCode(() -> {
      validator.validateBasicStructure(yamlData, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldPassWithValidStructureWithoutDescription() {
    Map<String, Object> pipelineSection = new HashMap<>();
    pipelineSection.put("name", "test-pipeline");

    Map<String, Object> yamlData = new HashMap<>();
    yamlData.put("pipeline", pipelineSection);

    assertThatCode(() -> {
      validator.validateBasicStructure(yamlData, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenMissingPipelineSection() {
    Map<String, Object> yamlData = new HashMap<>();
    yamlData.put("stages", List.of("build", "test"));

    validator.validateBasicStructure(yamlData, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Missing required 'pipeline' section");
  }

  @Test
  void shouldFailWhenPipelineIsNotMap() {
    Map<String, Object> yamlData = new HashMap<>();
    yamlData.put("pipeline", "not a map");

    validator.validateBasicStructure(yamlData, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("'pipeline' must be a map/object");
  }

  @Test
  void shouldFailWhenPipelineIsNull() {
    Map<String, Object> yamlData = new HashMap<>();
    yamlData.put("pipeline", null);

    validator.validateBasicStructure(yamlData, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("'pipeline' must be a map/object");
  }

  @Test
  void shouldFailWhenMissingPipelineName() {
    Map<String, Object> pipelineSection = new HashMap<>();
    pipelineSection.put("description", "Missing name");

    Map<String, Object> yamlData = new HashMap<>();
    yamlData.put("pipeline", pipelineSection);

    validator.validateBasicStructure(yamlData, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Missing required 'pipeline.name'");
  }

  @Test
  void shouldFailWhenPipelineNameIsNotString() {
    Map<String, Object> pipelineSection = new HashMap<>();
    pipelineSection.put("name", 123);

    Map<String, Object> yamlData = new HashMap<>();
    yamlData.put("pipeline", pipelineSection);

    validator.validateBasicStructure(yamlData, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Wrong type")
        .hasMessageContaining("pipeline.name");
  }

  @Test
  void shouldFailWhenDescriptionIsNotString() {
    Map<String, Object> pipelineSection = new HashMap<>();
    pipelineSection.put("name", "test-pipeline");
    pipelineSection.put("description", 456);

    Map<String, Object> yamlData = new HashMap<>();
    yamlData.put("pipeline", pipelineSection);

    validator.validateBasicStructure(yamlData, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Wrong type")
        .hasMessageContaining("pipeline.description");
  }

  @Test
  void shouldCollectMultipleErrors() {
    Map<String, Object> pipelineSection = new HashMap<>();
    pipelineSection.put("name", 123);  // Wrong type
    pipelineSection.put("description", true);  // Wrong type

    Map<String, Object> yamlData = new HashMap<>();
    yamlData.put("pipeline", pipelineSection);

    validator.validateBasicStructure(yamlData, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThat(errorCollector.getErrors()).hasSizeGreaterThanOrEqualTo(2);
  }

  private Map<String, Object> createValidYamlData(String name, String description) {
    Map<String, Object> pipelineSection = new HashMap<>();
    pipelineSection.put("name", name);
    if (description != null) {
      pipelineSection.put("description", description);
    }

    Map<String, Object> yamlData = new HashMap<>();
    yamlData.put("pipeline", pipelineSection);
    return yamlData;
  }
}