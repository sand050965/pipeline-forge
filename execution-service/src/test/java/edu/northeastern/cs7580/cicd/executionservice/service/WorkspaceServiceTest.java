package edu.northeastern.cs7580.cicd.executionservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.cs7580.cicd.executionservice.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.executionservice.exception.WorkspaceException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void prepareWorkspace_clonesAndChecksOutCommit() throws Exception {
    Path repoDir = tempDir.resolve("repo");
    Files.createDirectories(repoDir);

    String commitHash;
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      Path file = repoDir.resolve("hello.txt");
      Files.writeString(file, "v1", StandardCharsets.UTF_8);

      git.add().addFilepattern("hello.txt").call();
      RevCommit commit = git.commit().setMessage("commit v1").call();
      commitHash = commit.getName();
    }

    GitMetadata meta = new GitMetadata();
    meta.setRepositoryUrl(repoDir.toAbsolutePath().toString());
    meta.setBranch("master");
    meta.setCommitHash(commitHash);

    WorkspaceService service = new WorkspaceService();
    Path workspace = service.prepareWorkspace(meta);

    try {
      assertNotNull(workspace);
      assertTrue(Files.exists(workspace));
      assertTrue(Files.exists(workspace.resolve("hello.txt")));
      assertEquals("v1", Files.readString(workspace.resolve("hello.txt"), StandardCharsets.UTF_8));
    } finally {
      service.cleanupWorkspace(workspace);
    }
  }

  @Test
  void prepareWorkspace_missingRepoUrl_throws() {
    GitMetadata meta = new GitMetadata();
    meta.setRepositoryUrl("  ");
    meta.setBranch("master");
    meta.setCommitHash("abc");

    WorkspaceService service = new WorkspaceService();
    assertThrows(WorkspaceException.class, () -> service.prepareWorkspace(meta));
  }

  @Test
  void prepareWorkspace_missingBranch_throws() {
    GitMetadata meta = new GitMetadata();
    meta.setRepositoryUrl("/tmp/repo");
    meta.setBranch(null);
    meta.setCommitHash("abc");

    WorkspaceService service = new WorkspaceService();
    assertThrows(WorkspaceException.class, () -> service.prepareWorkspace(meta));
  }

  @Test
  void prepareWorkspace_missingCommitHash_throws() {
    GitMetadata meta = new GitMetadata();
    meta.setRepositoryUrl("/tmp/repo");
    meta.setBranch("master");
    meta.setCommitHash("  ");

    WorkspaceService service = new WorkspaceService();
    assertThrows(WorkspaceException.class, () -> service.prepareWorkspace(meta));
  }
}
