package edu.northeastern.cs7580.cicd.pipeline.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageExecutionTest {

  @Test
  void testBuilderCreatesStageExecution() {
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

    assertNotNull(stage);
    assertEquals("build", stage.getStageName());
    assertEquals(1, stage.getJobs().size());
    assertEquals("compile", stage.getJobs().get(0).getName());
  }

  @Test
  void testGetStageNameReturnsCorrectName() {
    Job job = Job.builder().name("job1").stage("test").image("img").script("script").build();

    StageExecution stage = StageExecution.builder()
        .stageName("test")
        .jobs(Collections.singletonList(job))
        .build();

    String stageName = stage.getStageName();

    assertEquals("test", stageName);
  }

  @Test
  void testGetJobsReturnsCorrectList() {
    Job job1 = Job.builder().name("job1").stage("build").image("img").script("script").build();
    Job job2 = Job.builder().name("job2").stage("build").image("img").script("script").build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Arrays.asList(job1, job2))
        .build();

    List<Job> jobs = stage.getJobs();

    assertEquals(2, jobs.size());
    assertEquals("job1", jobs.get(0).getName());
    assertEquals("job2", jobs.get(1).getName());
  }

  @Test
  void testEmptyJobsList() {
    StageExecution stage = StageExecution.builder()
        .stageName("empty-stage")
        .jobs(Collections.emptyList())
        .build();

    assertNotNull(stage);
    assertEquals("empty-stage", stage.getStageName());
    assertTrue(stage.getJobs().isEmpty());
  }

  @Test
  void testMultipleJobsWithDifferentProperties() {
    Job job1 = Job.builder()
        .name("compile")
        .stage("build")
        .image("gradle:8.12-jdk21")
        .script(Arrays.asList("./gradlew clean", "./gradlew build"))
        .build();

    Job job2 = Job.builder()
        .name("lint")
        .stage("build")
        .image("node:18")
        .script("npm run lint")
        .needs(Collections.singletonList("compile"))
        .build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Arrays.asList(job1, job2))
        .build();

    assertEquals(2, stage.getJobs().size());
    assertEquals("compile", stage.getJobs().get(0).getName());
    assertEquals("lint", stage.getJobs().get(1).getName());
    assertNotNull(stage.getJobs().get(1).getNeeds());
    assertEquals(1, stage.getJobs().get(1).getNeeds().size());
  }

  @Test
  void testEqualsAndHashCode() {
    Job job = Job.builder().name("job1").stage("build").image("img").script("script").build();

    StageExecution stage1 = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    StageExecution stage2 = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    assertEquals(stage1, stage2);
    assertEquals(stage1.hashCode(), stage2.hashCode());
  }

  @Test
  void testToStringContainsStageName() {
    Job job = Job.builder().name("job1").stage("build").image("img").script("script").build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    String toString = stage.toString();

    assertTrue(toString.contains("build"));
    assertTrue(toString.contains("stageName"));
  }

  @Test
  void testJobsListIsUnmodifiable() {
    Job job = Job.builder().name("job1").stage("build").image("img").script("script").build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    List<Job> jobs = stage.getJobs();

    // Should throw UnsupportedOperationException
    assertThatThrownBy(() -> jobs.add(Job.builder().name("job2").build()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testJobsListCannotBeClearedExternally() {
    Job job = Job.builder().name("job1").stage("build").image("img").script("script").build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    List<Job> jobs = stage.getJobs();

    assertThatThrownBy(() -> jobs.clear())
        .isInstanceOf(UnsupportedOperationException.class);

    // Original should still have the job
    assertThat(stage.getJobs()).hasSize(1);
  }

  @Test
  void testStageExecutionWithNullStageName() {
    Job job = Job.builder().name("job1").stage("build").image("img").script("script").build();

    StageExecution stage = StageExecution.builder()
        .stageName(null)
        .jobs(Collections.singletonList(job))
        .build();

    assertThat(stage.getStageName()).isNull();
    assertThat(stage.getJobs()).hasSize(1);
  }

  @Test
  void testStageExecutionBuilderWithMethodChaining() {
    Job job1 = Job.builder().name("job1").stage("build").image("img").script("script").build();
    Job job2 = Job.builder().name("job2").stage("build").image("img").script("script").build();

    StageExecution stage = StageExecution.builder()
        .stageName("build")
        .jobs(Arrays.asList(job1, job2))
        .build();

    assertThat(stage.getStageName()).isEqualTo("build");
    assertThat(stage.getJobs()).hasSize(2);
  }

  @Test
  void testDifferentStagesAreNotEqual() {
    Job job = Job.builder().name("job1").stage("build").image("img").script("script").build();

    StageExecution stage1 = StageExecution.builder()
        .stageName("build")
        .jobs(Collections.singletonList(job))
        .build();

    StageExecution stage2 = StageExecution.builder()
        .stageName("test")
        .jobs(Collections.singletonList(job))
        .build();

    assertThat(stage1).isNotEqualTo(stage2);
    assertThat(stage1.hashCode()).isNotEqualTo(stage2.hashCode());
  }
}