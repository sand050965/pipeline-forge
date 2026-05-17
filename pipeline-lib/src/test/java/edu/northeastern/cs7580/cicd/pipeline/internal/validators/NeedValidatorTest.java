package edu.northeastern.cs7580.cicd.pipeline.internal.validators;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationErrorCollector;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.NeedValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.PipelineMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NeedValidatorTest {

  private NeedValidator validator;
  private Path testPath;
  private PositionAwareYamlParser parser;
  private ValidationErrorCollector errorCollector;

  @BeforeEach
  void setUp() {
    validator = new NeedValidator();
    testPath = Path.of("test.yaml");
    parser = new PositionAwareYamlParser();
    errorCollector = new ValidationErrorCollector(testPath);
  }

  @Test
  void shouldPassWhenNoNeeds() {
    Pipeline pipeline = createPipeline(
        List.of("test"),
        Map.of(
            "job1", createJob("job1", "test", null)
        )
    );

    assertThatCode(() -> {
      validator.validateNeeds(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldPassWhenValidNeeds() {
    Pipeline pipeline = createPipeline(
        List.of("test"),
        Map.of(
            "job1", createJob("job1", "test", null),
            "job2", createJob("job2", "test", List.of("job1"))
        )
    );

    assertThatCode(() -> {
      validator.validateNeeds(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldFailWhenNeededJobDoesNotExist() {
    Pipeline pipeline = createPipeline(
        List.of("test"),
        Map.of(
            "job1", createJob("job1", "test", List.of("job2"))
        )
    );

    validator.validateNeeds(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("non-existent job")
        .hasMessageContaining("job2");
  }

  @Test
  void shouldFailWhenNeededJobInDifferentStage() {
    Pipeline pipeline = createPipeline(
        List.of("build", "test"),
        Map.of(
            "job1", createJob("job1", "build", null),
            "job2", createJob("job2", "test", List.of("job1"))
        )
    );

    validator.validateNeeds(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("same stage");
  }

  @Test
  void shouldFailWhenEmptyNeeds() {
    Pipeline pipeline = createPipeline(
        List.of("test"),
        Map.of(
            "job1", createJob("job1", "test", List.of())
        )
    );

    validator.validateNeeds(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("empty 'needs' list");
  }

  @Test
  void shouldFailOnSimpleCircularDependency() {
    Pipeline pipeline = createPipeline(
        List.of("test"),
        Map.of(
            "job1", createJob("job1", "test", List.of("job2")),
            "job2", createJob("job2", "test", List.of("job1"))
        )
    );

    validator.validateNeeds(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cycle detected")
        .hasMessageContaining("job1")
        .hasMessageContaining("job2");
  }

  @Test
  void shouldFailOnComplexCircularDependency() {
    Pipeline pipeline = createPipeline(
        List.of("test"),
        Map.of(
            "job1", createJob("job1", "test", List.of("job3")),
            "job2", createJob("job2", "test", List.of("job1")),
            "job3", createJob("job3", "test", List.of("job2"))
        )
    );

    validator.validateNeeds(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cycle detected");
  }

  @Test
  void shouldPassOnDiamondDependency() {
    Pipeline pipeline = createPipeline(
        List.of("test"),
        Map.of(
            "job1", createJob("job1", "test", null),
            "job2", createJob("job2", "test", List.of("job1")),
            "job3", createJob("job3", "test", List.of("job1")),
            "job4", createJob("job4", "test", List.of("job2", "job3"))
        )
    );

    assertThatCode(() -> {
      validator.validateNeeds(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldPassOnLinearDependencyChain() {
    Pipeline pipeline = createPipeline(
        List.of("test"),
        Map.of(
            "job1", createJob("job1", "test", null),
            "job2", createJob("job2", "test", List.of("job1")),
            "job3", createJob("job3", "test", List.of("job2")),
            "job4", createJob("job4", "test", List.of("job3"))
        )
    );

    assertThatCode(() -> {
      validator.validateNeeds(pipeline, parser, errorCollector);
      errorCollector.throwIfHasErrors();
    }).doesNotThrowAnyException();
  }

  @Test
  void shouldFailOnSelfDependency() {
    Pipeline pipeline = createPipeline(
        List.of("test"),
        Map.of(
            "job1", createJob("job1", "test", List.of("job1"))
        )
    );

    validator.validateNeeds(pipeline, parser, errorCollector);

    assertThat(errorCollector.hasErrors()).isTrue();
    assertThatThrownBy(() -> errorCollector.throwIfHasErrors())
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Cycle detected")
        .hasMessageContaining("job1");
  }

  private Pipeline createPipeline(List<String> stages, Map<String, Job> jobs) {
    return Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(stages)
        .jobs(jobs)
        .build();
  }

  private Job createJob(String name, String stage, List<String> needs) {
    return Job.builder()
        .name(name)
        .stage(stage)
        .image("alpine:latest")
        .script("echo test")
        .needs(needs)
        .build();
  }
}