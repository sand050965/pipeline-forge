package edu.northeastern.cs7580.cicd.pipeline.internal.validators;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationErrorCollector;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.StageValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.PipelineMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class StageValidatorTest {

  private StageValidator validator;
  private Path testPath;
  private PositionAwareYamlParser parser;
  private ValidationErrorCollector errorCollector;

  @BeforeEach
  void setUp() {
    validator = new StageValidator();
    testPath = Path.of("test.yaml");
    parser = new PositionAwareYamlParser();
    errorCollector = new ValidationErrorCollector(testPath);
  }

  @Test
  void shouldPassWithValidStages() {
    Pipeline pipeline = createPipeline(
        List.of("build", "test"),
        Map.of(
            "job1", createJob("job1", "build"),
            "job2", createJob("job2", "test")
        )
    );

    assertThatCode(() -> {
      validator.validateStages(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldUseDefaultStagesWhenNotSpecified() {
    Pipeline pipeline = createPipeline(
        null,
        Map.of(
            "job1", createJob("job1", "build"),
            "job2", createJob("job2", "test"),
            "job3", createJob("job3", "docs")
        )
    );

    assertThatCode(() -> {
      validator.validateStages(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenDuplicateStageNames() {
    Pipeline pipeline = createPipeline(
        List.of("build", "test", "build"),
        Map.of(
            "job1", createJob("job1", "build")
        )
    );

    validator.validateStages(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Duplicate stage name");
  }

  @Test
  void shouldFailWhenStageHasNoJobs() {
    Pipeline pipeline = createPipeline(
        List.of("build", "test"),
        Map.of(
            "job1", createJob("job1", "build")
        )
    );

    validator.validateStages(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("has no jobs");
  }

  @Test
  void shouldFailWhenMultipleStagesHaveNoJobs() {
    Pipeline pipeline = createPipeline(
        List.of("build", "test", "deploy"),
        Map.of(
            "job1", createJob("job1", "build")
        )
    );

    validator.validateStages(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThat(errorCollector.getErrors()).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void shouldPassWithSingleStage() {
    Pipeline pipeline = createPipeline(
        List.of("build"),
        Map.of(
            "job1", createJob("job1", "build")
        )
    );

    assertThatCode(() -> {
      validator.validateStages(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldPassWithMultipleJobsInSameStage() {
    Pipeline pipeline = createPipeline(
        List.of("build"),
        Map.of(
            "job1", createJob("job1", "build"),
            "job2", createJob("job2", "build"),
            "job3", createJob("job3", "build")
        )
    );

    assertThatCode(() -> {
      validator.validateStages(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  private Pipeline createPipeline(List<String> stages, Map<String, Job> jobs) {
    return Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(stages)
        .jobs(jobs)
        .build();
  }

  private Job createJob(String name, String stage) {
    return Job.builder()
        .name(name)
        .stage(stage)
        .image("alpine:latest")
        .script("echo test")
        .build();
  }
}