package edu.northeastern.cs7580.cicd.executionservice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PipelineRunEntityTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Test
  void builder_allFields_populatedCorrectly() {
    PipelineRunEntity entity = PipelineRunEntity.builder()
        .id(1L)
        .pipelineId(10L)
        .runNo(3)
        .status(ExecutionStatus.RUNNING.name())
        .gitBranch("main")
        .gitHash("abc123")
        .startTime(NOW)
        .endTime(NOW.plusMinutes(5))
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getPipelineId()).isEqualTo(10L);
    assertThat(entity.getRunNo()).isEqualTo(3);
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.RUNNING.name());
    assertThat(entity.getGitBranch()).isEqualTo("main");
    assertThat(entity.getGitHash()).isEqualTo("abc123");
    assertThat(entity.getStartTime()).isEqualTo(NOW);
    assertThat(entity.getEndTime()).isEqualTo(NOW.plusMinutes(5));
    assertThat(entity.getCreatedAt()).isEqualTo(NOW);
    assertThat(entity.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void builder_optionalFields_defaultToNull() {
    PipelineRunEntity entity = PipelineRunEntity.builder()
        .pipelineId(10L)
        .status(ExecutionStatus.RUNNING.name())
        .gitBranch("main")
        .gitHash("abc123")
        .startTime(NOW)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();

    assertThat(entity.getId()).isNull();
    assertThat(entity.getRunNo()).isNull();
    assertThat(entity.getEndTime()).isNull();
  }

  @Test
  void setters_updateFieldsCorrectly() {
    PipelineRunEntity entity = new PipelineRunEntity();

    entity.setId(2L);
    entity.setPipelineId(20L);
    entity.setRunNo(5);
    entity.setStatus(ExecutionStatus.SUCCESS.name());
    entity.setGitBranch("feature/xyz");
    entity.setGitHash("def456");
    entity.setStartTime(NOW);
    entity.setEndTime(NOW.plusSeconds(90));
    entity.setCreatedAt(NOW);
    entity.setUpdatedAt(NOW);

    assertThat(entity.getId()).isEqualTo(2L);
    assertThat(entity.getPipelineId()).isEqualTo(20L);
    assertThat(entity.getRunNo()).isEqualTo(5);
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCESS.name());
    assertThat(entity.getGitBranch()).isEqualTo("feature/xyz");
    assertThat(entity.getGitHash()).isEqualTo("def456");
    assertThat(entity.getEndTime()).isEqualTo(NOW.plusSeconds(90));
  }

  @Test
  void equals_sameFieldValues_areEqual() {
    PipelineRunEntity a = buildEntity(1L, ExecutionStatus.RUNNING);
    PipelineRunEntity b = buildEntity(1L, ExecutionStatus.RUNNING);

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
  }

  @Test
  void equals_differentId_areNotEqual() {
    PipelineRunEntity a = buildEntity(1L, ExecutionStatus.RUNNING);
    PipelineRunEntity b = buildEntity(2L, ExecutionStatus.RUNNING);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_differentStatus_areNotEqual() {
    PipelineRunEntity a = buildEntity(1L, ExecutionStatus.RUNNING);
    PipelineRunEntity b = buildEntity(1L, ExecutionStatus.SUCCESS);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_differentRunNo_areNotEqual() {
    PipelineRunEntity a = buildEntity(1L, ExecutionStatus.RUNNING);
    PipelineRunEntity b = buildEntity(1L, ExecutionStatus.RUNNING);
    b.setRunNo(99);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_null_notEqual() {
    assertThat(buildEntity(1L, ExecutionStatus.RUNNING)).isNotEqualTo(null);
  }

  @Test
  void status_allEnumValuesAreAssignable() {
    PipelineRunEntity entity = new PipelineRunEntity();
    for (ExecutionStatus s : ExecutionStatus.values()) {
      entity.setStatus(s.name());
      assertThat(entity.getStatus()).isEqualTo(s.name());
    }
  }

  @Test
  void status_canTransitionFromRunningToSuccess() {
    PipelineRunEntity entity = buildEntity(1L, ExecutionStatus.RUNNING);
    entity.setStatus(ExecutionStatus.SUCCESS.name());
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.SUCCESS.name());
  }

  @Test
  void status_canTransitionFromRunningToFailed() {
    PipelineRunEntity entity = buildEntity(1L, ExecutionStatus.RUNNING);
    entity.setStatus(ExecutionStatus.FAILED.name());
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.FAILED.name());
  }

  @Test
  void endTime_nullByDefault_canBeSetAfterCompletion() {
    PipelineRunEntity entity = buildEntity(1L, ExecutionStatus.RUNNING);
    assertThat(entity.getEndTime()).isNull();

    entity.setEndTime(NOW.plusMinutes(3));
    assertThat(entity.getEndTime()).isEqualTo(NOW.plusMinutes(3));
  }

  @Test
  void toString_doesNotThrow() {
    assertThat(buildEntity(1L, ExecutionStatus.RUNNING).toString()).isNotBlank();
  }

  @Test
  void toString_emptyEntity_doesNotThrow() {
    assertThat(new PipelineRunEntity().toString()).isNotBlank();
  }

  @Test
  void noArgsConstructor_allFieldsNull() {
    PipelineRunEntity entity = new PipelineRunEntity();

    assertThat(entity.getId()).isNull();
    assertThat(entity.getPipelineId()).isNull();
    assertThat(entity.getRunNo()).isNull();
    assertThat(entity.getStatus()).isNull();
    assertThat(entity.getGitBranch()).isNull();
    assertThat(entity.getGitHash()).isNull();
    assertThat(entity.getStartTime()).isNull();
    assertThat(entity.getEndTime()).isNull();
    assertThat(entity.getCreatedAt()).isNull();
    assertThat(entity.getUpdatedAt()).isNull();
    assertThat(entity.getTraceId()).isNull();
  }

  @Test
  void allArgsConstructor_populatesAllFields() {
    PipelineRunEntity entity = new PipelineRunEntity(
        1L, 10L, 3, ExecutionStatus.RUNNING.name(),
        "main", "abc123",
        NOW, NOW.plusMinutes(5), NOW, NOW, null, "4bf92f3577b34da6a3ce929d0e0e4736");

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getPipelineId()).isEqualTo(10L);
    assertThat(entity.getRunNo()).isEqualTo(3);
    assertThat(entity.getStatus()).isEqualTo(ExecutionStatus.RUNNING.name());
    assertThat(entity.getGitBranch()).isEqualTo("main");
    assertThat(entity.getGitHash()).isEqualTo("abc123");
    assertThat(entity.getEndTime()).isEqualTo(NOW.plusMinutes(5));
    assertThat(entity.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
  }

  private PipelineRunEntity buildEntity(Long id, ExecutionStatus status) {
    return PipelineRunEntity.builder()
        .id(id)
        .pipelineId(10L)
        .runNo(1)
        .status(status.name())
        .gitBranch("main")
        .gitHash("abc123")
        .startTime(NOW)
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}