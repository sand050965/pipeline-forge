package edu.northeastern.cs7580.cicd.executionservice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PipelineEntityTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Test
  void builder_allFields_populatedCorrectly() {
    PipelineEntity entity = PipelineEntity.builder()
        .id(1L)
        .repoId("abc123")
        .pipelineName("deploy-prod")
        .gitRepo("https://github.com/org/repo.git")
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getRepoId()).isEqualTo("abc123");
    assertThat(entity.getPipelineName()).isEqualTo("deploy-prod");
    assertThat(entity.getGitRepo()).isEqualTo("https://github.com/org/repo.git");
    assertThat(entity.getCreatedAt()).isEqualTo(NOW);
    assertThat(entity.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void builder_optionalId_defaultsToNull() {
    PipelineEntity entity = PipelineEntity.builder()
        .repoId("abc123")
        .pipelineName("deploy-prod")
        .gitRepo("https://github.com/org/repo.git")
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();

    assertThat(entity.getId()).isNull();
  }

  @Test
  void setters_updateFieldsCorrectly() {
    PipelineEntity entity = new PipelineEntity();

    entity.setId(5L);
    entity.setRepoId("def456");
    entity.setPipelineName("run-tests");
    entity.setGitRepo("https://github.com/org/other.git");
    entity.setCreatedAt(NOW);
    entity.setUpdatedAt(NOW.plusMinutes(1));

    assertThat(entity.getId()).isEqualTo(5L);
    assertThat(entity.getRepoId()).isEqualTo("def456");
    assertThat(entity.getPipelineName()).isEqualTo("run-tests");
    assertThat(entity.getGitRepo()).isEqualTo("https://github.com/org/other.git");
    assertThat(entity.getUpdatedAt()).isEqualTo(NOW.plusMinutes(1));
  }

  @Test
  void equals_sameFieldValues_areEqual() {
    PipelineEntity a = buildEntity(1L, "abc123", "deploy-prod");
    PipelineEntity b = buildEntity(1L, "abc123", "deploy-prod");

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
  }

  @Test
  void equals_differentId_areNotEqual() {
    PipelineEntity a = buildEntity(1L, "abc123", "deploy-prod");
    PipelineEntity b = buildEntity(2L, "abc123", "deploy-prod");

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_differentRepoId_areNotEqual() {
    PipelineEntity a = buildEntity(1L, "abc123", "deploy-prod");
    PipelineEntity b = buildEntity(1L, "xyz789", "deploy-prod");

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_differentPipelineName_areNotEqual() {
    PipelineEntity a = buildEntity(1L, "abc123", "deploy-prod");
    PipelineEntity b = buildEntity(1L, "abc123", "run-tests");

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_nullOther_notEqual() {
    PipelineEntity a = buildEntity(1L, "abc123", "deploy-prod");

    assertThat(a).isNotEqualTo(null);
  }

  @Test
  void toString_doesNotThrow() {
    assertThat(buildEntity(1L, "abc123", "deploy-prod").toString()).isNotBlank();
  }

  @Test
  void toString_emptyEntity_doesNotThrow() {
    assertThat(new PipelineEntity().toString()).isNotBlank();
  }

  @Test
  void noArgsConstructor_allFieldsNull() {
    PipelineEntity entity = new PipelineEntity();

    assertThat(entity.getId()).isNull();
    assertThat(entity.getRepoId()).isNull();
    assertThat(entity.getPipelineName()).isNull();
    assertThat(entity.getGitRepo()).isNull();
    assertThat(entity.getCreatedAt()).isNull();
    assertThat(entity.getUpdatedAt()).isNull();
  }

  @Test
  void allArgsConstructor_populatesAllFields() {
    PipelineEntity entity = new PipelineEntity(1L, "abc123", "deploy-prod",
        "https://github.com/org/repo.git", NOW, NOW);

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getRepoId()).isEqualTo("abc123");
    assertThat(entity.getPipelineName()).isEqualTo("deploy-prod");
    assertThat(entity.getGitRepo()).isEqualTo("https://github.com/org/repo.git");
    assertThat(entity.getCreatedAt()).isEqualTo(NOW);
    assertThat(entity.getUpdatedAt()).isEqualTo(NOW);
  }

  private PipelineEntity buildEntity(Long id, String repoId, String pipelineName) {
    return PipelineEntity.builder()
        .id(id)
        .repoId(repoId)
        .pipelineName(pipelineName)
        .gitRepo("https://github.com/org/repo.git")
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}