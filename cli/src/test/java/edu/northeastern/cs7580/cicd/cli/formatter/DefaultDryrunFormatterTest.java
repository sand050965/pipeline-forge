package edu.northeastern.cs7580.cicd.cli.formatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultDryrunFormatterTest {

  @Test
  void formatPrintsStageAndJobHierarchy() {
    Pipeline pipeline = Pipeline.builder().build();

    Job compile = Job.builder().name("compile").build();
    StageExecution buildStage = StageExecution.builder()
        .stageName("build")
        .jobs(List.of(compile))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(buildStage))
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = ""
        + "build:\n"
        + "    compile:\n"
        + "        failures: false\n";

    assertEquals(expected, actual);
  }

  @Test
  void formatPrintsImageAndScript() {
    Pipeline pipeline = Pipeline.builder().build();

    Job compile = Job.builder()
        .name("compile")
        .image("gradle:8.12-jdk21")
        .script(List.of("./gradlew classes"))
        .build();

    StageExecution buildStage = StageExecution.builder()
        .stageName("build")
        .jobs(List.of(compile))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(buildStage))
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = ""
        + "build:\n"
        + "    compile:\n"
        + "        image: gradle:8.12-jdk21\n"
        + "        script:\n"
        + "            - ./gradlew classes\n"
        + "        failures: false\n";

    assertEquals(expected, actual);
  }

  @Test
  void formatPrintsMultipleStagesAndJobs() {
    Pipeline pipeline = Pipeline.builder().build();

    Job compile = Job.builder()
        .name("compile")
        .image("gradle:8.12-jdk21")
        .script(List.of("./gradlew classes"))
        .build();

    Job test = Job.builder()
        .name("test")
        .image("gradle:8.12-jdk21")
        .script(List.of("./gradlew test"))
        .build();

    StageExecution buildStage = StageExecution.builder()
        .stageName("build")
        .jobs(List.of(compile))
        .build();

    StageExecution testStage = StageExecution.builder()
        .stageName("test")
        .jobs(List.of(test))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(buildStage, testStage))
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = ""
        + "build:\n"
        + "    compile:\n"
        + "        image: gradle:8.12-jdk21\n"
        + "        script:\n"
        + "            - ./gradlew classes\n"
        + "        failures: false\n"
        + "test:\n"
        + "    test:\n"
        + "        image: gradle:8.12-jdk21\n"
        + "        script:\n"
        + "            - ./gradlew test\n"
        + "        failures: false\n";

    assertEquals(expected, actual);
  }

  @Test
  void formatPrintsNeedsList() {
    Pipeline pipeline = Pipeline.builder().build();

    Job testJob = Job.builder()
        .name("test")
        .image("alpine:latest")
        .needs(List.of("compile", "lint"))
        .script(List.of("echo test"))
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("test")
        .jobs(List.of(testJob))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(stage))
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = ""
        + "test:\n"
        + "    test:\n"
        + "        image: alpine:latest\n"
        + "        script:\n"
        + "            - echo test\n"
        + "        needs:\n"
        + "            - compile\n"
        + "            - lint\n"
        + "        failures: false\n";

    assertEquals(expected, actual);
  }

  @Test
  void formatReturnsEmptyStringWhenNoStages() {
    Pipeline pipeline = Pipeline.builder().build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of())
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    assertEquals("", actual);
  }

  @Test
  void formatSkipsNullStageExecution() {
    Pipeline pipeline = Pipeline.builder().build();

    StageExecution validStage = StageExecution.builder()
        .stageName("build")
        .jobs(List.of(
            Job.builder()
                .name("compile")
                .image("alpine:latest")
                .script(List.of("echo build"))
                .build()
        ))
        .build();

    List<StageExecution> stages = java.util.Arrays.asList(null, validStage);

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(stages)
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = ""
        + "build:\n"
        + "    compile:\n"
        + "        image: alpine:latest\n"
        + "        script:\n"
        + "            - echo build\n"
        + "        failures: false\n";

    assertEquals(expected, actual);
  }

  @Test
  void formatSkipsStageWithBlankName() {
    Pipeline pipeline = Pipeline.builder().build();

    StageExecution invalidStage = StageExecution.builder()
        .stageName("   ")
        .jobs(List.of(
            Job.builder().name("x").build()
        ))
        .build();

    StageExecution validStage = StageExecution.builder()
        .stageName("build")
        .jobs(List.of(
            Job.builder()
                .name("compile")
                .image("alpine:latest")
                .script(List.of("echo build"))
                .build()
        ))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(invalidStage, validStage))
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = ""
        + "build:\n"
        + "    compile:\n"
        + "        image: alpine:latest\n"
        + "        script:\n"
        + "            - echo build\n"
        + "        failures: false\n";

    assertEquals(expected, actual);
  }

  @Test
  void formatSkipsStageWithNoJobs() {
    Pipeline pipeline = Pipeline.builder().build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(List.of())
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(stage))
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = "build:\n";

    assertEquals(expected, actual);
  }

  @Test
  void formatSkipsNullJob() {
    Pipeline pipeline = Pipeline.builder().build();

    Job validJob = Job.builder()
        .name("compile")
        .image("alpine:latest")
        .script(List.of("echo build"))
        .build();

    List<Job> jobs = java.util.Arrays.asList(null, validJob);

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(jobs)
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(stage))
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = ""
        + "build:\n"
        + "    compile:\n"
        + "        image: alpine:latest\n"
        + "        script:\n"
        + "            - echo build\n"
        + "        failures: false\n";

    assertEquals(expected, actual);
  }

  @Test
  void formatPrintsFailuresWhenTrue() {
    Pipeline pipeline = Pipeline.builder().build();

    Job flaky = Job.builder()
        .name("flaky-check")
        .image("alpine:latest")
        .script(List.of("echo flaky"))
        .failures(true)
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("test")
        .jobs(List.of(flaky))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(stage))
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = ""
        + "test:\n"
        + "    flaky-check:\n"
        + "        image: alpine:latest\n"
        + "        script:\n"
        + "            - echo flaky\n"
        + "        failures: true\n";

    assertEquals(expected, actual);
  }

  @Test
  void formatPrintsFailuresWhenFalse() {
    Pipeline pipeline = Pipeline.builder().build();

    Job job = Job.builder()
        .name("compile")
        .image("alpine:latest")
        .script(List.of("echo build"))
        .failures(false)
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(List.of(job))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(stage))
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = ""
        + "build:\n"
        + "    compile:\n"
        + "        image: alpine:latest\n"
        + "        script:\n"
        + "            - echo build\n"
        + "        failures: false\n";

    assertEquals(expected, actual);
  }

  @Test
  void formatSkipsJobWithBlankName() {
    Pipeline pipeline = Pipeline.builder().build();

    Job invalidJob = Job.builder()
        .name(" ")
        .image("alpine:latest")
        .script(List.of("echo skip"))
        .build();

    Job validJob = Job.builder()
        .name("compile")
        .image("alpine:latest")
        .script(List.of("echo build"))
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(List.of(invalidJob, validJob))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(List.of(stage))
        .build();

    DryrunFormatter formatter = new DefaultDryrunFormatter();
    String actual = formatter.format(pipeline, plan);

    String expected = ""
        + "build:\n"
        + "    compile:\n"
        + "        image: alpine:latest\n"
        + "        script:\n"
        + "            - echo build\n"
        + "        failures: false\n";

    assertEquals(expected, actual);
  }
}
