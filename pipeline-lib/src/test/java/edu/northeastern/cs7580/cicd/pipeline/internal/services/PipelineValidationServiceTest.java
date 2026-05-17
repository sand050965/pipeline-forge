package edu.northeastern.cs7580.cicd.pipeline.internal.services;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.PipelineValidationService;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.JobValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.NeedValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.StageValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.YamlValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class PipelineValidationServiceTest {

  private PipelineValidationService validationService;

  @BeforeEach
  void setUp() {
    YamlValidator yamlValidator = new YamlValidator();
    StageValidator stageValidator = new StageValidator();
    JobValidator jobValidator = new JobValidator();
    NeedValidator needValidator = new NeedValidator();

    validationService = new PipelineValidationService(
        yamlValidator,
        stageValidator,
        jobValidator,
        needValidator
    );
  }

  @Test
  void shouldValidateBasicPipeline() {
    Path path = getResourcePath("test-fixtures/valids/valid-basic.yaml");
    Pipeline pipeline = validationService.validateAndParse(path);

    assertThat(pipeline).isNotNull();
    assertThat(pipeline.getPipeline().getName()).isEqualTo("valid-basic");
    assertThat(pipeline.getJobs()).hasSize(2);
  }

  @Test
  void shouldValidateCustomStages() {
    Path path = getResourcePath("test-fixtures/valids/valid-custom-stages.yaml");

    Pipeline pipeline = validationService.validateAndParse(path);

    assertThat(pipeline.getStages()).containsExactly(
        "lint", "compile", "unit-test", "integration-test", "deploy"
    );
    assertThat(pipeline.getJobs()).hasSize(5);
  }

  @Test
  void shouldValidatePipelineWithNeeds() {
    Path path = getResourcePath("test-fixtures/valids/valid-with-needs.yaml");

    Pipeline pipeline = validationService.validateAndParse(path);

    assertThat(pipeline.getJobs().get("test-e2e").getNeeds())
        .containsExactly("test-unit", "test-integration");
  }

  @Test
  void shouldValidateComplexPipeline() {
    Path path = getResourcePath("test-fixtures/valids/valid-complex.yaml");

    Pipeline pipeline = validationService.validateAndParse(path);

    assertThat(pipeline.getJobs()).hasSize(6);
    assertThat(pipeline.getStages()).hasSize(4);
  }

  @Test
  void shouldUseDefaultStages() {
    Path path = getResourcePath("test-fixtures/valids/valid-default-stages.yaml");

    Pipeline pipeline = validationService.validateAndParse(path);

    assertThat(pipeline.getStagesOrDefault())
        .containsExactly("build", "test", "docs");
  }

  @Test
  void shouldValidateWithoutDescription() {
    Path path = getResourcePath("test-fixtures/valids/valid-no-description.yaml");

    Pipeline pipeline = validationService.validateAndParse(path);

    assertThat(pipeline.getPipeline().getDescription()).isEmpty();
  }

  @Test
  void shouldValidateDiamondDependency() {
    Path path = getResourcePath("test-fixtures/valids/valid-diamond-dependency.yaml");

    Pipeline pipeline = validationService.validateAndParse(path);

    assertThat(pipeline.getJobs()).hasSize(4);
  }

  @Test
  void shouldValidateLinearDependencyChain() {
    Path path = getResourcePath("test-fixtures/valids/valid-linear-chain.yaml");

    Pipeline pipeline = validationService.validateAndParse(path);

    assertThat(pipeline.getJobs()).hasSize(4);
  }

  @Test
  void shouldFailOnMissingPipeline() {
    Path path = getResourcePath("test-fixtures/invalid-yamls/invalid-missing-pipeline.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Missing required 'pipeline' section");
  }

  @Test
  void shouldFailOnPipelineNotMap() {
    Path path = getResourcePath("test-fixtures/invalid-yamls/invalid-pipeline-not-map.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("'pipeline' must be a map/object");
  }

  @Test
  void shouldFailOnMissingName() {
    Path path = getResourcePath("test-fixtures/invalid-yamls/invalid-missing-name.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Missing required 'pipeline.name'");
  }

  @Test
  void shouldFailOnNameWrongType() {
    Path path = getResourcePath("test-fixtures/invalid-yamls/invalid-wrong-type.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Wrong type");
  }

  @Test
  void shouldFailOnDescriptionWrongType() {
    Path path = getResourcePath("test-fixtures/invalid-yamls/invalid-description-wrong-type.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Wrong type");
  }

  @Test
  void shouldPassWhenAllPipelineNamesAreUnique() {
    Path directory = getResourcePath("test-fixtures/valids");

    assertThatCode(() -> validationService.validateDirectory(directory))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldFailOnDuplicatePipelineNames() {
    Path directory = getResourcePath("test-fixtures/Invalid-duplicate-names");

    assertThatThrownBy(() -> validationService.validateDirectory(directory))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Duplicate pipeline name 'default'")
        .hasMessageContaining("ERROR");
  }

  @Test
  void shouldFailOnDuplicateStages() {
    Path path = getResourcePath("test-fixtures/invalid-stages/invalid-duplicate-stages.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Duplicate stage name");
  }

  @Test
  void shouldFailOnEmptyStage() {
    Path path = getResourcePath("test-fixtures/invalid-stages/invalid-empty-stage.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("has no jobs");
  }

  @Test
  void shouldFailOnNoStages() {
    Path path = getResourcePath("test-fixtures/invalid-stages/invalid-no-stages.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("has no jobs");
  }

  @Test
  void shouldFailOnMissingStageField() {
    Path path = getResourcePath("test-fixtures/invalid-jobs/invalid-missing-stage.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("missing required 'stage'");
  }

  @Test
  void shouldFailOnMissingImage() {
    Path path = getResourcePath("test-fixtures/invalid-jobs/invalid-missing-image.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("missing required 'image'");
  }

  @Test
  void shouldFailOnMissingScript() {
    Path path = getResourcePath("test-fixtures/invalid-jobs/invalid-missing-script.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("missing required 'script'");
  }

  @Test
  void shouldFailOnUndefinedStage() {
    Path path = getResourcePath("test-fixtures/invalid-jobs/invalid-undefined-stage.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("unknown stage");
  }

  @Test
  void shouldFailOnEmptyNeeds() {
    Path path = getResourcePath("test-fixtures/invalid-needs/invalid-empty-needs.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("empty 'needs' list");
  }

  @Test
  void shouldFailOnUndefinedJobInNeeds() {
    Path path = getResourcePath("test-fixtures/invalid-needs/invalid-job-needs.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("non-existent job");
  }

  @Test
  void shouldFailOnNeedsDifferentStage() {
    Path path = getResourcePath("test-fixtures/invalid-needs/invalid-needs-different-stage.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("same stage");
  }

  @Test
  void shouldFailOnSimpleCircularDependency() {
    Path path = getResourcePath("test-fixtures/invalid-needs/invalid-cycle-simple.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cycle detected")
        .hasMessageContaining("job1")
        .hasMessageContaining("job2");
  }

  @Test
  void shouldFailOnComplexCircularDependency() {
    Path path = getResourcePath("test-fixtures/invalid-needs/invalid-cycle-complex.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cycle detected")
        .satisfies(e -> {
          String message = e.getMessage();
          assertThat(message).contains("job1");
          assertThat(message).contains("job2");
          assertThat(message).contains("job3");
        });
  }

  @Test
  void shouldFailOnSelfDependency() {
    Path path = getResourcePath("test-fixtures/invalid-needs/invalid-cycle-self.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cycle detected")
        .hasMessageContaining("job1");
  }

  @Test
  void shouldDetectCycleOnlyInAffectedStage() {
    Path path = getResourcePath("test-fixtures/invalid-needs/invalid-cycle-one-stage.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cycle detected")
        .hasMessageContaining("test1")
        .hasMessageContaining("test2");
  }

  private Path getResourcePath(String resource) {
    try {
      return Paths.get(getClass().getClassLoader()
          .getResource(resource).toURI());
    } catch (Exception e) {
      throw new RuntimeException("Resource not found: " + resource, e);
    }
  }

  @Test
  void shouldValidateDirectoryWithNoYamlFiles() {
    Path emptyDir = createTempDirectory();

    assertThatThrownBy(() -> validationService.validateDirectory(emptyDir))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("No YAML files found");
  }

  @Test
  void shouldHandleExceptionDuringParsing() {
    Path nonExistentFile = Path.of("nonexistent-directory/missing-file.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(nonExistentFile))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void shouldHandleMalformedYamlSyntax() {
    Path path = getResourcePath("test-fixtures/invalid-yamls/malformed-syntax.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("ERROR");
  }

  @Test
  void shouldValidateFileWithCheckUniquenessTrue() {
    Path path = getResourcePath("test-fixtures/valids/valid-basic.yaml");

    // This calls validateFile with checkUniqueness=true (via validateAndParse)
    Pipeline pipeline = validationService.validateAndParse(path);

    assertThat(pipeline).isNotNull();
  }

  @Test
  void shouldCollectMultipleValidationErrors() {
    Path path = getResourcePath("test-fixtures/invalid-jobs/invalid-multiple-errors.yaml");

    // File should have multiple errors (missing stage, missing image, etc.)
    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .satisfies(e -> {
          String message = e.getMessage();
          // Should contain multiple error lines
          assertThat(message.split("\n").length).isGreaterThan(1);
        });
  }

  @Test
  void shouldHandleEmptyYamlFile() {
    Path path = getResourcePath("test-fixtures/invalid-yamls/empty.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void shouldHandleYamlWithOnlyComments() {
    Path path = getResourcePath("test-fixtures/invalid-yamls/only-comments.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void shouldHandleNullStagesInPipeline() {
    Path path = getResourcePath("test-fixtures/valids/valid-default-stages.yaml");

    Pipeline pipeline = validationService.validateAndParse(path);

    // Should use default stages when stages is null
    assertThat(pipeline.getStagesOrDefault()).isNotEmpty();
  }

  @Test
  void shouldValidateScriptAsString() {
    Path path = getResourcePath("test-fixtures/valids/valid-script-string.yaml");

    Pipeline pipeline = validationService.validateAndParse(path);
    Job job = pipeline.getJobs().values().iterator().next();

    assertThat(job.getScript()).isInstanceOf(String.class);
  }

  @Test
  void shouldValidateScriptAsList() {
    Path path = getResourcePath("test-fixtures/valids/valid-script-list.yaml");

    Pipeline pipeline = validationService.validateAndParse(path);
    Job job = pipeline.getJobs().values().iterator().next();

    assertThat(job.getScript()).isInstanceOf(List.class);
  }

  @Test
  void shouldHandleClassCastException() {
    Path path = getResourcePath("test-fixtures/invalid-yamls/invalid-pipeline-not-map.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("ERROR")
        .satisfies(e -> {
          assertThat(e.getMessage()).doesNotContain("ClassCastException");
          assertThat(e.getMessage()).doesNotContain("at edu.northeastern");
        });
  }

  @Test
  void shouldHandleGenericException() {
    Path nonExistentFile = Path.of("completely/nonexistent/path/file.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(nonExistentFile))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("ERROR");
  }

  @Test
  void shouldFailOnEmptyYamlFile() {
    Path path = getResourcePath("test-fixtures/invalid-yamls/empty.yaml");

    assertThatThrownBy(() -> validationService.validateAndParse(path))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("YAML file is empty or invalid");
  }

  @Test
  void shouldValidateFileWithCheckUniquenessFalse() {
    Path path = getResourcePath("test-fixtures/valids/valid-basic.yaml");

    Pipeline pipeline = validationService.validateFile(path, false);

    assertThat(pipeline).isNotNull();
  }

  private Path createTempDirectory() {
    try {
      return Files.createTempDirectory("test-validation");
    } catch (Exception e) {
      throw new RuntimeException("Failed to create temp directory", e);
    }
  }
}
