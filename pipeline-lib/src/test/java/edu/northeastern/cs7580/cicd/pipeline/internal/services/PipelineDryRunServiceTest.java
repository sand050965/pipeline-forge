package edu.northeastern.cs7580.cicd.pipeline.internal.services;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.builder.ExecutionPlanBuilder;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.PipelineDryRunService;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.PipelineValidationService;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.PipelineMetadata;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineDryRunServiceTest {

  @Mock
  private PipelineValidationService validationService;

  @Mock
  private ExecutionPlanBuilder executionPlanBuilder;

  private PipelineDryRunService dryRunService;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    dryRunService = new PipelineDryRunService(validationService, executionPlanBuilder);
  }

  @Test
  void testBuildExecutionPlanSuccess() throws ValidationException, IOException {
    Path configFile = tempDir.resolve("pipeline.yaml");
    Files.writeString(configFile, "pipeline:\n  name: test\n");

    Job job = Job.builder()
        .name("compile")
        .stage("build")
        .image("gradle:8.12-jdk21")
        .script("./gradlew build")
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("compile", job);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("build"))
        .jobs(jobs)
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    ExecutionPlan expectedPlan = ExecutionPlan.builder()
        .stages(Collections.singletonList(stage))
        .build();

    when(validationService.validateAndParse(configFile)).thenReturn(pipeline);
    when(executionPlanBuilder.build(pipeline)).thenReturn(expectedPlan);

    ExecutionPlan result = dryRunService.buildExecutionPlan(configFile);

    assertNotNull(result);
    assertEquals(1, result.getStages().size());
    assertEquals("build", result.getStages().get(0).getStageName());

    verify(validationService, times(1)).validateAndParse(configFile);
    verify(executionPlanBuilder, times(1)).build(pipeline);
  }

  @Test
  void testBuildExecutionPlanWithValidationFailure() throws ValidationException, IOException {
    Path configFile = tempDir.resolve("invalid.yaml");
    Files.writeString(configFile, "invalid: yaml\n");

    ValidationException validationException = new ValidationException("Validation failed");
    when(validationService.validateAndParse(configFile)).thenThrow(validationException);

    assertThrows(ValidationException.class, () -> {
      dryRunService.buildExecutionPlan(configFile);
    });

    verify(validationService, times(1)).validateAndParse(configFile);
    verify(executionPlanBuilder, never()).build(any());
  }

  @Test
  void testBuildExecutionPlanWithException() throws ValidationException {
    Path nonExistentFile = tempDir.resolve("nonexistent.yaml");

    when(validationService.validateAndParse(nonExistentFile))
        .thenThrow(new ValidationException("File not found: " + nonExistentFile));

    assertThrows(ValidationException.class, () -> {
      dryRunService.buildExecutionPlan(nonExistentFile);
    });

    verify(validationService, times(1)).validateAndParse(nonExistentFile);
    verify(executionPlanBuilder, never()).build(any());
  }


  @Test
  void testBuildExecutionPlanWithMultipleStages() throws ValidationException, IOException {
    Path configFile = tempDir.resolve("pipeline.yaml");
    Files.writeString(configFile, "pipeline:\n  name: test\n");

    Job buildJob = Job.builder()
        .name("compile")
        .stage("build")
        .image("gradle:8.12-jdk21")
        .script("./gradlew build")
        .build();

    Job testJob = Job.builder()
        .name("test")
        .stage("test")
        .image("gradle:8.12-jdk21")
        .script("./gradlew test")
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("compile", buildJob);
    jobs.put("test", testJob);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("build"))
        .jobs(jobs)
        .build();

    StageExecution buildStage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(buildJob))
        .build();

    StageExecution testStage = StageExecution.builder()
        .stageName("test")
        .jobs(Collections.singletonList(testJob))
        .build();

    ExecutionPlan expectedPlan = ExecutionPlan.builder()
        .stages(java.util.Arrays.asList(buildStage, testStage))
        .build();

    when(validationService.validateAndParse(configFile)).thenReturn(pipeline);
    when(executionPlanBuilder.build(pipeline)).thenReturn(expectedPlan);

    ExecutionPlan result = dryRunService.buildExecutionPlan(configFile);

    assertNotNull(result);
    assertEquals(2, result.getStages().size());
    assertEquals("build", result.getStages().get(0).getStageName());
    assertEquals("test", result.getStages().get(1).getStageName());

    verify(validationService, times(1)).validateAndParse(configFile);
    verify(executionPlanBuilder, times(1)).build(pipeline);
  }

  @Test
  void testBuildExecutionPlanCallsServicesInCorrectOrder() throws ValidationException, IOException {
    Path configFile = tempDir.resolve("pipeline.yaml");
    Files.writeString(configFile, "pipeline:\n  name: test\n");

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("build"))
        .jobs(new HashMap<>())
        .build();

    ExecutionPlan expectedPlan = ExecutionPlan.builder()
        .stages(Collections.emptyList())
        .build();

    when(validationService.validateAndParse(configFile)).thenReturn(pipeline);
    when(executionPlanBuilder.build(pipeline)).thenReturn(expectedPlan);

    dryRunService.buildExecutionPlan(configFile);

    var inOrder = inOrder(validationService, executionPlanBuilder);
    inOrder.verify(validationService).validateAndParse(configFile);
    inOrder.verify(executionPlanBuilder).build(pipeline);
  }

  @Test
  void testBuildExecutionPlanWithComplexPipeline() throws ValidationException, IOException {
    Path configFile = tempDir.resolve("complex.yaml");
    Files.writeString(configFile, "pipeline:\n  name: complex\n");

    Job unitTest = Job.builder()
        .name("unit-tests")
        .stage("test")
        .image("gradle:8.12-jdk21")
        .script("./gradlew test")
        .build();

    Job integrationTest = Job.builder()
        .name("integration-tests")
        .stage("test")
        .image("gradle:8.12-jdk21")
        .script("./gradlew integrationTest")
        .needs(Collections.singletonList("unit-tests"))
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("unit-tests", unitTest);
    jobs.put("integration-tests", integrationTest);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("complex").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    StageExecution testStage = StageExecution.builder()
        .stageName("test")
        .jobs(java.util.Arrays.asList(unitTest, integrationTest))
        .build();

    ExecutionPlan expectedPlan = ExecutionPlan.builder()
        .stages(Collections.singletonList(testStage))
        .build();

    when(validationService.validateAndParse(configFile)).thenReturn(pipeline);
    when(executionPlanBuilder.build(pipeline)).thenReturn(expectedPlan);

    ExecutionPlan result = dryRunService.buildExecutionPlan(configFile);

    assertNotNull(result);
    assertEquals(1, result.getStages().size());
    assertEquals(2, result.getStages().get(0).getJobs().size());

    verify(validationService, times(1)).validateAndParse(configFile);
    verify(executionPlanBuilder, times(1)).build(pipeline);
  }
}