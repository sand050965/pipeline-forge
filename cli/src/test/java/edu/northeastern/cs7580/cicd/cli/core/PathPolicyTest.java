package edu.northeastern.cs7580.cicd.cli.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathPolicyTest {
  @Test
  void parseAndRejectAbsoluteAcceptsRelativePath() {
    PathPolicy policy = new PathPolicy();

    assertEquals(Path.of(".pipelines/release.yaml"), policy
        .parseAndRejectAbsolute(".pipelines/release.yaml"));
  }

  @Test
  void parseAndRejectAbsoluteRejectsAbsolutePath() {
    PathPolicy policy = new PathPolicy();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class,
            () -> policy.parseAndRejectAbsolute("/tmp/x.yaml"));
    assertEquals("absolute paths are not allowed: /tmp/x.yaml", ex.getMessage());
  }

  @Test
  void resolveUnderRepoRootResolvesAndNormalizesWithinRepo(@TempDir Path tempDir) {
    PathPolicy policy = new PathPolicy();

    Path resolved = policy.resolveUnderRepoRoot(tempDir,
        Path.of(".pipelines/../.pipelines/pipeline.yaml"));

    assertEquals(tempDir.resolve(".pipelines/pipeline.yaml").toAbsolutePath().normalize(),
        resolved);
  }

  @Test
  void resolveUnderRepoRootRejectsPathTraversalOutsideRepo(@TempDir Path tempDir) {
    PathPolicy policy = new PathPolicy();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class,
            () -> policy.resolveUnderRepoRoot(tempDir, Path.of("../x.yaml")));
    assertEquals("path traversal is not allowed: ../x.yaml", ex.getMessage());
  }

  @Test
  void enforceUnderPipelinesAllowsPathsUnderPipelines(@TempDir Path tempDir) {
    PathPolicy policy = new PathPolicy();

    Path pipelinesRoot = tempDir.resolve(".pipelines").toAbsolutePath().normalize();
    Path resolved = pipelinesRoot.resolve("release.yaml").normalize();

    policy.enforceUnderPipelines(tempDir, resolved);
  }

  @Test
  void enforceUnderPipelinesRejectsPathsOutsidePipelines(@TempDir Path tempDir) {
    PathPolicy policy = new PathPolicy();

    Path resolved = tempDir.resolve("release.yaml").toAbsolutePath().normalize();

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class,
            () -> policy.enforceUnderPipelines(tempDir, resolved));
    assertEquals("configuration files must be located under .pipelines/: "
        + resolved, ex.getMessage());
  }

  @Test
  void enforceExistingRegularFileRejectsMissingPath(@TempDir Path tempDir) {
    PathPolicy policy = new PathPolicy();

    Path missing = tempDir.resolve(".pipelines/missing.yaml");

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class,
            () -> policy.enforceExistingRegularFile(missing));
    assertEquals("configuration file not found: " + missing, ex.getMessage());
  }

  @Test
  void enforceExistingRegularFileRejectsDirectory(@TempDir Path tempDir) throws Exception {
    PathPolicy policy = new PathPolicy();

    Path dir = tempDir.resolve(".pipelines");
    Files.createDirectories(dir);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> policy.enforceExistingRegularFile(dir));
    assertEquals("configuration path is not a file: " + dir, ex.getMessage());
  }

  @Test
  void enforceExistingRegularFileAcceptsRegularFile(@TempDir Path tempDir) throws Exception {
    final PathPolicy policy = new PathPolicy();

    Path file = tempDir.resolve(".pipelines/release.yaml");
    Path parent = file.getParent();
    if (parent == null) {
      throw new IllegalStateException("Test setup error: file has no parent: " + file);
    }

    Files.createDirectories(parent);
    Files.writeString(file, "pipeline:\n  name: ok\n", StandardCharsets.UTF_8);

    policy.enforceExistingRegularFile(file);
  }
}