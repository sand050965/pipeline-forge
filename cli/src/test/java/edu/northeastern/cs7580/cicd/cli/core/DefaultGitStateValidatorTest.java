package edu.northeastern.cs7580.cicd.cli.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultGitStateValidatorTest {

  @TempDir
  private Path tempDir;

  @Test
  public void validate_allowsMatchingBranchAndLatestCommit() throws Exception {
    try (Git git = initRepo(tempDir)) {
      commitFile(git, tempDir.resolve("a.txt"), "hello");
      String branch = currentBranchShortName(git);
      Path repoRoot = git.getRepository().getWorkTree().toPath();

      DefaultGitStateValidator validator = new DefaultGitStateValidator();
      assertDoesNotThrow(() -> validator.validate(repoRoot, branch, "latest"));
    }
  }

  @Test
  public void validate_throwsOnBranchMismatch() throws Exception {
    try (Git git = initRepo(tempDir)) {
      commitFile(git, tempDir.resolve("a.txt"), "hello");
      String actualBranch = currentBranchShortName(git);
      String requestedBranch = actualBranch.equals("main") ? "feature" : "main";
      Path repoRoot = git.getRepository().getWorkTree().toPath();

      DefaultGitStateValidator validator = new DefaultGitStateValidator();
      GitStateException ex =
          assertThrows(GitStateException.class, () -> validator.validate(repoRoot,
              requestedBranch, "latest"));
      if (!ex.getMessage().contains("Branch mismatch")) {
        throw new AssertionError("Expected Branch mismatch message but got: "
            + ex.getMessage());
      }
    }
  }

  @Test
  public void validate_allowsMatchingCommitPrefix() throws Exception {
    try (Git git = initRepo(tempDir)) {
      ObjectId commitId = commitFile(git, tempDir.resolve("a.txt"), "hello");
      String branch = currentBranchShortName(git);
      String fullSha = commitId.name();
      String prefix = fullSha.substring(0, 8);
      Path repoRoot = git.getRepository().getWorkTree().toPath();

      DefaultGitStateValidator validator = new DefaultGitStateValidator();
      assertDoesNotThrow(() -> validator.validate(repoRoot, branch, prefix));
    }
  }

  @Test
  public void validate_throwsOnCommitMismatch() throws Exception {
    try (Git git = initRepo(tempDir)) {
      commitFile(git, tempDir.resolve("a.txt"), "hello");
      String branch = currentBranchShortName(git);
      Path repoRoot = git.getRepository().getWorkTree().toPath();

      DefaultGitStateValidator validator = new DefaultGitStateValidator();
      GitStateException ex =
          assertThrows(GitStateException.class, () -> validator.validate(repoRoot,
              branch, "deadbeef"));
      if (!ex.getMessage().contains("Commit mismatch")) {
        throw new AssertionError("Expected Commit mismatch message but got: "
            + ex.getMessage());
      }
    }
  }

  @Test
  public void validate_throwsOnDetachedHeadWhenBranchRequested() throws Exception {
    try (Git git = initRepo(tempDir)) {
      ObjectId commitId = commitFile(git, tempDir.resolve("a.txt"), "hello");
      git.checkout().setName(commitId.name()).call();
      Path repoRoot = git.getRepository().getWorkTree().toPath();

      DefaultGitStateValidator validator = new DefaultGitStateValidator();
      GitStateException ex =
          assertThrows(GitStateException.class, () -> validator.validate(repoRoot,
              "main", "latest"));
      if (!ex.getMessage().contains("Detached HEAD")) {
        throw new AssertionError("Expected Detached HEAD message but got: "
            + ex.getMessage());
      }
    }
  }

  @Test
  public void validate_throwsWhenRepositoryHasNoCommits() throws Exception {
    try (Git git = initRepo(tempDir)) {
      Path repoRoot = git.getRepository().getWorkTree().toPath();

      DefaultGitStateValidator validator = new DefaultGitStateValidator();
      GitStateException ex =
          assertThrows(GitStateException.class, () -> validator.validate(repoRoot,
              "main", "latest"));
      if (!ex.getMessage().toLowerCase().contains("no commits")) {
        throw new AssertionError("Expected no commits message but got: "
            + ex.getMessage());
      }
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

  /**
   * Resolves the current branch short name for a non-detached repository.
   *
   * @param git git handle
   * @return current branch short name
   * @throws IOException if repository cannot be read
   */
  private static String currentBranchShortName(Git git) throws IOException {
    String fullBranch = git.getRepository().getFullBranch();
    if (fullBranch == null || !fullBranch.startsWith(Constants.R_HEADS)) {
      throw new IllegalStateException("Repository is in detached HEAD state.");
    }
    return fullBranch.substring(Constants.R_HEADS.length());
  }

  /**
   * Computes path relative to the repository work tree for {@code git add}.
   *
   * @param git git handle
   * @param file absolute file path
   * @return repository-relative path string
   */
  private static String relativizeToWorkTree(Git git, Path file) {
    File workTree = git.getRepository().getWorkTree();
    Path workTreePath = workTree.toPath();
    return workTreePath.relativize(file).toString();
  }
}
