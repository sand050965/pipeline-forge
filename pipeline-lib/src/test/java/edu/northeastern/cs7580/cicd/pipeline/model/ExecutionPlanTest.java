package edu.northeastern.cs7580.cicd.pipeline.model;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ExecutionPlanTest {

  @Test
  void testBuilderCreatesExecutionPlan() {
    Job job = Job.builder()
        .name("compile")
        .stage("build")
        .image("gradle:8.12-jdk21")
        .script("./gradlew build")
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(Collections.singletonList(stage))
        .build();

    assertNotNull(plan);
    assertEquals(1, plan.getStages().size());
    assertEquals("build", plan.getStages().get(0).getStageName());
  }

  @Test
  void testGetStagesReturnsCorrectList() {
    Job job1 = Job.builder().name("job1").stage("build").image("img").script("script").build();
    Job job2 = Job.builder().name("job2").stage("test").image("img").script("script").build();

    StageExecution buildStage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job1))
        .build();

    StageExecution testStage = StageExecution.builder()
        .stageName("test")
        .jobs(Collections.singletonList(job2))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(Arrays.asList(buildStage, testStage))
        .build();

    List<StageExecution> stages = plan.getStages();

    assertEquals(2, stages.size());
    assertEquals("build", stages.get(0).getStageName());
    assertEquals("test", stages.get(1).getStageName());
  }

  @Test
  void testEmptyStagesList() {
    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(Collections.emptyList())
        .build();

    assertNotNull(plan);
    assertTrue(plan.getStages().isEmpty());
  }

  @Test
  void testMultipleStagesWithMultipleJobs() {
    Job job1 = Job.builder().name("compile").stage("build").image("img").script("script").build();
    Job job2 = Job.builder().name("lint").stage("build").image("img").script("script").build();
    Job job3 = Job.builder().name("unit").stage("test").image("img").script("script").build();
    Job job4 = Job.builder()
        .name("integration")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    StageExecution buildStage = StageExecution.builder()
        .stageName("build")
        .jobs(Arrays.asList(job1, job2))
        .build();

    StageExecution testStage = StageExecution.builder()
        .stageName("test")
        .jobs(Arrays.asList(job3, job4))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(Arrays.asList(buildStage, testStage))
        .build();

    assertEquals(2, plan.getStages().size());
    assertEquals(2, plan.getStages().get(0).getJobs().size());
    assertEquals(2, plan.getStages().get(1).getJobs().size());
  }

  @Test
  void testEqualsAndHashCode() {
    Job job = Job.builder().name("job1").stage("build").image("img").script("script").build();
    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    ExecutionPlan plan1 = ExecutionPlan.builder()
        .stages(Collections.singletonList(stage))
        .build();

    ExecutionPlan plan2 = ExecutionPlan.builder()
        .stages(Collections.singletonList(stage))
        .build();

    assertEquals(plan1, plan2);
    assertEquals(plan1.hashCode(), plan2.hashCode());
  }

  @Test
  void testBuilderCreatesExecutionPlanWithDependencies() {
    Job job = Job.builder()
        .name("compile")
        .stage("build")
        .image("gradle:8.12-jdk21")
        .script("./gradlew build")
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    Map<String, List<String>> dependencies = new HashMap<>();
    dependencies.put("compile", new ArrayList<>());

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(Collections.singletonList(stage))
        .jobDependencies(dependencies)
        .build();

    assertNotNull(plan);
    assertNotNull(plan.getJobDependencies());
    assertEquals(1, plan.getJobDependencies().size());
    assertTrue(plan.getJobDependencies().get("compile").isEmpty());
  }

  @Test
  void testGetJobDependenciesReturnsCorrectMap() {
    Job job1 = Job.builder().name("compile").stage("build").image("img").script("script").build();
    Job job2 = Job.builder()
        .name("test")
        .stage("test")
        .image("img")
        .script("script")
        .needs(Collections.singletonList("compile"))
        .build();

    StageExecution buildStage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job1))
        .build();

    StageExecution testStage = StageExecution.builder()
        .stageName("test")
        .jobs(Collections.singletonList(job2))
        .build();

    Map<String, List<String>> dependencies = new HashMap<>();
    dependencies.put("compile", new ArrayList<>());
    dependencies.put("test", Collections.singletonList("compile"));

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(Arrays.asList(buildStage, testStage))
        .jobDependencies(dependencies)
        .build();

    Map<String, List<String>> deps = plan.getJobDependencies();

    assertNotNull(deps);
    assertEquals(2, deps.size());
    assertTrue(deps.get("compile").isEmpty());
    assertEquals(Collections.singletonList("compile"), deps.get("test"));
  }

  @Test
  void testEmptyJobDependenciesMap() {
    Job job = Job.builder().name("job1").stage("build").image("img").script("script").build();
    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(Collections.singletonList(stage))
        .jobDependencies(new HashMap<>())
        .build();

    assertNotNull(plan);
    assertNotNull(plan.getJobDependencies());
    assertTrue(plan.getJobDependencies().isEmpty());
  }

  @Test
  void testJobDependenciesWithMultipleDependencies() {
    Job job1 = Job.builder().name("compile").stage("build").image("img").script("script").build();
    Job job2 = Job.builder().name("lint").stage("build").image("img").script("script").build();
    Job job3 = Job.builder()
        .name("deploy")
        .stage("deploy")
        .image("img")
        .script("script")
        .needs(Arrays.asList("compile", "lint"))
        .build();

    StageExecution buildStage = StageExecution.builder()
        .stageName("build")
        .jobs(Arrays.asList(job1, job2))
        .build();

    StageExecution deployStage = StageExecution.builder()
        .stageName("deploy")
        .jobs(Collections.singletonList(job3))
        .build();

    Map<String, List<String>> dependencies = new HashMap<>();
    dependencies.put("compile", new ArrayList<>());
    dependencies.put("lint", new ArrayList<>());
    dependencies.put("deploy", Arrays.asList("compile", "lint"));

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(Arrays.asList(buildStage, deployStage))
        .jobDependencies(dependencies)
        .build();

    assertEquals(3, plan.getJobDependencies().size());
    assertEquals(2, plan.getJobDependencies().get("deploy").size());
    assertTrue(plan.getJobDependencies().get("deploy").contains("compile"));
    assertTrue(plan.getJobDependencies().get("deploy").contains("lint"));
  }

  @Test
  void testFlattenStagesIntoTopologicalOrder() {
    Job job1 = Job.builder().name("compile").stage("build").image("img").script("script").build();
    Job job2 = Job.builder().name("test").stage("test").image("img").script("script").build();
    Job job3 = Job.builder().name("deploy").stage("deploy").image("img").script("script").build();

    StageExecution buildStage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job1))
        .build();

    StageExecution testStage = StageExecution.builder()
        .stageName("test")
        .jobs(Collections.singletonList(job2))
        .build();

    StageExecution deployStage = StageExecution.builder()
        .stageName("deploy")
        .jobs(Collections.singletonList(job3))
        .build();

    ExecutionPlan plan = ExecutionPlan.builder()
        .stages(Arrays.asList(buildStage, testStage, deployStage))
        .jobDependencies(new HashMap<>())
        .build();

    // Flatten stages to get topological order
    List<Job> allJobs = plan.getStages().stream()
        .flatMap(stage -> stage.getJobs().stream())
        .collect(Collectors.toList());

    assertEquals(3, allJobs.size());
    assertEquals("compile", allJobs.get(0).getName());
    assertEquals("test", allJobs.get(1).getName());
    assertEquals("deploy", allJobs.get(2).getName());
  }

  @Test
  void testEqualsAndHashCodeWithDependencies() {
    Job job = Job.builder().name("job1").stage("build").image("img").script("script").build();
    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    Map<String, List<String>> deps = new HashMap<>();
    deps.put("job1", new ArrayList<>());

    ExecutionPlan plan1 = ExecutionPlan.builder()
        .stages(Collections.singletonList(stage))
        .jobDependencies(deps)
        .build();

    ExecutionPlan plan2 = ExecutionPlan.builder()
        .stages(Collections.singletonList(stage))
        .jobDependencies(deps)
        .build();

    assertEquals(plan1, plan2);
    assertEquals(plan1.hashCode(), plan2.hashCode());
  }
}