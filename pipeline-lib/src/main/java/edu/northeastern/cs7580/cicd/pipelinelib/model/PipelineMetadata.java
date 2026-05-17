package edu.northeastern.cs7580.cicd.pipelinelib.model;

import lombok.Builder;
import lombok.Data;

/**
 * The {@code PipelineMetadata} class represents identifying information for a
 * CI/CD pipeline. All pipeline metadata in configuration files are implemented
 * as instances of this class.
 *
 * <p>Pipeline metadata provides essential identifying information including
 * the pipeline's unique name and an optional human-readable description.
 * The name serves as the primary identifier for the pipeline within the
 * repository and must be unique across all pipeline configurations.
 *
 * <p>Here are some examples of how pipeline metadata can be used:
 * <blockquote><pre>
 *     PipelineMetadata metadata = PipelineMetadata.builder()
 *         .name("default")
 *         .description("Default build pipeline for nightly builds")
 *         .build();
 *
 *     PipelineMetadata release = PipelineMetadata.builder()
 *         .name("release")
 *         .description("Production release pipeline")
 *         .build();
 * </pre></blockquote>
 *
 * <p>Pipeline metadata is typically defined in the {@code pipeline} section
 * of YAML configuration files:
 * <blockquote><pre>
 * pipeline:
 *   name: default
 *   description: Default build pipeline
 * </pre></blockquote>
 *
 * <p>The {@link #name} field is required and must be a non-null, non-empty
 * string. The {@link #description} field is optional and may be {@code null}
 * or empty.
 *
 * @implNote Pipeline names are used for lookup operations and must be unique
 *     within a repository. Two pipelines with the same name in different
 *     configuration files will result in a validation error.
 * @see Pipeline
 */
@Data
@Builder
public class PipelineMetadata {

  /**
   * The unique identifier for this pipeline.
   *
   * <p>Must be unique across all pipeline configurations in the repository.
   * The name is used to reference this pipeline in CLI commands and to
   * identify pipeline runs in execution history. Pipeline names should be
   * descriptive and follow naming conventions (e.g., {@code "default"},
   * {@code "nightly"}, {@code "release"}).
   *
   * <p>This field is required and must not be {@code null} or empty.
   * During validation, the system ensures that no two pipelines in the
   * repository share the same name. Related field is {@link #description}.
   *
   * @see Pipeline
   * @see #description
   */
  private String name;

  /**
   * A human-readable description of this pipeline's purpose.
   *
   * <p>Provides additional context about what this pipeline does, when it
   * should be used, or any special considerations. The description appears
   * in logs, reports, and documentation. This field is optional and may be
   * {@code null}, empty, or omitted entirely.
   *
   * <p>Examples of good descriptions:
   * <blockquote><pre>
   *     "Default build pipeline for nightly builds"
   *     "Production release pipeline with full test suite"
   *     "Quick validation pipeline for pull requests"
   * </pre></blockquote>
   *
   * @see #name
   */
  private String description;

}
