package edu.northeastern.cs7580.cicd.cli.command;

import edu.northeastern.cs7580.cicd.cli.client.ApiGatewayClient;
import edu.northeastern.cs7580.cicd.cli.client.PipelineExecutionClient;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunRequestDto;
import edu.northeastern.cs7580.cicd.cli.client.dto.RunResponseDto;
import edu.northeastern.cs7580.cicd.cli.core.DefaultGitStateValidator;
import edu.northeastern.cs7580.cicd.cli.core.ExitCodes;
import edu.northeastern.cs7580.cicd.cli.core.GitStateException;
import edu.northeastern.cs7580.cicd.cli.core.GitStateValidator;
import edu.northeastern.cs7580.cicd.cli.core.OptionValidator;
import edu.northeastern.cs7580.cicd.cli.core.RepoRootDetector;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import picocli.CommandLine;

/**
 * Implements the {@code run} CLI subcommand for executing CI/CD pipelines.
 *
 * <p>This command is responsible for validating the invocation context and
 * input options before delegating execution to the controller via HTTP. It
 * ensures that the CLI is executed from within a Git repository and that the
 * requested branch/commit matches the current repository state to avoid
 * confusion about which code version is being executed.
 *
 * <p>The {@code RunCommand} enforces the following rules:
 * <ul>
 *   <li>Exactly one of {@code --name} or {@code --file} must be provided.</li>
 *   <li>The CLI must be executed from within a Git repository
 *       (a {@code .git/} directory must be found by traversing upward).</li>
 *   <li>If {@code --branch} is specified, it must match the current branch
 *       (detached HEAD is rejected when a branch is required).</li>
 *   <li>If {@code --commit} is specified and not {@code latest}, it must match
 *       the current {@code HEAD} commit (full SHA or prefix).</li>
 * </ul>
 *
 * <p>Remote execution is delegated to {@link PipelineExecutionClient}.
 */
@CommandLine.Command(
    name = "run",
    description = "Execute a CI/CD pipeline by name or configuration file.",
    mixinStandardHelpOptions = true,
    optionListHeading = "%nOptions:%n",
    footer = {
        "",
        "Usage examples:",
        "  cicd run --name default",
        "  cicd run --file .pipelines/release.yaml",
        "  cicd run --name default --branch my-feature",
        "  cicd run --name default --branch my-feature --commit 61b1b60",
        ""
    }
)
public final class RunCommand implements Callable<Integer> {

  private static final String PIPELINES_DIR = ".pipelines";

  /** Pipeline name defined under .pipelines. */
  @CommandLine.Option(
      names = "--name",
      description = "Pipeline name defined in a YAML file under .pipelines. "
          + "Exactly one of --name or --file must be provided."
  )
  private String name;

  /** Relative path to a pipeline configuration file. */
  @CommandLine.Option(
      names = "--file",
      description = "Path to a pipeline YAML file under .pipelines (relative to repo root). "
          + "Exactly one of --name or --file must be provided."
  )
  private Path file;

  /** Git branch to execute from. */
  @CommandLine.Option(
      names = "--branch",
      defaultValue = "main",
      description = "Git branch to use for the pipeline run (default: ${DEFAULT-VALUE})."
  )
  private String branch;

  /** Git commit hash or the keyword 'latest'. */
  @CommandLine.Option(
      names = "--commit",
      defaultValue = "latest",
      description = "Git commit to use on the selected branch (default: ${DEFAULT-VALUE}). "
          + "Use 'latest' to select the branch HEAD."
  )
  private String commit;

  /** Run using local repository path instead of remote URL. */
  @CommandLine.Option(
      names = "--local",
      description = "Use local repository path for execution (default: remote URL)."
  )
  private boolean local;

  private final PipelineExecutionClient executionClient;
  private final RepoRootDetector repoRootDetector;
  private final GitStateValidator gitStateValidator;

  /** Creates a run command using the default {@link ApiGatewayClient}. */
  public RunCommand() {
    this(new ApiGatewayClient(), new RepoRootDetector(), new DefaultGitStateValidator());
  }

  /**3we
   * Creates a run command with an injected execution client.
   *
   * @param executionClient gateway client to use
   */
  public RunCommand(PipelineExecutionClient executionClient) {
    this(executionClient, new RepoRootDetector(), new DefaultGitStateValidator());
  }

  /**
   * Creates a run command with injected dependencies.
   *
   * @param executionClient gateway client to use
   * @param repoRootDetector repository root detector
   * @param gitStateValidator git state validator
   */
  public RunCommand(
      PipelineExecutionClient executionClient,
      RepoRootDetector repoRootDetector,
      GitStateValidator gitStateValidator) {
    this.executionClient = Objects.requireNonNull(executionClient, "executionClient");
    this.repoRootDetector = Objects.requireNonNull(repoRootDetector, "repoRootDetector");
    this.gitStateValidator = Objects.requireNonNull(gitStateValidator, "gitStateValidator");
  }

  /**
   * Executes the run command.
   *
   * @return process exit code
   */
  @Override
  public Integer call() {
    try {
      OptionValidator.validateRunOptions(name, file, new CommandLine(this));
    } catch (CommandLine.ParameterException e) {
      System.err.println(e.getMessage());
      return ExitCodes.INVALID_CLI_ARGUMENTS;
    }

    Path repoRoot;
    try {
      Path currentDir = Path.of("").toAbsolutePath();
      repoRoot = repoRootDetector.findRepoRoot(currentDir);
      if (repoRoot == null) {
        System.err.println("Not inside a Git repository.");
        return ExitCodes.GIT_REPOSITORY_ERROR;
      }
      gitStateValidator.validate(repoRoot, branch, commit);
    } catch (GitStateException e) {
      System.err.println(e.getMessage());
      return ExitCodes.GIT_REPOSITORY_ERROR;
    }

    String pipelineFilePath;
    try {
      pipelineFilePath = resolveYamlFilePath(repoRoot, name, file);
    } catch (IOException e) {
      System.err.println("Failed to read pipeline configuration: " + e.getMessage());
      return ExitCodes.RUNTIME_EXECUTION_ERROR;
    }

    String repositoryUrl;
    String resolvedCommit;
    try {
      repositoryUrl = local ? repoRoot.toAbsolutePath().toString() : resolveRemoteUrl(repoRoot);
      resolvedCommit = resolveCommitHash(repoRoot, commit);
    } catch (IOException e) {
      System.err.println("Failed to read Git metadata: " + e.getMessage());
      return ExitCodes.GIT_REPOSITORY_ERROR;
    }

    String pipelineName;
    if (name != null) {
      pipelineName = name;
    } else if (file != null) {
      try {
        pipelineName = extractPipelineName(repoRoot.resolve(file));
      } catch (IOException e) {
        System.err.println("Failed to read pipeline name from file: " + e.getMessage());
        return ExitCodes.RUNTIME_EXECUTION_ERROR;
      }
    } else {
      pipelineName = "unknown";
    }
    String filePath = file == null ? null : file.toString();
    RunRequestDto request = new RunRequestDto(pipelineName, filePath, branch, resolvedCommit);
    request.setPipelineFilePath(pipelineFilePath);
    request.setRepositoryUrl(repositoryUrl);
    request.setResolvedCommitHash(resolvedCommit);

    try {
      RunResponseDto response = executionClient.executePipeline(request);

      if ("VALIDATION_FAILED".equals(response.getStatus())) {
        System.err.println(response.getMessage());
        return ExitCodes.CONFIG_VALIDATION_ERROR;
      }
      System.out.println(response.getMessage());
      return ExitCodes.OK;

    } catch (Exception e) {
      String msg = e.getMessage();
      if (msg == null || msg.isBlank()) {
        System.err.println("Failed to connect to the API Gateway. Is it running?");
      } else {
        System.err.println(msg);
      }
      return ExitCodes.RUNTIME_EXECUTION_ERROR;
    }
  }

  /**
   * Resolves the relative path to the pipeline YAML file.
   *
   * <p>If {@code pipelineFile} is provided, returns its relative path directly.
   * Otherwise, scans all YAML files under {@code {repoRoot}/.pipelines/} for one
   * whose {@code pipeline.name} field matches the given {@code pipelineName}, and
   * returns its path relative to the repo root.
   *
   * @param repoRoot repository root directory
   * @param pipelineName pipeline name to search for (nullable)
   * @param pipelineFile explicit file path (nullable)
   * @return relative path from repo root (e.g. .pipelines/default.yaml)
   * @throws IOException if no matching file is found
   */
  private String resolveYamlFilePath(Path repoRoot, String pipelineName, Path pipelineFile)
      throws IOException {
    if (pipelineFile != null) {
      return pipelineFile.toString();
    }

    Path pipelinesDir = repoRoot.resolve(PIPELINES_DIR);
    if (!Files.isDirectory(pipelinesDir)) {
      throw new IOException("Pipelines directory not found: " + pipelinesDir);
    }

    try (DirectoryStream<Path> stream =
             Files.newDirectoryStream(pipelinesDir, "*.{yaml,yml}")) {
      for (Path candidate : stream) {
        String content = Files.readString(candidate);
        if (containsPipelineName(content, pipelineName)) {
          return repoRoot.relativize(candidate).toString();
        }
      }
    }

    throw new IOException(
        "No pipeline configuration found with name '" + pipelineName + "' in " + pipelinesDir);
  }

  /**
   * Checks whether YAML content contains a matching pipeline name declaration.
   *
   * @param yamlContent raw YAML content
   * @param pipelineName expected pipeline name
   * @return true if the content declares the given pipeline name
   */
  private boolean containsPipelineName(String yamlContent, String pipelineName) {
    for (String line : yamlContent.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("name:")) {
        String value = trimmed.substring("name:".length()).trim();
        if (value.equals(pipelineName)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Reads the pipeline name from a YAML configuration file.
   *
   * @param filePath path to the pipeline YAML file
   * @return the pipeline name defined under the {@code pipeline.name} key
   * @throws IOException if the file cannot be read or no pipeline name is found
   */
  private String extractPipelineName(Path filePath) throws IOException {
    String content = Files.readString(filePath);
    boolean inPipelineSection = false;
    for (String line : content.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.equals("pipeline:")) {
        inPipelineSection = true;
        continue;
      }
      if (inPipelineSection && trimmed.startsWith("name:")) {
        return trimmed.substring("name:".length()).trim();
      }
      if (inPipelineSection && !line.startsWith(" ") && !line.startsWith("\t")
          && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
        inPipelineSection = false;
      }
    }
    throw new IOException("No 'pipeline.name' found in: " + filePath);
  }

  /**
   * Resolves the commit hash from the repository HEAD.
   *
   * <p>If the requested commit is {@code "latest"}, returns the full SHA of
   * the current HEAD. Otherwise returns the requested value as-is.
   *
   * @param repoRoot repository root directory
   * @param requestedCommit commit hash or "latest"
   * @return full commit SHA
   * @throws IOException if the repository cannot be opened or HEAD is unresolvable
   */
  private String resolveRemoteUrl(Path repoRoot) throws IOException {
    File gitDir = repoRoot.resolve(".git").toFile();
    try (Repository repository = new FileRepositoryBuilder()
        .setGitDir(gitDir)
        .readEnvironment()
        .build()) {
      String url = repository.getConfig().getString("remote", "origin", "url");
      if (url != null && !url.isBlank()) {
        return normalizeRemoteUrl(url);
      }
    }
    return repoRoot.toAbsolutePath().toString();
  }

  private String normalizeRemoteUrl(String url) {
    String trimmed = url.trim();
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      return trimmed;
    }
    String sshPrefix = "git@";
    int colonIndex = trimmed.indexOf(':');
    if (trimmed.startsWith(sshPrefix) && colonIndex > sshPrefix.length()) {
      String host = trimmed.substring(sshPrefix.length(), colonIndex);
      String path = trimmed.substring(colonIndex + 1);
      return "https://" + host + "/" + path;
    }
    String sshSchemePrefix = "ssh://git@";
    if (trimmed.startsWith(sshSchemePrefix)) {
      String remainder = trimmed.substring(sshSchemePrefix.length());
      int slashIndex = remainder.indexOf('/');
      if (slashIndex > 0) {
        String host = remainder.substring(0, slashIndex);
        String path = remainder.substring(slashIndex + 1);
        return "https://" + host + "/" + path;
      }
    }
    return trimmed;
  }

  private String resolveCommitHash(Path repoRoot, String requestedCommit) throws IOException {
    if (!"latest".equals(requestedCommit)) {
      return requestedCommit;
    }
    File gitDir = repoRoot.resolve(".git").toFile();
    try (Repository repository = new FileRepositoryBuilder()
        .setGitDir(gitDir)
        .readEnvironment()
        .build()) {
      var head = repository.resolve(Constants.HEAD);
      if (head == null) {
        throw new IOException("Unable to resolve HEAD commit.");
      }
      return head.name();
    }
  }

}
