package edu.northeastern.cs7580.cicd.pipelinelib.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * The {@code Pipeline} class represents a complete CI/CD pipeline configuration.
 * All pipeline definitions in YAML configuration files are implemented as
 * instances of this class.
 *
 * <p>A pipeline is the top-level container that organizes the execution of jobs
 * across multiple stages. Each pipeline has metadata, a sequence of stages, and
 * a collection of jobs to be executed. Jobs are grouped by stage and executed
 * according to stage order and job dependencies.
 *
 * <p>Here are some examples of how pipelines can be used:
 * <blockquote><pre>
 *     PipelineMetadata metadata = PipelineMetadata.builder()
 *         .name("default")
 *         .description("Default build pipeline")
 *         .build();
 *
 *     Job buildJob = Job.builder()
 *         .name("compile")
 *         .stage("build")
 *         .image("gradle:jdk21")
 *         .script("gradle build")
 *         .build();
 *
 *     Pipeline pipeline = Pipeline.builder()
 *         .pipeline(metadata)
 *         .stages(List.of("build", "test", "deploy"))
 *         .jobs(Map.of("compile", buildJob))
 *         .build();
 * </pre></blockquote>
 *
 * <p>The class {@code Pipeline} includes methods for retrieving stages with
 * default fallback values and for accessing all jobs defined in the pipeline.
 * Pipelines are validated to ensure structural integrity before execution.
 *
 * <p>Pipeline instances are typically created by parsing YAML configuration files
 * located in the {@code .pipelines} directory at the repository root:
 * <blockquote><pre>
 * pipeline:
 *   name: default
 *   description: Default pipeline
 *
 * stages:
 *   - build
 *   - test
 *
 * compile:
 *   - stage: build
 *   - image: gradle:jdk21
 *   - script: gradle build
 * </pre></blockquote>
 *
 * <p>Unless otherwise noted, passing a {@code null} value for required
 * fields will result in validation errors.
 *
 * @implNote Pipelines are validated to ensure: (1) at least one stage exists,
 *     (2) all stages have at least one job, (3) all jobs reference valid stages,
 *     and (4) job dependencies form a valid directed acyclic graph within each stage.
 * @see PipelineMetadata
 * @see Job
 */
@Data
@Builder
public class Pipeline {

  /**
   * The metadata for this pipeline.
   *
   * <p>Contains the pipeline's unique name and optional description.
   * The pipeline name must be unique within the repository to avoid
   * configuration conflicts. The metadata provides identifying information
   * used in logs and reports.
   *
   * @see PipelineMetadata
   * @see PipelineMetadata#getName()
   * @see PipelineMetadata#getDescription()
   */
  private PipelineMetadata pipeline;

  /**
   * The ordered list of stages in this pipeline.
   *
   * <p>Defines the execution sequence for the pipeline. Stages execute in the
   * order specified in this list. If {@code null} or empty, default stages
   * {@code ["build", "test", "docs"]} are used. All jobs must be assigned to
   * one of these stages.
   *
   * <p>Stage execution is sequential: all jobs in stage N must complete before
   * any job in stage N+1 begins. Within a single stage, jobs may execute in
   * parallel unless constrained by {@link Job#getNeeds() dependency declarations}.
   *
   * @see #getStagesOrDefault()
   * @see Job#getStage()
   */
  private List<String> stages;

  /**
   * The collection of all jobs defined in this pipeline.
   *
   * <p>Maps job names to their corresponding {@link Job} objects. Job names
   * serve as unique identifiers within the pipeline and are used for dependency
   * declarations. The map key is the job name, and the value is the complete
   * job configuration.
   *
   * <p>Jobs are organized by their {@link Job#getStage() stage assignment}
   * during execution. Each job defines its container image, script commands,
   * and optional dependencies on other jobs within the same stage.
   *
   * @see Job
   * @see Job#getName()
   * @see Job#getStage()
   * @see Job#getNeeds()
   */
  private Map<String, Job> jobs;

  /**
   * Returns the stages for this pipeline, or default stages if none are specified.
   *
   * <p>This method provides a consistent way to retrieve the stage list, handling
   * the case where stages are not explicitly defined. The default stages are used
   * when the {@link #stages} field is {@code null} or empty, following common
   * CI/CD conventions.
   *
   * <p>Behavior:
   * <ul>
   *   <li>If {@code stages} is {@code null}, returns {@code ["build", "test", "docs"]}</li>
   *   <li>If {@code stages} is empty list, returns {@code ["build", "test", "docs"]}</li>
   *   <li>Otherwise, returns the user-defined {@code stages}</li>
   * </ul>
   *
   * <p>Examples:
   * <blockquote><pre>
   *     Pipeline p1 = Pipeline.builder()
   *         .stages(null)
   *         .build();
   *     p1.getStagesOrDefault();  // returns ["build", "test", "docs"]
   *
   *     Pipeline p2 = Pipeline.builder()
   *         .stages(List.of())
   *         .build();
   *     p2.getStagesOrDefault();  // returns ["build", "test", "docs"]
   *
   *     Pipeline p3 = Pipeline.builder()
   *         .stages(List.of("lint", "compile", "deploy"))
   *         .build();
   *     p3.getStagesOrDefault();  // returns ["lint", "compile", "deploy"]
   * </pre></blockquote>
   *
   * @return the user-defined stages if present and non-empty, otherwise the
   *     default stages {@code ["build", "test", "docs"]}
   * @implNote The default stages represent common phases in software development:
   *     build (compilation), test (verification), and docs (documentation generation).
   *     These defaults align with industry-standard CI/CD practices.
   * @see #stages
   */
  public List<String> getStagesOrDefault() {
    return (stages != null && !stages.isEmpty())
        ? stages
        : List.of("build", "test", "docs");
  }

  /**
   * Returns an unmodifiable view of the stage names.
   *
   * @return an unmodifiable list of stage names, or {@code null} if not defined
   */
  public List<String> getStages() {
    return stages == null ? null : Collections.unmodifiableList(stages);
  }

  /**
   * Returns an unmodifiable view of all jobs in the pipeline.
   *
   * @return an unmodifiable map of job name to Job object
   */
  public Map<String, Job> getJobs() {
    return jobs == null ? null : Collections.unmodifiableMap(jobs);
  }

  /**
   * Sets the pipeline stages with a defensive copy.
   *
   * @param stages the list of stage names, or {@code null}
   */
  public void setStages(List<String> stages) {
    this.stages = stages == null ? null : new ArrayList<>(stages);
  }

  /**
   * Sets the pipeline jobs with a defensive copy.
   *
   * @param jobs the map of job name to Job object
   */
  public void setJobs(Map<String, Job> jobs) {
    this.jobs = jobs == null ? null : new HashMap<>(jobs);
  }

  /**
   * Returns a copy of the pipeline metadata.
   *
   * @return a copy of the pipeline metadata
   */
  public PipelineMetadata getPipeline() {
    if (pipeline == null) {
      return null;
    }
    return PipelineMetadata.builder()
        .name(pipeline.getName())
        .description(pipeline.getDescription())
        .build();
  }

  /**
   * Sets the pipeline metadata with a defensive copy.
   *
   * @param pipeline the pipeline metadata
   */
  public void setPipeline(PipelineMetadata pipeline) {
    if (pipeline == null) {
      this.pipeline = null;
      return;
    }
    this.pipeline = PipelineMetadata.builder()
        .name(pipeline.getName())
        .description(pipeline.getDescription())
        .build();
  }

}
