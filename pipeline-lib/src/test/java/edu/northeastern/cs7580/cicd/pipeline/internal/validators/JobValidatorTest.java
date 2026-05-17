package edu.northeastern.cs7580.cicd.pipeline.internal.validators;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationErrorCollector;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.JobValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.PipelineMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobValidatorTest {

  private JobValidator validator;
  private Path testPath;
  private PositionAwareYamlParser parser;
  private ValidationErrorCollector errorCollector;

  @BeforeEach
  void setUp() {
    validator = new JobValidator();
    testPath = Path.of("test.yaml");
    parser = new PositionAwareYamlParser();
    errorCollector = new ValidationErrorCollector(testPath);
  }

  @Test
  void shouldPassWithValidJobs() {
    Pipeline pipeline = createPipeline(
        List.of("build"),
        Map.of(
            "job1", createValidJob("job1", "build")
        )
    );

    assertThatCode(() -> {
      validator.validateJobs(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenMissingStage() {
    Job job = Job.builder()
        .name("job1")
        .stage(null)
        .image("alpine:latest")
        .script("echo test")
        .build();

    Pipeline pipeline = createPipeline(
        List.of("build"),
        Map.of("job1", job)
    );

    validator.validateJobs(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("missing required 'stage'");
  }

  @Test
  void shouldFailWhenMissingImage() {
    Job job = Job.builder()
        .name("job1")
        .stage("build")
        .image(null)
        .script("echo test")
        .build();

    Pipeline pipeline = createPipeline(
        List.of("build"),
        Map.of("job1", job)
    );

    validator.validateJobs(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("missing required 'image'");
  }

  @Test
  void shouldFailWhenMissingScript() {
    Job job = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script(null)
        .build();

    Pipeline pipeline = createPipeline(
        List.of("build"),
        Map.of("job1", job)
    );

    validator.validateJobs(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("missing required 'script'");
  }

  @Test
  void shouldAcceptScriptAsString() {
    Job job = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    Pipeline pipeline = createPipeline(
        List.of("build"),
        Map.of("job1", job)
    );

    assertThatCode(() -> {
      validator.validateJobs(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptScriptAsList() {
    Job job = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script(List.of("echo test1", "echo test2"))
        .build();

    Pipeline pipeline = createPipeline(
        List.of("build"),
        Map.of("job1", job)
    );

    assertThatCode(() -> {
      validator.validateJobs(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldCollectMultipleErrors() {
    Job job1 = Job.builder()
        .name("job1")
        .stage(null)  // Missing stage
        .image(null)  // Missing image
        .script(null) // Missing script
        .build();

    Pipeline pipeline = createPipeline(
        List.of("build"),
        Map.of("job1", job1)
    );

    validator.validateJobs(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThat(errorCollector.getErrors()).hasSizeGreaterThanOrEqualTo(3);
  }

  private Pipeline createPipeline(List<String> stages, Map<String, Job> jobs) {
    return Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(stages)
        .jobs(jobs)
        .build();
  }

  private Job createValidJob(String name, String stage) {
    return Job.builder()
        .name(name)
        .stage(stage)
        .image("alpine:latest")
        .script("echo test")
        .build();
  }
}