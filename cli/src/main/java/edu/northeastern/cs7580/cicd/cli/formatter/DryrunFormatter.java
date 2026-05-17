package edu.northeastern.cs7580.cicd.cli.formatter;

import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;

/**
 * Formats a dry-run execution plan into a readable YAML-style string.
 *
 * <p>This formatter is responsible only for presentation. It converts a validated
 * {@link ExecutionPlan} plus the corresponding {@link Pipeline} model into text that
 * resembles YAML for human inspection.
 *
 * <p>The output is intended for CLI display and is not required to be a valid pipeline
 * configuration file.
 */
public interface DryrunFormatter {

  /**
   * Formats the given execution plan using job details from the pipeline model.
   *
   * @param pipeline validated pipeline model used to look up job configuration
   * @param plan execution plan containing stage and job ordering
   * @return YAML-style formatted output
   * @throws NullPointerException if any argument is {@code null}
   */
  String format(Pipeline pipeline, ExecutionPlan plan);
}

