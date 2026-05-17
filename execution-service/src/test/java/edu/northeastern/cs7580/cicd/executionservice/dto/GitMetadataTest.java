package edu.northeastern.cs7580.cicd.executionservice.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GitMetadataTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  void testBuilder_validMetadata() {
    // Arrange & Act
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo.git")
        .branch("main")
        .commitHash("abc123def456")
        .build();

    // Assert
    assertEquals("https://github.com/user/repo.git", metadata.getRepositoryUrl());
    assertEquals("main", metadata.getBranch());
    assertEquals("abc123def456", metadata.getCommitHash());
  }

  @Test
  void testValidation_allFieldsValid() {
    // Arrange
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo.git")
        .branch("feature-branch")
        .commitHash("1234567890abcdef")
        .build();

    // Act
    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    // Assert
    assertTrue(violations.isEmpty());
  }

  @Test
  void testValidation_repositoryUrlNull() {
    // Arrange
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl(null)
        .branch("main")
        .commitHash("abc123")
        .build();

    // Act
    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<GitMetadata> violation = violations.iterator().next();
    assertEquals("Repository URL is required", violation.getMessage());
    assertEquals("repositoryUrl", violation.getPropertyPath().toString());
  }

  @Test
  void testValidation_repositoryUrlBlank() {
    // Arrange
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("   ")
        .branch("main")
        .commitHash("abc123")
        .build();

    // Act
    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<GitMetadata> violation = violations.iterator().next();
    assertEquals("Repository URL is required", violation.getMessage());
  }

  @Test
  void testValidation_branchNull() {
    // Arrange
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo.git")
        .branch(null)
        .commitHash("abc123")
        .build();

    // Act
    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<GitMetadata> violation = violations.iterator().next();
    assertEquals("Branch name is required", violation.getMessage());
    assertEquals("branch", violation.getPropertyPath().toString());
  }

  @Test
  void testValidation_branchEmpty() {
    // Arrange
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo.git")
        .branch("")
        .commitHash("abc123")
        .build();

    // Act
    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    // Assert
    assertEquals(1, violations.size());
    assertEquals("Branch name is required", violations.iterator().next().getMessage());
  }

  @Test
  void testValidation_commitHashNull() {
    // Arrange
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo.git")
        .branch("main")
        .commitHash(null)
        .build();

    // Act
    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    // Assert
    assertEquals(1, violations.size());
    ConstraintViolation<GitMetadata> violation = violations.iterator().next();
    assertEquals("Commit hash is required", violation.getMessage());
    assertEquals("commitHash", violation.getPropertyPath().toString());
  }

  @Test
  void testValidation_allFieldsInvalid() {
    // Arrange
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("")
        .branch("")
        .commitHash("")
        .build();

    // Act
    Set<ConstraintViolation<GitMetadata>> violations = validator.validate(metadata);

    // Assert
    assertEquals(3, violations.size());
  }

  @Test
  void testNoArgsConstructor() {
    // Act
    GitMetadata metadata = new GitMetadata();

    // Assert
    assertNull(metadata.getRepositoryUrl());
    assertNull(metadata.getBranch());
    assertNull(metadata.getCommitHash());
  }

  @Test
  void testAllArgsConstructor() {
    // Act
    GitMetadata metadata = new GitMetadata(
        "https://github.com/user/repo.git",
        "develop",
        "xyz789"
    );

    // Assert
    assertEquals("https://github.com/user/repo.git", metadata.getRepositoryUrl());
    assertEquals("develop", metadata.getBranch());
    assertEquals("xyz789", metadata.getCommitHash());
  }

  @Test
  void testSetters() {
    // Arrange
    GitMetadata metadata = new GitMetadata();

    // Act
    metadata.setRepositoryUrl("https://gitlab.com/user/repo.git");
    metadata.setBranch("feature-x");
    metadata.setCommitHash("def456");

    // Assert
    assertEquals("https://gitlab.com/user/repo.git", metadata.getRepositoryUrl());
    assertEquals("feature-x", metadata.getBranch());
    assertEquals("def456", metadata.getCommitHash());
  }

  @Test
  void testEquals_sameValues() {
    // Arrange
    GitMetadata metadata1 = GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo.git")
        .branch("main")
        .commitHash("abc123")
        .build();

    GitMetadata metadata2 = GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo.git")
        .branch("main")
        .commitHash("abc123")
        .build();

    // Assert
    assertEquals(metadata1, metadata2);
    assertEquals(metadata1.hashCode(), metadata2.hashCode());
  }

  @Test
  void testEquals_differentValues() {
    // Arrange
    GitMetadata metadata1 = GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo1.git")
        .branch("main")
        .commitHash("abc123")
        .build();

    GitMetadata metadata2 = GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo2.git")
        .branch("develop")
        .commitHash("xyz789")
        .build();

    // Assert
    assertNotEquals(metadata1, metadata2);
  }

  @Test
  void testToString() {
    // Arrange
    GitMetadata metadata = GitMetadata.builder()
        .repositoryUrl("https://github.com/user/repo.git")
        .branch("main")
        .commitHash("abc123")
        .build();

    // Act
    String toString = metadata.toString();

    // Assert
    assertTrue(toString.contains("https://github.com/user/repo.git"));
    assertTrue(toString.contains("main"));
    assertTrue(toString.contains("abc123"));
  }
}
