package edu.northeastern.cs7580.cicd.pipeline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineServiceFactory;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class PipelineServiceIntegrationTest {

  private PipelineService service;

  @BeforeEach
  void setUp() {
    service = PipelineServiceFactory.create();
  }

  @Test
  void shouldValidateValidPipeline() {
    Path file = getResourcePath("test-fixtures/valids/valid-basic.yaml");

    Pipeline pipeline = service.validatePipeline(file);

    assertThat(pipeline).isNotNull();
    assertThat(pipeline.getPipeline().getName()).isNotEmpty();
  }

  @Test
  void shouldThrowValidationExceptionForInvalidPipeline() {
    Path file = getResourcePath("test-fixtures/invalid-yamls/invalid-missing-name.yaml");

    assertThatThrownBy(() -> service.validatePipeline(file))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("ERROR");
  }

  @Test
  void shouldCreateExecutionPlanWithStagesAndDependencies() {
    Path file = getResourcePath("test-fixtures/valids/valid-basic.yaml");

    ExecutionPlan plan = service.createExecutionPlan(file);

    assertThat(plan).isNotNull();
    assertThat(plan.getStages()).isNotEmpty();
    assertThat(plan.getJobDependencies()).isNotNull();
  }

  @Test
  void shouldValidateDirectorySuccessfully() {
    Path dir = getResourcePath("test-fixtures/valids");

    assertThatCode(() -> service.validateDirectory(dir))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptFailuresTrue() {
    Path file = getResourcePath("test-fixtures/valids/valid-allow-failures-true.yaml");

    Pipeline pipeline = service.validatePipeline(file);

    assertThat(pipeline.getJobs().get("build-job").isFailures()).isTrue();
  }

  @Test
  void shouldAcceptFailuresFalse() {
    Path file = getResourcePath("test-fixtures/valids/valid-allow-failures-false.yaml");

    Pipeline pipeline = service.validatePipeline(file);

    assertThat(pipeline.getJobs().get("build-job").isFailures()).isFalse();
  }

  @Test
  void shouldDefaultFailuresToFalseWhenAbsent() {
    Path file = getResourcePath("test-fixtures/valids/valid-basic.yaml");

    Pipeline pipeline = service.validatePipeline(file);

    assertThat(pipeline.getJobs().get("build-job").isFailures()).isFalse();
  }

  @Test
  void shouldRejectFailuresWithNonBooleanValue() {
    Path file = getResourcePath(
        "test-fixtures/invalid-yamls/invalid-allow-failures-wrong-type.yaml");

    assertThatThrownBy(() -> service.validatePipeline(file))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("failures")
        .hasMessageContaining("ERROR");
  }

  private Path getResourcePath(String path) {
    try {
      return Path.of(getClass().getClassLoader().getResource(path).toURI());
    } catch (Exception e) {
      throw new RuntimeException("Resource not found: " + path, e);
    }
  }
}