package edu.northeastern.cs7580.cicd.cli.command;

import edu.northeastern.cs7580.cicd.cli.client.ApiGatewayReportClient;
import edu.northeastern.cs7580.cicd.cli.client.PipelineReportClient;
import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.exception.ReportNotFoundException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.Map;
import java.util.concurrent.Callable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;

/**
 * Implements the {@code cicd report} subcommand for retrieving and displaying
 * pipeline execution reports from the CI/CD system.
 *
 * <p>Supports four levels of granularity, determined by which options are provided:
 * <ol>
 *   <li>{@code --pipeline <name>} — all runs for the named pipeline</li>
 *   <li>{@code --pipeline <name> --run <n>} — a specific run with stage summaries</li>
 *   <li>{@code --pipeline <name> --run <n> --stage <s>} — a specific stage with job summaries</li>
 *   <li>{@code --pipeline <name> --run <n> --stage <s> --job <j>} — a single job</li>
 * </ol>
 *
 * <p>Option dependencies are enforced at call time:
 * {@code --run} requires {@code --pipeline} (enforced by Picocli's {@code required = true});
 * {@code --stage} requires {@code --run}; {@code --job} requires {@code --stage}.
 *
 * <p>The JSON response from the API Gateway is deserialized into a
 * {@code Map<String, Object>} and rendered as YAML to {@code stdout},
 * matching the spec's sample output format exactly.
 *
 * <p>Exit codes returned by this command:
 * <ul>
 *   <li>{@link ExitCodes#OK} — report printed successfully</li>
 *   <li>{@link ExitCodes#INVALID_CLI_ARGUMENTS} — option dependency violation
 *       (e.g. {@code --stage} without {@code --run})</li>
 *   <li>{@link ExitCodes#CONFIG_VALIDATION_ERROR} — pipeline, run, stage, or job not found
 *       (HTTP 404)</li>
 *   <li>{@link ExitCodes#RUNTIME_EXECUTION_ERROR} — API Gateway unreachable or
 *       returned an unexpected error</li>
 * </ul>
 *
 * @see PipelineReportClient
 * @see ApiGatewayReportClient
 * @see RunCommand
 * @see VerifyCommand
 */
@CommandLine.Command(
    name = "report",
    description = "Retrieve execution reports for pipelines, stages, and jobs.",
    mixinStandardHelpOptions = true,
    optionListHeading = "%nOptions:%n",
    footer = {
        "",
        "Usage examples:",
        "  cicd report --pipeline default",
        "  cicd report --pipeline default --run 1",
        "  cicd report --pipeline default --run 1 --stage build",
        "  cicd report --pipeline default --run 1 --stage build --job compile",
        ""
    }
)
public final class ReportCommand implements Callable<Integer> {

  /**
   * The pipeline name to report on. Matches {@code pipeline.name} in the YAML
   * configuration file. Required — all other options depend on this one.
   */
  @CommandLine.Option(
      names = "--pipeline",
      description = "Name of the pipeline to report on (matches pipeline.name in the config file).",
      required = true
  )
  private String pipeline;

  /**
   * The run number to filter by. Run numbers start at 1 and increment with each
   * execution. When omitted, reports on all runs for the pipeline.
   */
  @CommandLine.Option(
      names = "--run",
      description = "Run number to report on. Requires --pipeline."
  )
  private Integer run;

  /**
   * The stage name to filter by. When omitted, reports on all stages in the run.
   * Requires {@code --run}.
   */
  @CommandLine.Option(
      names = "--stage",
      description = "Stage name to report on. Requires --run."
  )
  private String stage;

  /**
   * The job name to filter by. When omitted, reports on all jobs in the stage.
   * Requires {@code --stage}.
   */
  @CommandLine.Option(
      names = "--job",
      description = "Job name to report on. Requires --stage."
  )
  private String job;

  /** Client used to fetch report data from the API Gateway. */
  private final PipelineReportClient reportClient;

  /**
   * Creates a {@code ReportCommand} using the default {@link ApiGatewayReportClient},
   * which resolves the API Gateway URL from {@code CliConfig}.
   *
   * <p>This is the constructor used during normal CLI operation when Picocli
   * instantiates the command.
   */
  public ReportCommand() {
    this(new ApiGatewayReportClient());
  }

  /**
   * Creates a {@code ReportCommand} with an injected {@link PipelineReportClient}.
   *
   * <p>This constructor exists for unit testing, where a mock client can be
   * injected to verify option parsing and output formatting without making
   * real HTTP requests.
   *
   * @param reportClient the client used to fetch report data; must not be {@code null}
   */
  public ReportCommand(PipelineReportClient reportClient) {
    this.reportClient = reportClient;
  }

  /**
   * Executes the report command.
   *
   * <p>Validates option dependencies, fetches the appropriate report from the
   * API Gateway via {@link PipelineReportClient}, and prints the result as YAML
   * to {@code stdout}.
   *
   * @return {@link ExitCodes#OK} on success;
   *         {@link ExitCodes#INVALID_CLI_ARGUMENTS} on option dependency violation;
   *         {@link ExitCodes#CONFIG_VALIDATION_ERROR} if the resource is not found (HTTP 404);
   *         {@link ExitCodes#RUNTIME_EXECUTION_ERROR} if the API Gateway is unreachable
   *         or returns an unexpected error
   */
  @Override
  public Integer call() {
    try {
      validateOptions();
    } catch (CommandLine.ParameterException e) {
      System.err.println(e.getMessage());
      return ExitCodes.INVALID_CLI_ARGUMENTS;
    }

    try {
      Map<String, Object> report = fetchReport();
      System.out.print(toYaml(report));
      return ExitCodes.OK;
    } catch (ReportNotFoundException e) {
      System.err.println(e.getMessage());
      return ExitCodes.CONFIG_VALIDATION_ERROR;
    } catch (ConnectException | HttpConnectTimeoutException e) {
      System.err.println("Failed to connect to the API Gateway. Is it running?");
      return ExitCodes.RUNTIME_EXECUTION_ERROR;
    } catch (Exception e) {
      String msg = e.getMessage();
      System.err.println(msg == null || msg.isBlank()
          ? "Failed to connect to the API Gateway. Is it running?"
          : msg);
      return ExitCodes.RUNTIME_EXECUTION_ERROR;
    }
  }

  /**
   * Validates inter-option dependencies that Picocli cannot express declaratively.
   *
   * <p>Enforces:
   * <ul>
   *   <li>{@code --stage} requires {@code --run}</li>
   *   <li>{@code --job} requires {@code --stage}</li>
   * </ul>
   *
   * <p>{@code --pipeline} is always required and enforced by Picocli's
   * {@code required = true} annotation, so it is not checked here.
   *
   * @throws CommandLine.ParameterException if a dependency is violated, using the
   *     same exception type as Picocli so {@code call()} can handle all argument
   *     errors uniformly
   */
  private void validateOptions() {
    if (stage != null && run == null) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "--stage requires --run.");
    }
    if (job != null && stage == null) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "--job requires --stage.");
    }
  }

  /**
   * Dispatches to the correct {@link PipelineReportClient} method based on
   * which options were provided.
   *
   * <p>Resolution order (most specific wins):
   * <ol>
   *   <li>pipeline + run + stage + job → {@link PipelineReportClient#getJobReport}</li>
   *   <li>pipeline + run + stage → {@link PipelineReportClient#getStageReport}</li>
   *   <li>pipeline + run → {@link PipelineReportClient#getRunReport}</li>
   *   <li>pipeline only → {@link PipelineReportClient#getPipelineReport}</li>
   * </ol>
   *
   * @return the report map returned by the API Gateway
   * @throws Exception if the HTTP request fails
   */
  private Map<String, Object> fetchReport() throws Exception {
    if (job != null) {
      return reportClient.getJobReport(pipeline, run, stage, job);
    }
    if (stage != null) {
      return reportClient.getStageReport(pipeline, run, stage);
    }
    if (run != null) {
      return reportClient.getRunReport(pipeline, run);
    }
    return reportClient.getPipelineReport(pipeline);
  }

  /**
   * Serializes a report map to a YAML string using SnakeYAML.
   *
   * <p>Configured with {@link DumperOptions.FlowStyle#BLOCK} to produce
   * human-readable multi-line YAML matching the spec's sample output format,
   * rather than the compact inline style SnakeYAML uses by default.
   *
   * @param report the report map to serialize; must not be {@code null}
   * @return YAML string representation of the report, terminated with a newline
   */
  private String toYaml(Map<String, Object> report) {
    DumperOptions opts = new DumperOptions();
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    opts.setPrettyFlow(true);
    return new Yaml(opts).dump(report);
  }
}
