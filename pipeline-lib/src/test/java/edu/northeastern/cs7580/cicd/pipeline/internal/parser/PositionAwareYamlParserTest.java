package edu.northeastern.cs7580.cicd.pipeline.internal.parser;


import static org.assertj.core.api.Assertions.assertThat;

import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser.Position;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class PositionAwareYamlParserTest {

  private PositionAwareYamlParser parser;

  @BeforeEach
  void setUp() {
    parser = new PositionAwareYamlParser();
  }

  @Test
  void shouldParseValidBasicPipeline() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-basic.yaml");
    Map<String, Object> result = parser.parse(file);

    assertThat(result).isNotNull();
    assertThat(result).containsKey("pipeline");

    Map<String, Object> pipeline = (Map<String, Object>) result.get("pipeline");
    assertThat(pipeline.get("name")).isEqualTo("valid-basic");
  }

  @Test
  void shouldTrackKeyPositionsInBasicPipeline() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-basic.yaml");
    parser.parse(file);

    Position pipelinePos = parser.getKeyPosition("pipeline");
    assertThat(pipelinePos).isNotNull();
    assertThat(pipelinePos.getLine()).isGreaterThan(0);

    Position stagesPos = parser.getKeyPosition("stages");
    if (stagesPos != null) {
      assertThat(stagesPos.getLine()).isGreaterThan(0);
    }
  }

  @Test
  void shouldTrackValuePositionsInPipeline() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-basic.yaml");
    parser.parse(file);

    Position namePos = parser.getValuePosition("pipeline.name");
    assertThat(namePos).isNotNull();
    assertThat(namePos.getLine()).isGreaterThan(0);
    assertThat(namePos.getColumn()).isGreaterThan(0);
  }

  @Test
  void shouldParseCustomStages() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-custom-stages.yaml");
    Map<String, Object> result = parser.parse(file);

    assertThat(result.get("stages")).isInstanceOf(List.class);
    List<String> stages = (List<String>) result.get("stages");
    assertThat(stages).isNotEmpty();
  }

  @Test
  void shouldTrackStageListPositions() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-custom-stages.yaml");
    parser.parse(file);

    Position stage0 = parser.getValuePosition("stages[0]");
    assertThat(stage0).isNotNull();
    assertThat(stage0.getLine()).isGreaterThan(0);
  }

  @Test
  void shouldParseComplexPipeline() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-complex.yaml");
    Map<String, Object> result = parser.parse(file);

    assertThat(result).containsKey("pipeline");
    assertThat(result.size()).isGreaterThan(2); // pipeline, stages, and jobs
  }

  @Test
  void shouldTrackJobPositions() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-basic.yaml");
    parser.parse(file);

    // Check for any job key positions
    Map<String, Position> keyPositions = parser.getKeyPositions();
    assertThat(keyPositions).isNotEmpty();

    // Should have at least pipeline and some job keys
    assertThat(keyPositions.keySet())
        .anyMatch(key -> !key.equals("pipeline") && !key.equals("stages"));
  }

  @Test
  void shouldParsePipelineWithNeeds() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-with-needs.yaml");
    Map<String, Object> result = parser.parse(file);

    assertThat(result).containsKey("pipeline");
    // The file should have jobs with needs defined
    assertThat(result.size()).isGreaterThan(1);
  }

  @Test
  void shouldParseDefaultStagesPipeline() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-default-stages.yaml");
    Map<String, Object> result = parser.parse(file);

    assertThat(result).containsKey("pipeline");
    // May or may not have explicit stages key
  }

  @Test
  void shouldParsePipelineWithoutDescription() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-no-description.yaml");
    parser.parse(file);

    Position descPos = parser.getValuePosition("pipeline.description");
    // Description might not exist, which is fine
    if (descPos != null) {
      assertThat(descPos.getLine()).isGreaterThan(0);
    }
  }

  @Test
  void shouldParseDiamondDependency() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-diamond-dependency.yaml");
    Map<String, Object> result = parser.parse(file);

    assertThat(result).isNotNull();
    assertThat(result).containsKey("pipeline");
  }

  @Test
  void shouldParseLinearChain() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-linear-chain.yaml");
    Map<String, Object> result = parser.parse(file);

    assertThat(result).isNotNull();
    assertThat(result).containsKey("pipeline");
  }

  @Test
  void shouldHandleInvalidMissingPipeline() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-yamls/invalid-missing-pipeline.yaml");
    Map<String, Object> result = parser.parse(file);

    // Should still parse, just won't have pipeline key
    assertThat(result).isNotNull();
  }

  @Test
  void shouldHandleInvalidPipelineNotMap() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-yamls/invalid-pipeline-not-map.yaml");
    Map<String, Object> result = parser.parse(file);

    // Should parse without error, validation happens elsewhere
    assertThat(result).isNotNull();
  }

  @Test
  void shouldHandleInvalidMissingName() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-yamls/invalid-missing-name.yaml");
    parser.parse(file);

    Position pipelinePos = parser.getKeyPosition("pipeline");
    assertThat(pipelinePos).isNotNull();
  }

  @Test
  void shouldHandleInvalidWrongType() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-yamls/invalid-wrong-type.yaml");
    Map<String, Object> result = parser.parse(file);

    // Parser should still work, types are checked by validators
    assertThat(result).isNotNull();
  }

  @Test
  void shouldTrackPositionsInInvalidFiles() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-jobs/invalid-missing-stage.yaml");
    parser.parse(file);

    // Should still track positions even in invalid files
    Map<String, Position> positions = parser.getKeyPositions();
    assertThat(positions).isNotEmpty();
  }

  @Test
  void shouldParseFileWithEmptyNeeds() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-needs/invalid-empty-needs.yaml");
    Map<String, Object> result = parser.parse(file);

    assertThat(result).isNotNull();
    assertThat(result).containsKey("pipeline");
  }

  @Test
  void shouldParseFileWithCycle() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-needs/invalid-cycle-simple.yaml");
    Map<String, Object> result = parser.parse(file);

    // Should parse fine, cycle detection happens in validator
    assertThat(result).isNotNull();
  }

  @Test
  void shouldParseFileWithDuplicateStages() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-stages/invalid-duplicate-stages.yaml");
    Map<String, Object> result = parser.parse(file);

    assertThat(result).isNotNull();
    if (result.containsKey("stages")) {
      assertThat(result.get("stages")).isInstanceOf(List.class);
    }
  }

  @Test
  void shouldParseFileWithEmptyStage() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-stages/invalid-empty-stage.yaml");
    Map<String, Object> result = parser.parse(file);

    assertThat(result).isNotNull();
    assertThat(result).containsKey("pipeline");
  }

  @Test
  void shouldReturnNullForNonexistentKey() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-basic.yaml");
    parser.parse(file);

    Position pos = parser.getKeyPosition("this.key.does.not.exist");
    assertThat(pos).isNull();
  }

  @Test
  void shouldReturnNullForNonexistentValue() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-basic.yaml");
    parser.parse(file);

    Position pos = parser.getValuePosition("pipeline.nonexistent.field");
    assertThat(pos).isNull();
  }

  @Test
  void shouldProvideAccessToAllKeyPositions() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-complex.yaml");
    parser.parse(file);

    Map<String, Position> keyPositions = parser.getKeyPositions();
    assertThat(keyPositions).isNotEmpty();
    assertThat(keyPositions).containsKey("pipeline");
  }

  @Test
  void shouldProvideAccessToAllValuePositions() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-complex.yaml");
    parser.parse(file);

    Map<String, Position> valuePositions = parser.getValuePositions();
    assertThat(valuePositions).isNotEmpty();
    assertThat(valuePositions).containsKey("pipeline.name");
  }

  @Test
  void shouldDetectDuplicateJobNames() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-yamls/invalid-duplicate-job-simple.yaml");
    parser.parse(file);

    List<PositionAwareYamlParser.DuplicateError> errors = parser.getDuplicateErrors();

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).getMessage()).contains("Duplicate job name 'compile'");
    assertThat(errors.get(0).getPosition().getLine()).isGreaterThan(0);
  }

  @Test
  void shouldDetectMultipleDuplicateJobNames() throws IOException {
    Path file = getResourcePath("test-fixtures/invalid-yamls/invalid-duplicate-job-multiple.yaml");
    parser.parse(file);

    List<PositionAwareYamlParser.DuplicateError> errors = parser.getDuplicateErrors();

    assertThat(errors).hasSize(2);
    assertThat(errors).extracting(PositionAwareYamlParser.DuplicateError::getMessage)
        .containsExactlyInAnyOrder(
            "Duplicate job name 'job1'",
            "Duplicate job name 'job2'");
  }

  @Test
  void shouldNotReportDuplicatesForValidPipeline() throws IOException {
    Path file = getResourcePath("test-fixtures/valids/valid-basic.yaml");
    parser.parse(file);

    assertThat(parser.getDuplicateErrors()).isEmpty();
  }

  private Path getResourcePath(String resource) {
    try {
      return Paths.get(getClass().getClassLoader()
          .getResource(resource).toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException("Resource not found: " + resource, e);
    }
  }
}