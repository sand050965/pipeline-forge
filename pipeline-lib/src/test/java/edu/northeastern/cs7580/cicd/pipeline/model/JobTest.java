package edu.northeastern.cs7580.cicd.pipeline.model;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobTest {

  @Test
  void shouldBuildJobWithAllFields() {
    Job job = Job.builder()
        .name("test-job")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .needs(List.of("job1", "job2"))
        .build();

    assertThat(job.getName()).isEqualTo("test-job");
    assertThat(job.getStage()).isEqualTo("build");
    assertThat(job.getImage()).isEqualTo("alpine:latest");
    assertThat(job.getScript()).isEqualTo("echo test");
    assertThat(job.getNeeds()).containsExactly("job1", "job2");
  }

  @Test
  void shouldBuildJobWithoutNeeds() {
    Job job = Job.builder()
        .name("test-job")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    assertThat(job.getNeeds()).isNull();
  }

  @Test
  void shouldConvertStringScriptToList() {
    Job job = Job.builder()
        .name("test-job")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    List<String> commands = job.getScriptCommands();

    assertThat(commands).hasSize(1);
    assertThat(commands).containsExactly("echo test");
  }

  @Test
  void shouldReturnListScriptAsIs() {
    List<String> scriptList = List.of("echo test1", "echo test2", "echo test3");

    Job job = Job.builder()
        .name("test-job")
        .stage("build")
        .image("alpine:latest")
        .script(scriptList)
        .build();

    List<String> commands = job.getScriptCommands();

    assertThat(commands).hasSize(3);
    assertThat(commands).containsExactly("echo test1", "echo test2", "echo test3");
  }

  @Test
  void shouldReturnEmptyListWhenScriptIsNull() {
    Job job = Job.builder()
        .name("test-job")
        .stage("build")
        .image("alpine:latest")
        .script(null)
        .build();

    List<String> commands = job.getScriptCommands();

    assertThat(commands).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenScriptIsInvalidType() {
    Job job = Job.builder()
        .name("test-job")
        .stage("build")
        .image("alpine:latest")
        .script(123)
        .build();

    List<String> commands = job.getScriptCommands();

    assertThat(commands).isEmpty();
  }

  @Test
  void shouldSupportSetters() {
    Job job = Job.builder().build();

    job.setName("updated-job");
    job.setStage("test");
    job.setImage("ubuntu:latest");
    job.setScript("echo updated");
    job.setNeeds(List.of("dep1"));

    assertThat(job.getName()).isEqualTo("updated-job");
    assertThat(job.getStage()).isEqualTo("test");
    assertThat(job.getImage()).isEqualTo("ubuntu:latest");
    assertThat(job.getScript()).isEqualTo("echo updated");
    assertThat(job.getNeeds()).containsExactly("dep1");
  }

  @Test
  void shouldSupportEqualsAndHashCode() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    Job job3 = Job.builder()
        .name("job2")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    assertThat(job1).isEqualTo(job2);
    assertThat(job1).isNotEqualTo(job3);
    assertThat(job1.hashCode()).isEqualTo(job2.hashCode());
  }

  @Test
  void shouldNotBeEqualWhenNamesDiffer() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    Job job2 = Job.builder()
        .name("job2")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    assertThat(job1).isNotEqualTo(job2);
  }

  @Test
  void shouldNotBeEqualWhenStagesDiffer() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .stage("test")
        .image("alpine:latest")
        .script("echo test")
        .build();

    assertThat(job1).isNotEqualTo(job2);
  }

  @Test
  void shouldNotBeEqualWhenImagesDiffer() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .stage("build")
        .image("ubuntu:latest")
        .script("echo test")
        .build();

    assertThat(job1).isNotEqualTo(job2);
  }

  @Test
  void shouldNotBeEqualWhenScriptsDiffer() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test1")
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test2")
        .build();

    assertThat(job1).isNotEqualTo(job2);
  }

  @Test
  void shouldNotBeEqualWhenNeedsDiffer() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .needs(List.of("dep1"))
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .needs(List.of("dep2"))
        .build();

    assertThat(job1).isNotEqualTo(job2);
  }

  @Test
  void shouldBeEqualWhenBothNeedsAreNull() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .needs(null)
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .needs(null)
        .build();

    assertThat(job1).isEqualTo(job2);
    assertThat(job1.hashCode()).isEqualTo(job2.hashCode());
  }

  @Test
  void shouldNotBeEqualWhenOneNeedsIsNull() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .needs(null)
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .needs(List.of("dep1"))
        .build();

    assertThat(job1).isNotEqualTo(job2);
  }

  @Test
  void shouldNotBeEqualToNull() {
    Job job = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    assertThat(job).isNotEqualTo(null);
  }

  @Test
  void shouldNotBeEqualToDifferentType() {
    Job job = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    assertThat(job).isNotEqualTo("not a job");
  }

  @Test
  void shouldBeEqualToItself() {
    Job job = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    assertThat(job).isEqualTo(job);
    assertThat(job.hashCode()).isEqualTo(job.hashCode());
  }

  @Test
  void shouldHandleScriptAsListInEquals() {
    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script(List.of("cmd1", "cmd2"))
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine:latest")
        .script(List.of("cmd1", "cmd2"))
        .build();

    assertThat(job1).isEqualTo(job2);
    assertThat(job1.hashCode()).isEqualTo(job2.hashCode());
  }

  @Test
  void testSetNeedsWithEmptyList() {
    Job job = Job.builder().build();

    job.setNeeds(Collections.emptyList());

    assertThat(job.getNeeds()).isNotNull();
    assertThat(job.getNeeds()).isEmpty();
  }

  @Test
  void testSetNeedsWithMultipleItems() {
    Job job = Job.builder().build();

    job.setNeeds(Arrays.asList("dep1", "dep2", "dep3"));

    assertThat(job.getNeeds()).hasSize(3);
    assertThat(job.getNeeds()).containsExactly("dep1", "dep2", "dep3");
  }

  @Test
  void testSetNeedsReplacesExistingNeeds() {
    Job job = Job.builder()
        .needs(Arrays.asList("old1", "old2"))
        .build();

    job.setNeeds(Arrays.asList("new1", "new2", "new3"));

    assertThat(job.getNeeds()).hasSize(3);
    assertThat(job.getNeeds()).containsExactly("new1", "new2", "new3");
    assertThat(job.getNeeds()).doesNotContain("old1", "old2");
  }

  @Test
  void testSetNeedsToNullClearsNeeds() {
    Job job = Job.builder()
        .needs(Arrays.asList("dep1", "dep2"))
        .build();

    job.setNeeds(null);

    assertThat(job.getNeeds()).isNull();
  }

  @Test
  void testSetNeedsCreatesDefensiveCopyNotReference() {
    Job job = Job.builder().build();

    List<String> originalList = new ArrayList<>(Arrays.asList("dep1", "dep2"));
    job.setNeeds(originalList);

    // Modify the original list
    originalList.add("dep3");
    originalList.remove("dep1");

    // Job's needs should not be affected
    assertThat(job.getNeeds()).hasSize(2);
    assertThat(job.getNeeds()).containsExactly("dep1", "dep2");
  }

  @Test
  void testGetNeedsReturnsUnmodifiableView() {
    Job job = Job.builder()
        .needs(Arrays.asList("dep1", "dep2"))
        .build();

    List<String> needs = job.getNeeds();

    // Attempting to modify should throw exception
    assertThatThrownBy(() -> needs.add("dep3"))
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> needs.remove(0))
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> needs.clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testMultipleSetNeedsCallsWork() {
    Job job = Job.builder().build();

    job.setNeeds(Arrays.asList("dep1"));
    assertThat(job.getNeeds()).hasSize(1);

    job.setNeeds(Arrays.asList("dep1", "dep2"));
    assertThat(job.getNeeds()).hasSize(2);

    job.setNeeds(null);
    assertThat(job.getNeeds()).isNull();

    job.setNeeds(Arrays.asList("dep3", "dep4", "dep5"));
    assertThat(job.getNeeds()).hasSize(3);
  }

  @Test
  void testSetNeedsWithSingletonList() {
    Job job = Job.builder().build();

    job.setNeeds(Collections.singletonList("single-dep"));

    assertThat(job.getNeeds()).hasSize(1);
    assertThat(job.getNeeds()).containsExactly("single-dep");
  }

  @Test
  void testBuilderAndSetterProduceSameResult() {
    List<String> needs = Arrays.asList("dep1", "dep2");

    Job job1 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine")
        .script("echo test")
        .needs(needs)
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .stage("build")
        .image("alpine")
        .script("echo test")
        .build();
    job2.setNeeds(needs);

    assertThat(job1.getNeeds()).isEqualTo(job2.getNeeds());
  }

  @Test
  void testSetNeedsDoesNotAffectOtherFields() {
    Job job = Job.builder()
        .name("test-job")
        .stage("build")
        .image("alpine")
        .script("echo test")
        .build();

    job.setNeeds(Arrays.asList("dep1", "dep2"));

    assertThat(job.getName()).isEqualTo("test-job");
    assertThat(job.getStage()).isEqualTo("build");
    assertThat(job.getImage()).isEqualTo("alpine");
    assertThat(job.getScript()).isEqualTo("echo test");
  }

  @Test
  void testJobWithNeedsEqualsJobWithSameNeeds() {
    Job job1 = Job.builder()
        .name("job1")
        .needs(Arrays.asList("dep1", "dep2"))
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .needs(Arrays.asList("dep1", "dep2"))
        .build();

    assertThat(job1).isEqualTo(job2);
  }

  @Test
  void testJobWithDifferentNeedsAreNotEqual() {
    Job job1 = Job.builder()
        .name("job1")
        .needs(Arrays.asList("dep1"))
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .needs(Arrays.asList("dep2"))
        .build();

    assertThat(job1).isNotEqualTo(job2);
  }

  @Test
  void testJobWithNullNeedsEqualsJobWithNullNeeds() {
    Job job1 = Job.builder()
        .name("job1")
        .needs(null)
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .needs(null)
        .build();

    assertThat(job1).isEqualTo(job2);
  }

  @Test
  void testJobWithNullNeedsNotEqualsJobWithEmptyNeeds() {
    Job job1 = Job.builder()
        .name("job1")
        .needs(null)
        .build();

    Job job2 = Job.builder()
        .name("job1")
        .needs(Collections.emptyList())
        .build();

    assertThat(job1).isNotEqualTo(job2);
  }

  @Test
  void shouldDefaultFailuresToFalseWhenKeyAbsent() {
    Job job = Job.builder()
        .name("test-job")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .build();

    assertThat(job.isFailures()).isFalse();
  }

  @Test
  void shouldSetFailuresToTrueWhenExplicitlySet() {
    Job job = Job.builder()
        .name("test-job")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .failures(true)
        .build();

    assertThat(job.isFailures()).isTrue();
  }

  @Test
  void shouldSetFailuresToFalseWhenExplicitlySetFalse() {
    Job job = Job.builder()
        .name("test-job")
        .stage("build")
        .image("alpine:latest")
        .script("echo test")
        .failures(false)
        .build();

    assertThat(job.isFailures()).isFalse();
  }
}
