package edu.northeastern.cs7580.cicd.executionservice.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PipelineExecutionRequestTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  void testBuilder_validRequest() {
    GitMetadata gitMetadata = createValidGitMetadata();
    String filePath = ".pipelines/default.yaml";

    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("default")
        .pipelineFilePath(filePath)
        .gitMetadata(gitMetadata)
        .build();

    assertEquals("default", request.getPipelineName());
    assertEquals(filePath, request.getPipelineFilePath());
    assertEquals(gitMetadata, request.getGitMetadata());
  }

  @Test
  void testValidation_allFieldsValid() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("test-pipeline")
        .pipelineFilePath(".pipelines/test-pipeline.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertTrue(violations.isEmpty());
  }

  @Test
  void testValidation_pipelineNameNull() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName(null)
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertEquals("Pipeline name is required", violations.iterator().next().getMessage());
  }

  @Test
  void testValidation_pipelineNameBlank() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("   ")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertEquals("Pipeline name is required", violations.iterator().next().getMessage());
  }

  @Test
  void testValidation_pipelineFilePathNull() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("test")
        .pipelineFilePath(null)
        .gitMetadata(createValidGitMetadata())
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertEquals("Pipeline file path is required", violations.iterator().next().getMessage());
  }

  @Test
  void testValidation_pipelineFilePathEmpty() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("test")
        .pipelineFilePath("")
        .gitMetadata(createValidGitMetadata())
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertEquals("Pipeline file path is required", violations.iterator().next().getMessage());
  }

  @Test
  void testValidation_gitMetadataNull() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("test")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(null)
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertEquals("Git metadata is required", violations.iterator().next().getMessage());
  }

  @Test
  void testValidation_nestedGitMetadataInvalid() {
    GitMetadata invalidGitMetadata = GitMetadata.builder()
        .repositoryUrl("")
        .branch("")
        .commitHash("")
        .build();

    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("test")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(invalidGitMetadata)
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(3, violations.size());
  }

  @Test
  void testValidation_multipleInvalidFields() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("")
        .pipelineFilePath("")
        .gitMetadata(null)
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(3, violations.size());
  }

  @Test
  void testNoArgsConstructor() {
    PipelineExecutionRequest request = new PipelineExecutionRequest();

    assertNull(request.getPipelineName());
    assertNull(request.getPipelineFilePath());
    assertNull(request.getGitMetadata());
  }

  @Test
  void testAllArgsConstructor() {
    GitMetadata gitMetadata = createValidGitMetadata();
    String filePath = ".pipelines/default.yaml";

    PipelineExecutionRequest request = new PipelineExecutionRequest(
        "test-pipeline",
        filePath,
        gitMetadata
    );

    assertEquals("test-pipeline", request.getPipelineName());
    assertEquals(filePath, request.getPipelineFilePath());
    assertEquals(gitMetadata, request.getGitMetadata());
  }

  @Test
  void testSetters() {
    PipelineExecutionRequest request = new PipelineExecutionRequest();
    GitMetadata gitMetadata = createValidGitMetadata();

    request.setPipelineName("new-pipeline");
    request.setPipelineFilePath(".pipelines/new-pipeline.yaml");
    request.setGitMetadata(gitMetadata);

    assertEquals("new-pipeline", request.getPipelineName());
    assertEquals(".pipelines/new-pipeline.yaml", request.getPipelineFilePath());
    assertEquals(gitMetadata, request.getGitMetadata());
  }

  @Test
  void testEquals_sameValues() {
    GitMetadata gitMetadata = createValidGitMetadata();

    PipelineExecutionRequest request1 = PipelineExecutionRequest.builder()
        .pipelineName("test")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(gitMetadata)
        .build();

    PipelineExecutionRequest request2 = PipelineExecutionRequest.builder()
        .pipelineName("test")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(gitMetadata)
        .build();

    assertEquals(request1, request2);
    assertEquals(request1.hashCode(), request2.hashCode());
  }

  @Test
  void testToString() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("test-pipeline")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();

    String toString = request.toString();

    assertTrue(toString.contains("test-pipeline"));
    assertTrue(toString.contains(".pipelines/default.yaml"));
  }

  private GitMetadata createValidGitMetadata() {
    return GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo.git")
        .branch("main")
        .commitHash("abc123")
        .build();
  }
}
