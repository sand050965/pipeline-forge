package edu.northeastern.cs7580.cicd.executionservice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class JobRunEntityTest {

  @Test
  void builder_shouldSetAllFields() {
    OffsetDateTime now = OffsetDateTime.now();

    JobRunEntity entity = JobRunEntity.builder()
        .id(1L)
        .stageRunId(10L)
        .jobName("build")
        .failures(true)
        .status(ExecutionStatus.RUNNING.name())
        .startTime(now)
        .endTime(null)
        .createdAt(now)
        .updatedAt(now)
        .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getStageRunId()).isEqualTo(10L);
    assertThat(entity.getJobName()).isEqualTo("build");
    assertThat(entity.isFailures()).isTrue();
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.RUNNING.name());
    assertThat(entity.getStartTime()).isEqualTo(now);
    assertThat(entity.getEndTime()).isNull();
    assertThat(entity.getCreatedAt()).isEqualTo(now);
    assertThat(entity.getUpdatedAt()).isEqualTo(now);
  }

  @Test
  void noArgsConstructor_shouldCreateEmptyEntity() {
    JobRunEntity entity = new JobRunEntity();

    assertThat(entity.getId()).isNull();
    assertThat(entity.getStageRunId()).isNull();
    assertThat(entity.getJobName()).isNull();
    assertThat(entity.isFailures()).isFalse();
    assertThat(entity.getStatus()).isNull();
    assertThat(entity.getStartTime()).isNull();
    assertThat(entity.getEndTime()).isNull();
  }

  @Test
  void setter_shouldUpdateStatusToSuccess() {
    JobRunEntity entity = JobRunEntity.builder()
        .stageRunId(1L)
        .jobName("test")
        .status(ExecutionStatus.RUNNING.name())
        .startTime(OffsetDateTime.now())
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();

    OffsetDateTime endTime = OffsetDateTime.now();
    entity.setStatus(ExecutionStatus.SUCCESS.name());
    entity.setEndTime(endTime);

    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCESS.name());
    assertThat(entity.getEndTime()).isEqualTo(endTime);
  }

  @Test
  void setter_shouldUpdateStatusToFailed() {
    JobRunEntity entity = JobRunEntity.builder()
        .stageRunId(1L)
        .jobName("test")
        .status(ExecutionStatus.RUNNING.name())
        .startTime(OffsetDateTime.now())
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();

    entity.setStatus(ExecutionStatus.FAILED.name());
    entity.setEndTime(OffsetDateTime.now());

    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.FAILED.name());
    assertThat(entity.getEndTime()).isNotNull();
  }

  @Test
  void equals_shouldBeEqualForSameFields() {
    OffsetDateTime now = OffsetDateTime.now();

    JobRunEntity entity1 = JobRunEntity.builder()
        .id(1L)
        .stageRunId(10L)
        .jobName("build")
        .failures(false)
        .status(ExecutionStatus.SUCCESS.name())
        .startTime(now)
        .createdAt(now)
        .updatedAt(now)
        .build();

    JobRunEntity entity2 = JobRunEntity.builder()
        .id(1L)
        .stageRunId(10L)
        .jobName("build")
        .failures(false)
        .status(ExecutionStatus.SUCCESS.name())
        .startTime(now)
        .createdAt(now)
        .updatedAt(now)
        .build();

    assertThat(entity1).isEqualTo(entity2);
    assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode());
  }

  @Test
  void equals_shouldNotBeEqualForDifferentJobName() {
    OffsetDateTime now = OffsetDateTime.now();

    JobRunEntity entity1 = JobRunEntity.builder()
        .id(1L).stageRunId(10L).jobName("build")
        .failures(false)
        .status(ExecutionStatus.SUCCESS.name()).startTime(now).createdAt(now).updatedAt(now)
        .build();

    JobRunEntity entity2 = JobRunEntity.builder()
        .id(1L).stageRunId(10L).jobName("test")
        .failures(false)
        .status(ExecutionStatus.SUCCESS.name()).startTime(now).createdAt(now).updatedAt(now)
        .build();

    assertThat(entity1).isNotEqualTo(entity2);
  }

  @Test
  void toString_shouldContainKeyFields() {
    JobRunEntity entity = JobRunEntity.builder()
        .id(1L)
        .stageRunId(10L)
        .jobName("deploy")
        .failures(false)
        .status(ExecutionStatus.FAILED.name())
        .startTime(OffsetDateTime.now())
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();

    String result = entity.toString();

    assertThat(result).contains("deploy");
    assertThat(result).contains("FAILED");
  }
}
