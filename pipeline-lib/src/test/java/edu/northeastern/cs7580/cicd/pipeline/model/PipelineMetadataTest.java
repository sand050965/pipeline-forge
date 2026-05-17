package edu.northeastern.cs7580.cicd.pipeline.model;


import static org.assertj.core.api.Assertions.assertThat;

import edu.northeastern.cs7580.cicd.pipelinelib.model.PipelineMetadata;
import org.junit.jupiter.api.Test;

class PipelineMetadataTest {

  @Test
  void shouldBuildMetadataWithAllFields() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .description("This is a test pipeline")
        .build();

    assertThat(metadata.getName()).isEqualTo("test-pipeline");
    assertThat(metadata.getDescription()).isEqualTo("This is a test pipeline");
  }

  @Test
  void shouldBuildMetadataWithOnlyName() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .build();

    assertThat(metadata.getName()).isEqualTo("test-pipeline");
    assertThat(metadata.getDescription()).isNull();
  }

  @Test
  void shouldBuildMetadataWithEmptyDescription() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .description("")
        .build();

    assertThat(metadata.getName()).isEqualTo("test-pipeline");
    assertThat(metadata.getDescription()).isEmpty();
  }

  @Test
  void shouldSupportSetters() {
    PipelineMetadata metadata = PipelineMetadata.builder().build();

    metadata.setName("updated-pipeline");
    metadata.setDescription("Updated description");

    assertThat(metadata.getName()).isEqualTo("updated-pipeline");
    assertThat(metadata.getDescription()).isEqualTo("Updated description");
  }

  @Test
  void shouldSupportEqualsAndHashCode() {
    PipelineMetadata metadata1 = PipelineMetadata.builder()
        .name("pipeline1")
        .description("Description 1")
        .build();

    PipelineMetadata metadata2 = PipelineMetadata.builder()
        .name("pipeline1")
        .description("Description 1")
        .build();

    PipelineMetadata metadata3 = PipelineMetadata.builder()
        .name("pipeline2")
        .description("Description 2")
        .build();

    assertThat(metadata1).isEqualTo(metadata2);
    assertThat(metadata1).isNotEqualTo(metadata3);
    assertThat(metadata1.hashCode()).isEqualTo(metadata2.hashCode());
  }

  @Test
  void shouldHaveProperToString() {
    PipelineMetadata metadata = PipelineMetadata.builder()
        .name("test-pipeline")
        .description("Test description")
        .build();

    String toString = metadata.toString();

    assertThat(toString).contains("test-pipeline");
    assertThat(toString).contains("Test description");
  }
}
