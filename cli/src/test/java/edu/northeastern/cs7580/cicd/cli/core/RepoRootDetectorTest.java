package edu.northeastern.cs7580.cicd.cli.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoRootDetectorTest {
  @Test
  void isRepoRootReturnsFalseWhenPathIsNull() {
    RepoRootDetector detector = new RepoRootDetector();

    boolean result = detector.isRepoRoot(null);

    assertFalse(result);
  }

  @Test
  void isRepoRootReturnsFalseWhenGitDirectoryIsMissing(@TempDir Path tempDir) {
    RepoRootDetector detector = new RepoRootDetector();

    boolean result = detector.isRepoRoot(tempDir);

    assertFalse(result);
  }

  @Test
  void isRepoRootReturnsTrueWhenGitDirectoryExists(@TempDir Path tempDir) throws Exception {
    RepoRootDetector detector = new RepoRootDetector();

    Files.createDirectories(tempDir.resolve(".git"));

    boolean result = detector.isRepoRoot(tempDir);

    assertTrue(result);
  }
}