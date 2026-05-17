package edu.northeastern.cs7580.cicd.cli.command;

import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.core.PathPolicy;
import edu.northeastern.cs7580.cicd.cli.core.RepoRootDetector;
import edu.northeastern.cs7580.cicd.cli.formatter.DefaultDryrunFormatter;
import edu.northeastern.cs7580.cicd.cli.formatter.DryrunFormatter;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineServiceFactory;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Implements the {@code dryrun} CLI subcommand for validating CI/CD pipeline
 * configuration files and previewing their execution order.
 *
 * <p>This command validates the specified pipeline configuration file and
 * generates a dry-run execution plan without executing any jobs or stages.
 * The output shows the execution order in YAML format.
 *
 * <p>Pipeline validation and execution plan generation are delegated to
 * {@link PipelineService} from the pipeline library. Output formatting is
 * delegated to {@link DryrunFormatter}.
 */
@Command(
    name = "dryrun",
    description = "Validate a pipeline file and print the execution order as YAML",
    mixinStandardHelpOptions = true
)
public class DryrunCommand implements Callable<Integer> {

  @Parameters(index = "0", description = "Path to a pipeline YAML file, relative to the repo root")
  private String filePath;

  private final PipelineService pipelineService;
  private final RepoRootDetector repoRootDetector;
  private final PathPolicy pathPolicy;
  private final DryrunFormatter formatter;

  /**
   * Creates a new {@code DryrunCommand} with default dependencies.
   */
  public DryrunCommand() {
    this(
        PipelineServiceFactory.create(),
        new RepoRootDetector(),
        new PathPolicy(),
        new DefaultDryrunFormatter()
    );
  }

  /**
   * Creates a new {@code DryrunCommand} with injected dependencies.
   *
   * @param pipelineService pipeline service from library
   * @param repoRootDetector detector used to find the Git repository root
   * @param pathPolicy policy used to enforce path validation rules
   * @param formatter formatter for rendering execution plans
   */
  public DryrunCommand(
      PipelineService pipelineService,
      RepoRootDetector repoRootDetector,
      PathPolicy pathPolicy,
      DryrunFormatter formatter) {
    this.pipelineService = pipelineService;
    this.repoRootDetector = repoRootDetector;
    this.pathPolicy = pathPolicy;
    this.formatter = formatter;
  }

  /**
   * Executes the {@code dryrun} command.
   *
   * <p>This method validates and parses the pipeline configuration file,
   * creates an execution plan, and prints a YAML-style preview of the
   * execution plan to standard output.
   *
   * @return {@code 0} if validation and formatting succeed; non-zero on error
   */
  @Override
  public Integer call() {
    Path cwd = Paths.get("").toAbsolutePath().normalize();
    Path repoRoot = repoRootDetector.findRepoRoot(cwd);

    if (repoRoot == null) {
      System.err.println("Error: cicd must be run from within a Git repository");
      return ExitCodes.GIT_REPOSITORY_ERROR;
    }

    Path resolved;
    try {
      Path input = pathPolicy.parseAndRejectAbsolute(filePath);
      resolved = pathPolicy.resolveUnderRepoRoot(repoRoot, input);
      pathPolicy.enforceUnderPipelines(repoRoot, resolved);
      pathPolicy.enforceExistingRegularFile(resolved);

      Pipeline pipeline = pipelineService.validatePipeline(resolved);
      ExecutionPlan plan = pipelineService.createExecutionPlan(resolved);

      System.out.println("OK: dryrun validation succeeded");

      String formattedPlan = formatter.format(pipeline, plan);
      System.out.print(formattedPlan);

      return ExitCodes.OK;

    } catch (IllegalArgumentException ex) {
      System.err.println("Error: " + ex.getMessage());
      return ExitCodes.INVALID_CLI_ARGUMENTS;
    } catch (ValidationException e) {
      System.err.println(e.getMessage());
      return ExitCodes.CONFIG_VALIDATION_ERROR;
    }
  }

  /**
   * Returns the file path argument.
   *
   * @return file path provided by the user
   */
  public String getFilePath() {
    return filePath;
  }
}