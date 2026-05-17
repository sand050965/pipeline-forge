package edu.northeastern.cs7580.cicd.cli.client.dto;

/**
 * Request payload for starting a pipeline execution.
 */
public class RunRequestDto {

  private String name;
  private String file;
  private String gitBranch;
  private String gitCommit;
  private String pipelineFilePath;
  private String repositoryUrl;
  private String resolvedCommitHash;

  /** Default constructor. */
  public RunRequestDto() {}

  /**
   * Creates a new request DTO.
   *
   * @param name pipeline name (nullable)
   * @param file relative file path (nullable)
   * @param gitBranch git branch
   * @param gitCommit git commit
   */
  public RunRequestDto(String name, String file, String gitBranch, String gitCommit) {
    this.name = name;
    this.file = file;
    this.gitBranch = gitBranch;
    this.gitCommit = gitCommit;
  }

  /** Returns the pipeline name. */
  public String getName() {
    return name;
  }

  /** Returns the relative file path. */
  public String getFile() {
    return file;
  }

  /** Returns the git branch. */
  public String getGitBranch() {
    return gitBranch;
  }

  /** Returns the git commit. */
  public String getGitCommit() {
    return gitCommit;
  }

  /** Returns the relative path to the pipeline YAML file. */
  public String getPipelineFilePath() {
    return pipelineFilePath;
  }

  /**
   * Sets the relative path to the pipeline YAML file.
   *
   * @param pipelineFilePath relative path from repo root (e.g. .pipelines/default.yaml)
   */
  public void setPipelineFilePath(String pipelineFilePath) {
    this.pipelineFilePath = pipelineFilePath;
  }

  /** Returns the git repository URL. */
  public String getRepositoryUrl() {
    return repositoryUrl;
  }

  /**
   * Sets the git repository URL.
   *
   * @param repositoryUrl the repository URL
   */
  public void setRepositoryUrl(String repositoryUrl) {
    this.repositoryUrl = repositoryUrl;
  }

  /** Returns the resolved full commit hash. */
  public String getResolvedCommitHash() {
    return resolvedCommitHash;
  }

  /**
   * Sets the resolved full commit hash.
   *
   * @param resolvedCommitHash the full SHA commit hash
   */
  public void setResolvedCommitHash(String resolvedCommitHash) {
    this.resolvedCommitHash = resolvedCommitHash;
  }

}
