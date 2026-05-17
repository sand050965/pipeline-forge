package edu.northeastern.cs7580.cicd.apigateway.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class PipelineExecutionRequestTest {

  private Validator validator;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    objectMapper = new ObjectMapper();
  }

  @Test
  void validRequest_PassesValidation() {
    PipelineExecutionRequest request = createValidRequest();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertTrue(violations.isEmpty());
  }

  @Test
  void missingPipelineName_FailsValidation() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    ConstraintViolation<PipelineExecutionRequest> violation = violations.iterator().next();
    assertEquals("pipelineName", violation.getPropertyPath().toString());
    assertEquals("Pipeline name is required", violation.getMessage());
  }

  @Test
  void blankPipelineName_FailsValidation() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("   ")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertTrue(violations.stream()
        .anyMatch(v -> v.getPropertyPath().toString().equals("pipelineName")));
  }

  @Test
  void missingPipelineFilePath_FailsValidation() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("default")
        .gitMetadata(createValidGitMetadata())
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    ConstraintViolation<PipelineExecutionRequest> violation = violations.iterator().next();
    assertEquals("pipelineFilePath", violation.getPropertyPath().toString());
    assertEquals("Pipeline file path is required", violation.getMessage());
  }

  @Test
  void blankPipelineFilePath_FailsValidation() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("default")
        .pipelineFilePath("   ")
        .gitMetadata(createValidGitMetadata())
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertTrue(violations.stream()
        .anyMatch(v -> v.getPropertyPath().toString().equals("pipelineFilePath")));
  }

  @Test
  void nullGitMetadata_FailsValidation() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("default")
        .pipelineFilePath(".pipelines/default.yaml")
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    ConstraintViolation<PipelineExecutionRequest> violation = violations.iterator().next();
    assertEquals("gitMetadata", violation.getPropertyPath().toString());
    assertEquals("Git metadata is required", violation.getMessage());
  }

  @Test
  void invalidNestedGitMetadata_FailsValidation() {
    GitMetadata invalidMetadata = GitMetadata.builder()
        .branch("main")
        .commitHash("abc123")
        .build();

    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("default")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(invalidMetadata)
        .build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(1, violations.size());
    assertTrue(violations.stream()
        .anyMatch(v -> v.getPropertyPath().toString().equals("gitMetadata.repositoryUrl")));
  }

  @Test
  void allFieldsMissing_FailsWithMultipleViolations() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder().build();

    Set<ConstraintViolation<PipelineExecutionRequest>> violations = validator.validate(request);

    assertEquals(3, violations.size());
  }

  @Test
  void builder_CreatesValidObject() {
    PipelineExecutionRequest request = PipelineExecutionRequest.builder()
        .pipelineName("build-pipeline")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();

    assertEquals("build-pipeline", request.getPipelineName());
    assertEquals(".pipelines/default.yaml", request.getPipelineFilePath());
    assertNotNull(request.getGitMetadata());
    assertEquals("git@github.com:user/repo.git", request.getGitMetadata().getRepositoryUrl());
  }

  @Test
  void serialization_ProducesCorrectJson() throws Exception {
    PipelineExecutionRequest request = createValidRequest();

    String json = objectMapper.writeValueAsString(request);

    assertTrue(json.contains("\"pipelineName\":\"default\""));
    assertTrue(json.contains("\"pipelineFilePath\":"));
    assertTrue(json.contains("\"gitMetadata\":{"));
    assertTrue(json.contains("\"repositoryUrl\":\"git@github.com:user/repo.git\""));
  }

  @Test
  void deserialization_CreatesValidObject() throws Exception {
    String json = """
        {
          "pipelineName": "test-pipeline",
          "pipelineFilePath": ".pipelines/test-pipeline.yaml",
          "gitMetadata": {
            "repositoryUrl": "git@github.com:user/repo.git",
            "branch": "main",
            "commitHash": "abc123"
          }
        }
        """;

    PipelineExecutionRequest request = objectMapper.readValue(json, PipelineExecutionRequest.class);

    assertEquals("test-pipeline", request.getPipelineName());
    assertEquals(".pipelines/test-pipeline.yaml", request.getPipelineFilePath());
    assertNotNull(request.getGitMetadata());
    assertEquals("git@github.com:user/repo.git", request.getGitMetadata().getRepositoryUrl());
    assertEquals("main", request.getGitMetadata().getBranch());
    assertEquals("abc123", request.getGitMetadata().getCommitHash());
  }

  @Test
  void equals_SameValues_ReturnsTrue() {
    PipelineExecutionRequest request1 = createValidRequest();
    PipelineExecutionRequest request2 = createValidRequest();

    assertEquals(request1, request2);
    assertEquals(request1.hashCode(), request2.hashCode());
  }

  @Test
  void equals_DifferentPipelineName_ReturnsFalse() {
    PipelineExecutionRequest request1 = createValidRequest();
    PipelineExecutionRequest request2 = PipelineExecutionRequest.builder()
        .pipelineName("different")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();

    assertNotEquals(request1, request2);
  }

  @Test
  void toString_ContainsAllFields() {
    PipelineExecutionRequest request = createValidRequest();

    String toString = request.toString();

    assertTrue(toString.contains("pipelineName=default"));
    assertTrue(toString.contains("pipelineFilePath="));
    assertTrue(toString.contains("gitMetadata="));
  }

  private PipelineExecutionRequest createValidRequest() {
    return PipelineExecutionRequest.builder()
        .pipelineName("default")
        .pipelineFilePath(".pipelines/default.yaml")
        .gitMetadata(createValidGitMetadata())
        .build();
  }

  private GitMetadata createValidGitMetadata() {
    return GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .branch("main")
        .commitHash("abc123def456")
        .build();
  }
}