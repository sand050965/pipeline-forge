package edu.northeastern.cs7580.cicd.pipeline.internal.builder;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.cs7580.cicd.pipelinelib.internal.builder.ExecutionPlanBuilder;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.PipelineMetadata;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionPlanBuilderTest {

  private ExecutionPlanBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new ExecutionPlanBuilder();
  }

  @Test
  void testBuildSimplePipelineWithOneStageOneJob() {
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

    ExecutionPlan plan = builder.build(pipeline);

    assertNotNull(plan);
    assertEquals(1, plan.getStages().size());

    StageExecution buildStage = plan.getStages().get(0);
    assertEquals("build", buildStage.getStageName());
    assertEquals(1, buildStage.getJobs().size());
    assertEquals("compile", buildStage.getJobs().get(0).getName());
  }

  @Test
  void testBuildPipelineWithMultipleStages() {
    Job compileJob = Job.builder()
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

    Job deployJob = Job.builder()
        .name("deploy")
        .stage("deploy")
        .image("kubectl")
        .script("kubectl apply")
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("compile", compileJob);
    jobs.put("test", testJob);
    jobs.put("deploy", deployJob);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Arrays.asList("build", "test", "deploy"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    assertEquals(3, plan.getStages().size());
    assertEquals("build", plan.getStages().get(0).getStageName());
    assertEquals("test", plan.getStages().get(1).getStageName());
    assertEquals("deploy", plan.getStages().get(2).getStageName());
  }

  @Test
  void testBuildPipelineWithMultipleJobsInSameStage() {
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
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("unit-tests", unitTest);
    jobs.put("integration-tests", integrationTest);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    assertEquals(1, plan.getStages().size());
    StageExecution testStage = plan.getStages().get(0);
    assertEquals("test", testStage.getStageName());
    assertEquals(2, testStage.getJobs().size());
  }

  @Test
  void testBuildPipelineWithSimpleDependencies() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    Job job2 = Job.builder()
        .name("job2")
        .stage("test")
        .image("img")
        .script("script")
        .needs(Collections.singletonList("job1"))
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("job1", job1);
    jobs.put("job2", job2);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    StageExecution testStage = plan.getStages().get(0);
    List<Job> orderedJobs = testStage.getJobs();

    assertEquals(2, orderedJobs.size());
    // job1 should come before job2
    assertEquals("job1", orderedJobs.get(0).getName());
    assertEquals("job2", orderedJobs.get(1).getName());
  }

  @Test
  void testBuildPipelineWithComplexDependencies() {
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

    Job coverageReport = Job.builder()
        .name("coverage-report")
        .stage("test")
        .image("gradle:8.12-jdk21")
        .script("./gradlew jacocoTestReport")
        .needs(Arrays.asList("unit-tests", "integration-tests"))
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("unit-tests", unitTest);
    jobs.put("integration-tests", integrationTest);
    jobs.put("coverage-report", coverageReport);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    StageExecution testStage = plan.getStages().get(0);
    List<Job> orderedJobs = testStage.getJobs();

    assertEquals(3, orderedJobs.size());

    assertEquals("unit-tests", orderedJobs.get(0).getName());

    assertEquals("integration-tests", orderedJobs.get(1).getName());

    assertEquals("coverage-report", orderedJobs.get(2).getName());
  }

  @Test
  void testBuildPipelineWithMultipleDependencyChains() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    Job job3 = Job.builder()
        .name("job3")
        .stage("test")
        .image("img")
        .script("script")
        .needs(Collections.singletonList("job1"))
        .build();

    Job job2 = Job.builder()
        .name("job2")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    Job job4 = Job.builder()
        .name("job4")
        .stage("test")
        .image("img")
        .script("script")
        .needs(Collections.singletonList("job2"))
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("job1", job1);
    jobs.put("job2", job2);
    jobs.put("job3", job3);
    jobs.put("job4", job4);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    StageExecution testStage = plan.getStages().get(0);
    List<Job> orderedJobs = testStage.getJobs();

    assertEquals(4, orderedJobs.size());

    int job1Index = findJobIndex(orderedJobs, "job1");
    int job2Index = findJobIndex(orderedJobs, "job2");
    int job3Index = findJobIndex(orderedJobs, "job3");
    int job4Index = findJobIndex(orderedJobs, "job4");

    assertTrue(job1Index < job3Index);
    assertTrue(job2Index < job4Index);
  }

  @Test
  void testBuildPipelineWithJobsWithoutDependencies() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    Job job2 = Job.builder()
        .name("job2")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    Job job3 = Job.builder()
        .name("job3")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("job1", job1);
    jobs.put("job2", job2);
    jobs.put("job3", job3);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    StageExecution testStage = plan.getStages().get(0);
    assertEquals(3, testStage.getJobs().size());

    assertTrue(testStage.getJobs().stream().anyMatch(j -> j.getName().equals("job1")));
    assertTrue(testStage.getJobs().stream().anyMatch(j -> j.getName().equals("job2")));
    assertTrue(testStage.getJobs().stream().anyMatch(j -> j.getName().equals("job3")));
  }

  @Test
  void testBuildPipelineWithEmptyNeedsList() {
    Job job = Job.builder()
        .name("job1")
        .stage("test")
        .image("img")
        .script("script")
        .needs(Collections.emptyList())
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("job1", job);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    StageExecution testStage = plan.getStages().get(0);
    assertEquals(1, testStage.getJobs().size());
    assertEquals("job1", testStage.getJobs().get(0).getName());
  }

  @Test
  void testBuildPipelineUsesDefaultStages() {
    Job buildJob = Job.builder()
        .name("build")
        .stage("build")
        .image("img")
        .script("script")
        .build();

    Job testJob = Job.builder()
        .name("test")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("build", buildJob);
    jobs.put("test", testJob);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(null)
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    assertNotNull(plan);
    assertTrue(plan.getStages().size() > 0);
  }

  @Test
  void testBuildPipelineSkipsEmptyStages() {
    Job buildJob = Job.builder()
        .name("build")
        .stage("build")
        .image("img")
        .script("script")
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("build", buildJob);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Arrays.asList("build", "test", "deploy"))  // test and deploy have no jobs
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    assertEquals(1, plan.getStages().size());  // Only build stage should be in plan
    assertEquals("build", plan.getStages().get(0).getStageName());
  }

  @Test
  void testBuildPipelineIncludesJobDependenciesMap() {
    Job job1 = Job.builder()
        .name("compile")
        .stage("build")
        .image("img")
        .script("script")
        .build();

    Job job2 = Job.builder()
        .name("test")
        .stage("build")
        .image("img")
        .script("script")
        .needs(Collections.singletonList("compile"))
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("compile", job1);
    jobs.put("test", job2);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("build"))  // Only one stage now
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    assertNotNull(plan.getJobDependencies());
    assertEquals(2, plan.getJobDependencies().size());

    assertTrue(plan.getJobDependencies().containsKey("compile"));
    assertTrue(plan.getJobDependencies().get("compile").isEmpty());

    assertTrue(plan.getJobDependencies().containsKey("test"));
    assertEquals(Collections.singletonList("compile"), plan.getJobDependencies().get("test"));
  }

  @Test
  void testBuildPipelineWithJobsWithoutDependenciesHasEmptyLists() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    Job job2 = Job.builder()
        .name("job2")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("job1", job1);
    jobs.put("job2", job2);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    assertEquals(2, plan.getJobDependencies().size());
    assertTrue(plan.getJobDependencies().get("job1").isEmpty());
    assertTrue(plan.getJobDependencies().get("job2").isEmpty());
  }

  @Test
  void testBuildPipelineWithNullNeedsHasEmptyDependencyList() {
    Job job = Job.builder()
        .name("job1")
        .stage("test")
        .image("img")
        .script("script")
        .needs(null)
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("job1", job);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    assertTrue(plan.getJobDependencies().containsKey("job1"));
    assertNotNull(plan.getJobDependencies().get("job1"));
    assertTrue(plan.getJobDependencies().get("job1").isEmpty());
  }

  @Test
  void testBuildPipelineWithMultipleDependencies() {
    Job job1 = Job.builder()
        .name("compile")
        .stage("build")
        .image("img")
        .script("script")
        .build();

    Job job2 = Job.builder()
        .name("lint")
        .stage("build")
        .image("img")
        .script("script")
        .build();

    Job job3 = Job.builder()
        .name("package")
        .stage("build")
        .image("img")
        .script("script")
        .needs(Arrays.asList("compile", "lint"))
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("compile", job1);
    jobs.put("lint", job2);
    jobs.put("package", job3);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("build"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    assertEquals(3, plan.getJobDependencies().size());
    assertTrue(plan.getJobDependencies().get("compile").isEmpty());
    assertTrue(plan.getJobDependencies().get("lint").isEmpty());
    assertEquals(2, plan.getJobDependencies().get("package").size());
    assertTrue(plan.getJobDependencies().get("package").contains("compile"));
    assertTrue(plan.getJobDependencies().get("package").contains("lint"));
  }

  @Test
  void testBuildPipelineWithComplexDependenciesIncludesAllInMap() {
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

    Job coverageReport = Job.builder()
        .name("coverage-report")
        .stage("test")
        .image("gradle:8.12-jdk21")
        .script("./gradlew jacocoTestReport")
        .needs(Arrays.asList("unit-tests", "integration-tests"))
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("unit-tests", unitTest);
    jobs.put("integration-tests", integrationTest);
    jobs.put("coverage-report", coverageReport);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    Map<String, List<String>> deps = plan.getJobDependencies();

    assertEquals(3, deps.size());
    assertTrue(deps.get("unit-tests").isEmpty());
    assertEquals(Collections.singletonList("unit-tests"), deps.get("integration-tests"));
    assertEquals(2, deps.get("coverage-report").size());
    assertTrue(deps.get("coverage-report").contains("unit-tests"));
    assertTrue(deps.get("coverage-report").contains("integration-tests"));
  }

  @Test
  void testBuildPipelineDependencyListsAreImmutableCopies() {
    List<String> needs = new ArrayList<>();
    needs.add("compile");

    Job compileJob = Job.builder()
        .name("compile")
        .stage("test")
        .image("img")
        .script("script")
        .build();

    Job testJob = Job.builder()
        .name("test")
        .stage("test")
        .image("img")
        .script("script")
        .needs(needs)
        .build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("compile", compileJob);
    jobs.put("test", testJob);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Collections.singletonList("test"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    needs.add("another-job");

    assertEquals(1, plan.getJobDependencies().get("test").size());
    assertEquals("compile", plan.getJobDependencies().get("test").get(0));
  }

  @Test
  void testBuildPipelineAllJobsIncludedInDependencyMap() {
    Job job1 = Job.builder().name("job1").stage("build").image("img").script("script").build();
    Job job2 = Job.builder().name("job2").stage("build").image("img").script("script").build();
    Job job3 = Job.builder().name("job3").stage("test").image("img").script("script").build();
    Job job4 = Job.builder().name("job4").stage("deploy").image("img").script("script").build();

    Map<String, Job> jobs = new HashMap<>();
    jobs.put("job1", job1);
    jobs.put("job2", job2);
    jobs.put("job3", job3);
    jobs.put("job4", job4);

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(Arrays.asList("build", "test", "deploy"))
        .jobs(jobs)
        .build();

    ExecutionPlan plan = builder.build(pipeline);

    // All jobs should be in the dependency map
    assertEquals(4, plan.getJobDependencies().size());
    assertTrue(plan.getJobDependencies().containsKey("job1"));
    assertTrue(plan.getJobDependencies().containsKey("job2"));
    assertTrue(plan.getJobDependencies().containsKey("job3"));
    assertTrue(plan.getJobDependencies().containsKey("job4"));
  }

  private int findJobIndex(List<Job> jobs, String jobName) {
    for (int i = 0; i < jobs.size(); i++) {
      if (jobs.get(i).getName().equals(jobName)) {
        return i;
      }
    }
    return -1;
  }
}