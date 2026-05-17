package edu.northeastern.cs7580.cicd.cli.command;

import edu.northeastern.cs7580.cicd.cli.client.ApiGatewayStatusClient;
import edu.northeastern.cs7580.cicd.cli.client.PipelineStatusClient;
import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.exception.ReportNotFoundException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;

/**
 * Implements the {@code cicd status} subcommand for querying live pipeline execution status.
 *
 * <p>Supports two modes:
 * <ol>
 *   <li>{@code --repo <url>} — returns the active (RUNNING) or most recently completed run
 *       for the given repository URL</li>
 *   <li>{@code --pipeline <name> --run <n>} — returns the status of a specific run</li>
 * </ol>
 *
 * <p>Output is rendered as a stage/job hierarchy in YAML:
 * <pre>
 * build:
 *   status: SUCCESS
 *   compile:
 *     status: SUCCESS
 *   test:
 *     status: RUNNING
 * </pre>
 *
 * <p>Exit codes:
 * <ul>
 *   <li>{@link ExitCodes#OK} — status printed successfully</li>
 *   <li>{@link ExitCodes#INVALID_CLI_ARGUMENTS} — option validation failure</li>
 *   <li>{@link ExitCodes#CONFIG_VALIDATION_ERROR} — pipeline or run not found (HTTP 404)</li>
 *   <li>{@link ExitCodes#RUNTIME_EXECUTION_ERROR} — API Gateway unreachable or unexpected
 *       error</li>
 * </ul>
 */
@CommandLine.Command(
    name = "status",
    description = "Query live execution status of a pipeline run.",
    mixinStandardHelpOptions = true,
    optionListHeading = "%nOptions:%n",
    footer = {
        "",
        "Usage examples:",
        "  cicd status --repo https://github.com/org/repo",
        "  cicd status --pipeline default --run 3",
        ""
    }
)
public final class StatusCommand implements Callable<Integer> {

  /** Repository URL — returns the active or most recently completed run for this repo. */
  @CommandLine.Option(
      names = "--repo",
      description = "Repository URL. Returns the active or most recent run for this repo."
  )
  private String repo;

  /** Pipeline name — used with --run to query a specific run. */
  @CommandLine.Option(
      names = "--pipeline",
      description = "Pipeline name. Use with --run to query a specific run."
  )
  private String pipeline;

  /** Run number — requires --pipeline. */
  @CommandLine.Option(
      names = "--run",
      description = "Run number to query. Requires --pipeline."
  )
  private Integer run;

  private final PipelineStatusClient statusClient;

  /** Creates a {@code StatusCommand} using the default {@link ApiGatewayStatusClient}. */
  public StatusCommand() {
    this(new ApiGatewayStatusClient());
  }

  /**
   * Creates a {@code StatusCommand} with an injected client (for testing).
   *
   * @param statusClient the client used to fetch status data
   */
  public StatusCommand(PipelineStatusClient statusClient) {
    this.statusClient = statusClient;
  }

  @Override
  public Integer call() {
    try {
      validateOptions();
    } catch (CommandLine.ParameterException e) {
      System.err.println(e.getMessage());
      return ExitCodes.INVALID_CLI_ARGUMENTS;
    }

    try {
      Map<String, Object> response = fetchStatus();
      System.out.print(toYaml(toOutputMap(response)));
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

  private void validateOptions() {
    boolean hasRepo = repo != null;
    boolean hasPipeline = pipeline != null;

    if (!hasRepo && !hasPipeline) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "One of --repo or --pipeline must be provided.");
    }
    if (hasRepo && hasPipeline) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "--repo and --pipeline are mutually exclusive.");
    }
    if (hasPipeline && run == null) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "--pipeline requires --run.");
    }
    if (hasRepo && run != null) {
      throw new CommandLine.ParameterException(
          new CommandLine(this), "--run cannot be used with --repo.");
    }
  }

  private Map<String, Object> fetchStatus() throws Exception {
    if (repo != null) {
      return statusClient.getStatusByRepo(repo);
    }
    return statusClient.getStatusByRun(pipeline, run);
  }

  /**
   * Transforms the flat API response into a stage/job nested map for YAML rendering.
   *
   * <p>Input (from JSON):
   * {@code {pipeline-name, run-no, status, stages: [{name, status, jobs: [...]}]}}
   *
   * <p>Output: {@code {build: {status: SUCCESS, compile: {status: SUCCESS}, ...}, ...}}
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> toOutputMap(Map<String, Object> response) {
    Map<String, Object> output = new LinkedHashMap<>();
    List<?> stages = (List<?>) response.get("stages");
    if (stages == null) {
      return output;
    }
    for (Object stageObj : stages) {
      Map<String, Object> stage = (Map<String, Object>) stageObj;
      Map<String, Object> stageMap = new LinkedHashMap<>();
      stageMap.put("status", stage.get("status"));
      List<?> jobs = (List<?>) stage.get("jobs");
      if (jobs != null) {
        for (Object jobObj : jobs) {
          Map<String, Object> job = (Map<String, Object>) jobObj;
          Map<String, Object> jobMap = new LinkedHashMap<>();
          jobMap.put("status", job.get("status"));
          stageMap.put((String) job.get("name"), jobMap);
        }
      }
      output.put((String) stage.get("name"), stageMap);
    }
    return output;
  }

  private String toYaml(Map<String, Object> data) {
    DumperOptions opts = new DumperOptions();
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    opts.setPrettyFlow(true);
    return new Yaml(opts).dump(data);
  }
}
