package edu.northeastern.cs7580.cicd.executionservice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class StageRunEntityTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Test
  void builder_allFields_populatedCorrectly() {
    StageRunEntity entity = StageRunEntity.builder()
        .id(1L)
        .pipelineRunId(10L)
        .stageName("build")
        .status(ExecutionStatus.RUNNING.name())
        .startTime(NOW)
        .endTime(NOW.plusMinutes(3))
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getPipelineRunId()).isEqualTo(10L);
    assertThat(entity.getStageName()).isEqualTo("build");
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.RUNNING.name());
    assertThat(entity.getStartTime()).isEqualTo(NOW);
    assertThat(entity.getEndTime()).isEqualTo(NOW.plusMinutes(3));
    assertThat(entity.getCreatedAt()).isEqualTo(NOW);
    assertThat(entity.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void builder_optionalFields_defaultToNull() {
    StageRunEntity entity = StageRunEntity.builder()
        .pipelineRunId(10L)
        .stageName("test")
        .status(ExecutionStatus.PENDING.name())
        .startTime(NOW)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();

    assertThat(entity.getId()).isNull();
    assertThat(entity.getEndTime()).isNull();
  }

  @Test
  void setters_updateFieldsCorrectly() {
    StageRunEntity entity = new StageRunEntity();

    entity.setId(2L);
    entity.setPipelineRunId(20L);
    entity.setStageName("deploy");
    entity.setStatus(ExecutionStatus.SUCCESS.name());
    entity.setStartTime(NOW);
    entity.setEndTime(NOW.plusSeconds(60));
    entity.setCreatedAt(NOW);
    entity.setUpdatedAt(NOW);

    assertThat(entity.getId()).isEqualTo(2L);
    assertThat(entity.getPipelineRunId()).isEqualTo(20L);
    assertThat(entity.getStageName()).isEqualTo("deploy");
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCESS.name());
    assertThat(entity.getEndTime()).isEqualTo(NOW.plusSeconds(60));
  }

  @Test
  void equals_sameFieldValues_areEqual() {
    StageRunEntity a = buildEntity(1L, "build", ExecutionStatus.PENDING);
    StageRunEntity b = buildEntity(1L, "build", ExecutionStatus.PENDING);

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
  }

  @Test
  void equals_differentId_areNotEqual() {
    StageRunEntity a = buildEntity(1L, "build", ExecutionStatus.PENDING);
    StageRunEntity b = buildEntity(2L, "build", ExecutionStatus.PENDING);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_differentStageName_areNotEqual() {
    StageRunEntity a = buildEntity(1L, "build", ExecutionStatus.PENDING);
    StageRunEntity b = buildEntity(1L, "deploy", ExecutionStatus.PENDING);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_differentStatus_areNotEqual() {
    StageRunEntity a = buildEntity(1L, "build", ExecutionStatus.PENDING);
    StageRunEntity b = buildEntity(1L, "build", ExecutionStatus.SUCCESS);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_null_notEqual() {
    assertThat(buildEntity(1L, "build", ExecutionStatus.PENDING)).isNotEqualTo(null);
  }

  @Test
  void status_allEnumValuesAreAssignable() {
    StageRunEntity entity = new StageRunEntity();
    for (ExecutionStatus s : ExecutionStatus.values()) {
      entity.setStatus(s.name());
      assertThat(entity.getStatus()).isEqualTo(s.name());
    }
  }

  @Test
  void status_canTransitionFromPendingToRunning() {
    StageRunEntity entity = buildEntity(1L, "build", ExecutionStatus.PENDING);
    entity.setStatus(ExecutionStatus.RUNNING.name());
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.RUNNING.name());
  }

  @Test
  void status_canTransitionFromRunningToSuccess() {
    StageRunEntity entity = buildEntity(1L, "build", ExecutionStatus.RUNNING);
    entity.setStatus(ExecutionStatus.SUCCESS.name());
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCESS.name());
  }

  @Test
  void status_canTransitionFromRunningToFailed() {
    StageRunEntity entity = buildEntity(1L, "build", ExecutionStatus.RUNNING);
    entity.setStatus(ExecutionStatus.FAILED.name());
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.FAILED.name());
  }

  @Test
  void endTime_nullByDefault_canBeSetAfterCompletion() {
    StageRunEntity entity = buildEntity(1L, "build", ExecutionStatus.RUNNING);
    assertThat(entity.getEndTime()).isNull();

    entity.setEndTime(NOW.plusMinutes(2));
    assertThat(entity.getEndTime()).isEqualTo(NOW.plusMinutes(2));
  }

  @Test
  void toString_doesNotThrow() {
    assertThat(buildEntity(1L, "build", ExecutionStatus.RUNNING).toString()).isNotBlank();
  }

  @Test
  void toString_emptyEntity_doesNotThrow() {
    assertThat(new StageRunEntity().toString()).isNotBlank();
  }

  @Test
  void noArgsConstructor_allFieldsNull() {
    StageRunEntity entity = new StageRunEntity();

    assertThat(entity.getId()).isNull();
    assertThat(entity.getPipelineRunId()).isNull();
    assertThat(entity.getStageName()).isNull();
    assertThat(entity.getStatus()).isNull();
    assertThat(entity.getStartTime()).isNull();
    assertThat(entity.getEndTime()).isNull();
    assertThat(entity.getCreatedAt()).isNull();
    assertThat(entity.getUpdatedAt()).isNull();
  }

  @Test
  void allArgsConstructor_populatesAllFields() {
    StageRunEntity entity = new StageRunEntity(
        1L, 10L, "build", ExecutionStatus.RUNNING.name(),
        NOW, NOW.plusMinutes(3), NOW, NOW);

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getPipelineRunId()).isEqualTo(10L);
    assertThat(entity.getStageName()).isEqualTo("build");
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.RUNNING.name());
    assertThat(entity.getEndTime()).isEqualTo(NOW.plusMinutes(3));
  }

  private StageRunEntity buildEntity(Long id, String stageName, ExecutionStatus status) {
    return StageRunEntity.builder()
        .id(id)
        .pipelineRunId(10L)
        .stageName(stageName)
        .status(status.name())
        .startTime(NOW)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}