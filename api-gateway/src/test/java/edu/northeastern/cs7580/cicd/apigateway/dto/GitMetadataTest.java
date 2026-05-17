package edu.northeastern.cs7580.cicd.apigateway.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitMetadataTest {

  private Validator validator;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    objectMapper = new ObjectMapper();
  }

  @Test
  void validGitMetadata_PassesValidation() {
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .branch("main")
        .commitHash("abc123def456")
        .build();

    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    assertTrue(violations.isEmpty());
  }

  @Test
  void missingRepository_FailsValidation() {
    GitMetadata metadata = GitMetadata.builder()
        .branch("main")
        .commitHash("abc123")
        .build();

    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    assertEquals(1, violations.size());
    ConstraintViolation<GitMetadata> violation = violations.iterator().next();
    assertEquals("repositoryUrl", violation.getPropertyPath().toString());
    assertEquals("Repository URL is required", violation.getMessage());
  }

  @Test
  void blankRepository_FailsValidation() {
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("   ")
        .branch("main")
        .commitHash("abc123")
        .build();

    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    assertEquals(1, violations.size());
    assertTrue(violations.stream()
        .anyMatch(v -> v.getPropertyPath().toString().equals("repositoryUrl")));
  }

  @Test
  void missingBranch_FailsValidation() {
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .commitHash("abc123")
        .build();

    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    assertEquals(1, violations.size());
    ConstraintViolation<GitMetadata> violation = violations.iterator().next();
    assertEquals("branch", violation.getPropertyPath().toString());
    assertEquals("Branch name is required", violation.getMessage());
  }

  @Test
  void missingCommitHash_FailsValidation() {
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .branch("main")
        .build();

    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    assertEquals(1, violations.size());
    ConstraintViolation<GitMetadata> violation = violations.iterator().next();
    assertEquals("commitHash", violation.getPropertyPath().toString());
    assertEquals("Commit hash is required", violation.getMessage());
  }

  @Test
  void allFieldsMissing_FailsWithMultipleViolations() {
    GitMetadata metadata = new GitMetadata();

    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    assertEquals(3, violations.size());
  }

  @Test
  void builder_CreatesValidObject() {
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("git@gitlab.com:org/project.git")
        .branch("develop")
        .commitHash("1234567890abcdef")
        .build();

    assertEquals("git@gitlab.com:org/project.git", metadata.getRepositoryUrl());
    assertEquals("develop", metadata.getBranch());
    assertEquals("1234567890abcdef", metadata.getCommitHash());
  }

  @Test
  void serialization_ProducesCorrectJson() throws Exception {
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .branch("main")
        .commitHash("abc123")
        .build();

    String json = objectMapper.writeValueAsString(metadata);

    assertTrue(json.contains("\"repositoryUrl\":\"git@github.com:user/repo.git\""));
    assertTrue(json.contains("\"branch\":\"main\""));
    assertTrue(json.contains("\"commitHash\":\"abc123\""));
  }

  @Test
  void deserialization_CreatesValidObject() throws Exception {
    String json = """
        {
          "repositoryUrl": "git@github.com:user/repo.git",
          "branch": "main",
          "commitHash": "abc123def456"
        }
        """;

    GitMetadata metadata = objectMapper.readValue(json, GitMetadata.class);

    assertEquals("git@github.com:user/repo.git", metadata.getRepositoryUrl());
    assertEquals("main", metadata.getBranch());
    assertEquals("abc123def456", metadata.getCommitHash());
  }

  @Test
  void equals_SameValues_ReturnsTrue() {
    GitMetadata metadata1 = GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .branch("main")
        .commitHash("abc123")
        .build();

    GitMetadata metadata2 = GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .branch("main")
        .commitHash("abc123")
        .build();

    assertEquals(metadata1, metadata2);
    assertEquals(metadata1.hashCode(), metadata2.hashCode());
  }

  @Test
  void equals_DifferentValues_ReturnsFalse() {
    GitMetadata metadata1 = GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .branch("main")
        .commitHash("abc123")
        .build();

    GitMetadata metadata2 = GitMetadata.builder()
        .repositoryUrl("git@github.com:user/repo.git")
        .branch("develop")
        .commitHash("abc123")
        .build();

    assertNotEquals(metadata1, metadata2);
  }

  @Test
  void noArgsConstructor_CreatesEmptyObject() {
    GitMetadata metadata = new GitMetadata();

    assertNull(metadata.getRepositoryUrl());
    assertNull(metadata.getBranch());
    assertNull(metadata.getCommitHash());
  }

  @Test
  void allArgsConstructor_SetsAllFields() {
    GitMetadata metadata = new GitMetadata(
        "git@github.com:user/repo.git",
        "feature-branch",
        "xyz789"
    );

    assertEquals("git@github.com:user/repo.git", metadata.getRepositoryUrl());
    assertEquals("feature-branch", metadata.getBranch());
    assertEquals("xyz789", metadata.getCommitHash());
  }
}
