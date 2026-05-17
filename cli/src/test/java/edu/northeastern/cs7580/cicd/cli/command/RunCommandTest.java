package edu.northeastern.cs7580.cicd.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.northeastern.cs7580.cicd.cli.client.PipelineExecutionClient;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunRequestDto;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunResponseDto;
import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.core.GitStateException;
import edu.northeastern.cs7580.cicd.cli.core.GitStateValidator;
import edu.northeastern.cs7580.cicd.cli.core.RepoRootDetector;
import java.lang.reflect.Field;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class RunCommandTest {

  @Test
  void defaultConstructor_shouldCreateInstance() {
    RunCommand command = new RunCommand();
    assertNotNull(command);
  }

  @Test
  void constructor_shouldThrow_whenExecutionClientIsNull() {
    NullPointerException ex =
        assertThrows(NullPointerException.class, () -> new RunCommand(null));
    assertEquals("executionClient", ex.getMessage());
  }

  @Test
  void parse_runByName_appliesDefaults() throws Exception {
    RunCommand cmd = new RunCommand(new NoOpExecutionClient());
    CommandLine line = new CommandLine(cmd);

    line.parseArgs("--name", "default");

    assertEquals("default", getField(cmd, "name", String.class));
    assertEquals("main", getField(cmd, "branch", String.class));
    assertEquals("latest", getField(cmd, "commit", String.class));
  }

  @Test
  void parse_runByFile_parsesPath() throws Exception {
    RunCommand cmd = new RunCommand(new NoOpExecutionClient());
    CommandLine line = new CommandLine(cmd);

    line.parseArgs("--file", ".pipelines/a.yaml");

    Path parsed = getField(cmd, "file", Path.class);
    assertNotNull(parsed);
    assertTrue(parsed.toString().endsWith(".pipelines/a.yaml"));
    assertEquals("main", getField(cmd, "branch", String.class));
    assertEquals("latest", getField(cmd, "commit", String.class));
  }

  private static <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
    Field f = target.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    return type.cast(f.get(target));
  }

  /**
   * No-op client to avoid real HTTP calls in parsing tests.
   */
  private static final class NoOpExecutionClient implements PipelineExecutionClient {

    @Override
    public edu.northeastern.cs7580.cicd.cli.client.dto.RunResponseDto executePipeline(
        edu.northeastern.cs7580.cicd.cli.client.dto.RunRequestDto request) {
      throw new UnsupportedOperationException("Not used by parsing tests");
    }
  }

  @Test
  void call_returnsGitRepoErrorWhenGitValidationFails() {
    PipelineExecutionClient executionClient = new ThrowingExecutionClient();
    RepoRootDetector repoRootDetector = new FixedRepoRootDetector(Path.of("."));
    GitStateValidator gitStateValidator = new ThrowingGitStateValidator("Branch mismatch");

    RunCommand cmd = new RunCommand(executionClient, repoRootDetector, gitStateValidator);
    new CommandLine(cmd).parseArgs("--name", "default");

    int exitCode = cmd.call();
    assertEquals(ExitCodes.GIT_REPOSITORY_ERROR, exitCode);
  }

  /**
   * Implements a {@link GitStateValidator} that always fails validation.
   */
  private static final class ThrowingGitStateValidator implements GitStateValidator {

    private final String message;

    /**
     * Creates a validator that always throws {@link GitStateException}.
     *
     * @param message exception message
     */
    private ThrowingGitStateValidator(String message) {
      this.message = message;
    }

    @Override
    public void validate(Path repoRoot, String requestedBranch, String requestedCommit) {
      throw new GitStateException(message);
    }
  }

  /**
   * Implements a {@link RepoRootDetector} that always returns a fixed repo root.
   */
  private static final class FixedRepoRootDetector extends RepoRootDetector {

    private final Path repoRoot;

    /**
     * Creates a detector that always returns the given repository root.
     *
     * @param repoRoot repository root
     */
    private FixedRepoRootDetector(Path repoRoot) {
      this.repoRoot = repoRoot;
    }

    @Override
    public Path findRepoRoot(Path start) {
      return repoRoot;
    }
  }

  /**
   * Implements an execution client that must not be called in this test.
   */
  private static final class ThrowingExecutionClient implements PipelineExecutionClient {

    @Override
    public RunResponseDto executePipeline(RunRequestDto request) {
      throw new AssertionError("executePipeline must not be called when Git validation fails.");
    }
  }
}
