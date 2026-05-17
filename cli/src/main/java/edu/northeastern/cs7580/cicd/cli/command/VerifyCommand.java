package edu.northeastern.cs7580.cicd.cli.command;

import edu.northeastern.cs7580.cicd.cli.core.DefaultPathResolver;
import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.core.PathPolicy;
import edu.northeastern.cs7580.cicd.cli.core.RepoRootDetector;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineServiceFactory;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Implements the {@code verify} CLI subcommand for validating CI/CD pipeline
 * configuration files.
 *
 * <p>This command is responsible for validating the invocation context and
 * input path before any semantic pipeline validation is performed. It ensures
 * that the CLI is executed from within a Git repository and that the
 * provided configuration path complies with repository and project layout
 * constraints.
 *
 * <p>The {@code VerifyCommand} enforces the following rules:
 * <ul>
 *   <li>The CLI must be executed from within a Git repository
 *       (a {@code .git/} directory must be found by traversing upward).</li>
 *   <li>The input path must be repo-relative; absolute paths are rejected.</li>
 *   <li>The resolved path must be located under the {@code .pipelines/}
 *       directory at the repository root.</li>
 *   <li>The resolved path must exist and be located under the {@code .pipelines/}
 *     directory. It may refer to either a single YAML file or a directory
 *     containing multiple pipeline configuration files.</li>
 * </ul>
 *
 * <p>Pipeline semantic validation is delegated to {@link PipelineService}
 * from the pipeline library.
 */
@Command(
    name = "verify",
    description = "Validate a pipeline YAML configuration",
    mixinStandardHelpOptions = true
)
public class VerifyCommand implements Callable<Integer> {

  private final DefaultPathResolver defaultPathResolver;
  private final RepoRootDetector repoRootDetector;
  private final PathPolicy pathPolicy;
  private final PipelineService pipelineService;

  @Parameters(
      index = "0",
      arity = "0..1",
      description = "Repo-relative path to YAML file or directory"
  )
  private String relativePath;

  /**
   * Creates a new verify command with default dependencies.
   */
  public VerifyCommand() {
    this(
        new DefaultPathResolver(),
        new RepoRootDetector(),
        new PathPolicy(),
        PipelineServiceFactory.create()
    );
  }

  /**
   * Creates a new verify command with injected dependencies.
   *
   * @param defaultPathResolver resolver used when no input path is provided
   * @param repoRootDetector detector used to find the Git repository root
   * @param pathPolicy policy used to enforce path validation rules
   * @param pipelineService service from pipeline library for validation
   */
  public VerifyCommand(
      DefaultPathResolver defaultPathResolver,
      RepoRootDetector repoRootDetector,
      PathPolicy pathPolicy,
      PipelineService pipelineService) {
    this.defaultPathResolver = defaultPathResolver;
    this.repoRootDetector = repoRootDetector;
    this.pathPolicy = pathPolicy;
    this.pipelineService = pipelineService;
  }

  /**
   * Executes the verify subcommand.
   *
   * <p>This implementation finds the Git repository root by traversing upward,
   * validates the input path, and supports both single-file and directory
   * validation using the pipeline library.
   *
   * @return the process exit code
   */
  @Override
  public Integer call() {
    Path cwd = Paths.get("").toAbsolutePath().normalize();
    Path repoRoot = repoRootDetector.findRepoRoot(cwd);

    if (repoRoot == null) {
      System.err.println(
          "Error: cicd must be run from within a Git repository");
      return ExitCodes.GIT_REPOSITORY_ERROR;
    }

    String pathToVerify = defaultPathResolver.resolve(relativePath);

    Path input;
    Path resolved;
    try {
      input = pathPolicy.parseAndRejectAbsolute(pathToVerify);
      resolved = pathPolicy.resolveUnderRepoRoot(repoRoot, input);
      pathPolicy.enforceUnderPipelines(repoRoot, resolved);

      if (Files.isDirectory(resolved)) {
        pipelineService.validateDirectory(resolved);
      } else {
        pathPolicy.enforceExistingRegularFile(resolved);
        pipelineService.validatePipeline(resolved);
      }

    } catch (IllegalArgumentException ex) {
      System.err.println("Error: " + ex.getMessage());
      return ExitCodes.INVALID_CLI_ARGUMENTS;
    } catch (ValidationException ex) {
      System.err.println(ex.getMessage());
      return ExitCodes.CONFIG_VALIDATION_ERROR;
    }

    System.out.println("OK: " + resolved);
    return ExitCodes.OK;
  }

  /**
   * Sets the relative path for tests.
   *
   * @param relativePath repo-relative path argument to simulate CLI input
   */
  void setRelativePathForTest(String relativePath) {
    this.relativePath = relativePath;
  }
}