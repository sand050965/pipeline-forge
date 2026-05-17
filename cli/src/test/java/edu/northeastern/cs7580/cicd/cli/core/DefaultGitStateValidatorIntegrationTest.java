package edu.northeastern.cs7580.cicd.cli.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultGitStateValidatorIntegrationTest {

  @TempDir
  private Path tempDir;

  @Test
  public void validate_succeedsWithRepoRootDetectedFromNestedDirectory() throws Exception {
    try (Git git = initRepo(tempDir)) {
      commitFile(git, tempDir.resolve("a.txt"), "hello");

      Path nested = Files.createDirectories(tempDir.resolve("nested").resolve("deep"));
      Path detectedRoot = new RepoRootDetector().findRepoRoot(nested);
      assertNotNull(detectedRoot);

      String branch = git.getRepository().getBranch();
      DefaultGitStateValidator validator = new DefaultGitStateValidator();

      assertDoesNotThrow(() -> validator.validate(detectedRoot, branch, "latest"));
    }
  }

  /**
   * Initializes a new Git repository in the given directory.
   *
   * @param dir repository directory
   * @return initialized {@link Git} handle
   * @throws GitAPIException if initialization fails
   */
  private static Git initRepo(Path dir) throws GitAPIException {
    return Git.init().setDirectory(dir.toFile()).call();
  }

  /**
   * Writes a file and creates a commit containing it.
   *
   * @param git git handle
   * @param file file path
   * @param content file contents
   * @return created commit id
   * @throws IOException if file IO fails
   * @throws GitAPIException if git operations fail
   */
  private static ObjectId commitFile(Git git, Path file, String content)
      throws IOException, GitAPIException {
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));

    File workTreeFile = Objects.requireNonNull(
        git.getRepository().getWorkTree(), "workTree");
    Path workTree = workTreeFile.toPath();
    String repoRelativePath = workTree.relativize(file).toString();

    git.add().addFilepattern(repoRelativePath).call();
    return git.commit().setMessage("test commit").call().getId();
  }
}
