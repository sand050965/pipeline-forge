package edu.northeastern.cs7580.cicd.pipeline.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.PipelineMetadata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PipelineTest {

  @Test
  void shouldBuildPipelineWithAllFields() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .description("Test description")
        .build();

    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    Map<String, Job> jobs = Map.of("job1", job1);
    List<String> stages = List.of("build", "test", "deploy");

    Pipeline pipeline = Pipeline.builder()
        .pipeline(metadata)
        .stages(stages)
        .jobs(jobs)
        .build();

    assertThat(pipeline.getPipeline()).isEqualTo(metadata);
    assertThat(pipeline.getStages()).containsExactly("build", "test", "deploy");
    assertThat(pipeline.getJobs()).hasSize(1);
    assertThat(pipeline.getJobs().get("job1")).isEqualTo(job1);
  }

  @Test
  void shouldReturnDefaultStagesWhenStagesIsNull() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .build();

    Pipeline pipeline = Pipeline.builder()
        .pipeline(metadata)
        .stages(null)
        .jobs(Map.of())
        .build();

    List<String> stages = pipeline.getStagesOrDefault();

    assertThat(stages).containsExactly("build", "test", "docs");
  }

  @Test
  void shouldReturnDefaultStagesWhenStagesIsEmpty() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .build();

    Pipeline pipeline = Pipeline.builder()
        .pipeline(metadata)
        .stages(List.of())
        .jobs(Map.of())
        .build();

    List<String> stages = pipeline.getStagesOrDefault();

    assertThat(stages).containsExactly("build", "test", "docs");
  }

  @Test
  void shouldReturnCustomStagesWhenProvided() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .build();

    List<String> customStages = List.of("lint", "compile", "test", "deploy");

    Pipeline pipeline = Pipeline.builder()
        .pipeline(metadata)
        .stages(customStages)
        .jobs(Map.of())
        .build();

    List<String> stages = pipeline.getStagesOrDefault();

    assertThat(stages).containsExactly("lint", "compile", "test", "deploy");
  }

  @Test
  void shouldHandleMultipleJobs() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .build();

    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo job1")
        .build();

    Job job2 = Job.builder()
        .name("job2")
        .stage("test")
        .image("alpine:latest")
        .script("echo job2")
        .build();

    Job job3 = Job.builder()
        .name("job3")
        .stage("test")
        .image("alpine:latest")
        .script("echo job3")
        .needs(List.of("job2"))
        .build();

    Map<String, Job> jobs = Map.of(
        "job1", job1,
        "job2", job2,
        "job3", job3
    );

    Pipeline pipeline = Pipeline.builder()
        .pipeline(metadata)
        .stages(List.of("build", "test"))
        .jobs(jobs)
        .build();

    assertThat(pipeline.getJobs()).hasSize(3);
    assertThat(pipeline.getJobs().get("job1")).isEqualTo(job1);
    assertThat(pipeline.getJobs().get("job2")).isEqualTo(job2);
    assertThat(pipeline.getJobs().get("job3")).isEqualTo(job3);
  }

  @Test
  void shouldSupportSetters() {
    Pipeline pipeline = Pipeline.builder().build();

    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("new-pipeline")
        .build();

    Job job = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    pipeline.setPipeline(metadata);
    pipeline.setStages(List.of("build", "test"));
    pipeline.setJobs(Map.of("job1", job));

    assertThat(pipeline.getPipeline()).isEqualTo(metadata);
    assertThat(pipeline.getStages()).containsExactly("build", "test");
    assertThat(pipeline.getJobs()).hasSize(1);
  }

  @Test
  void shouldSupportEqualsAndHashCode() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .build();

    Job job = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    Pipeline pipeline1 = Pipeline.builder()
        .pipeline(metadata)
        .stages(List.of("build", "test"))
        .jobs(Map.of("job1", job))
        .build();

    Pipeline pipeline2 = Pipeline.builder()
        .pipeline(metadata)
        .stages(List.of("build", "test"))
        .jobs(Map.of("job1", job))
        .build();

    assertThat(pipeline1).isEqualTo(pipeline2);
    assertThat(pipeline1.hashCode()).isEqualTo(pipeline2.hashCode());
  }

  @Test
  void shouldHandleEmptyJobs() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .build();

    Pipeline pipeline = Pipeline.builder()
        .pipeline(metadata)
        .stages(List.of("build"))
        .jobs(Map.of())
        .build();

    assertThat(pipeline.getJobs()).isEmpty();
  }

  @Test
  void shouldAllowMutableJobsMap() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .build();

    Map<String, Job> jobs = new HashMap<>();

    Pipeline pipeline = Pipeline.builder()
        .pipeline(metadata)
        .stages(List.of("build"))
        .jobs(jobs)
        .build();

    Job newJob = Job.builder()
        .name("new-job")
        .stage("build")
        .image("alpine:latest")
        .script("echo new")
        .build();

    jobs.put("new-job", newJob);

    assertThat(pipeline.getJobs()).hasSize(1);
    assertThat(pipeline.getJobs().get("new-job")).isEqualTo(newJob);
  }

  @Test
  void testStagesListIsUnmodifiable() {
    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(List.of("build", "test"))
        .jobs(Map.of())
        .build();

    List<String> stages = pipeline.getStages();

    assertThatThrownBy(() -> stages.add("deploy"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testJobsMapIsUnmodifiable() {
    Job job = Job.builder().name("job1").stage("build").image("img").script("script").build();

    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(List.of("build"))
        .jobs(Map.of("job1", job))
        .build();

    Map<String, Job> jobs = pipeline.getJobs();

    assertThatThrownBy(() -> jobs.put("job2", job))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testSetStagesWithNull() {
    Pipeline pipeline = Pipeline.builder().build();

    pipeline.setStages(null);

    assertThat(pipeline.getStages()).isNull();
  }

  @Test
  void testSetStagesCreatesDefensiveCopy() {
    Pipeline pipeline = Pipeline.builder().build();

    List<String> originalStages = new ArrayList<>(Arrays.asList("build", "test"));
    pipeline.setStages(originalStages);

    // Modify original list
    originalStages.add("deploy");

    // Pipeline's internal list should not be affected
    assertThat(pipeline.getStages()).hasSize(2);
    assertThat(pipeline.getStages()).containsExactly("build", "test");
  }

  @Test
  void testSetJobsWithNull() {
    Pipeline pipeline = Pipeline.builder().build();

    pipeline.setJobs(null);

    assertThat(pipeline.getJobs()).isNull();
  }

  @Test
  void testSetJobsCreatesDefensiveCopy() {
    Pipeline pipeline = Pipeline.builder().build();

    Job job1 = Job.builder().name("job1").stage("build").image("img").script("script").build();
    Map<String, Job> originalJobs = new HashMap<>();
    originalJobs.put("job1", job1);

    pipeline.setJobs(originalJobs);

    // Modify original map
    Job job2 = Job.builder().name("job2").stage("test").image("img").script("script").build();
    originalJobs.put("job2", job2);

    // Pipeline's internal map should not be affected
    assertThat(pipeline.getJobs()).hasSize(1);
    assertThat(pipeline.getJobs()).containsOnlyKeys("job1");
  }

  @Test
  void testGetStagesReturnsNullWhenNotSet() {
    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(null)
        .jobs(Map.of())
        .build();

    assertThat(pipeline.getStages()).isNull();
  }

  @Test
  void testGetJobsReturnsNullWhenNotSet() {
    Pipeline pipeline = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test").build())
        .stages(List.of("build"))
        .jobs(null)
        .build();

    assertThat(pipeline.getJobs()).isNull();
  }

  @Test
  void testPipelineWithMinimalFields() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("minimal")
        .build();

    Pipeline pipeline = Pipeline.builder()
        .pipeline(metadata)
        .build();

    assertThat(pipeline.getPipeline()).isEqualTo(metadata);
    assertThat(pipeline.getStages()).isNull();
    assertThat(pipeline.getJobs()).isNull();
  }

  @Test
  void testDifferentPipelinesAreNotEqual() {
    Pipeline pipeline1 = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test1").build())
        .stages(List.of("build"))
        .jobs(Map.of())
        .build();

    Pipeline pipeline2 = Pipeline.builder()
        .pipeline(PipelineMetadata.builder().name("test2").build())
        .stages(List.of("build"))
        .jobs(Map.of())
        .build();

    assertThat(pipeline1).isNotEqualTo(pipeline2);
  }
}
