package edu.northeastern.cs7580.cicd.cli.formatter;

import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link DryrunFormatter} for rendering a dry-run
 * execution plan as a human-readable, YAML-style preview.
 *
 * <p>This formatter is responsible only for presentation. It converts a
 * validated {@link ExecutionPlan} into a textual representation that reflects
 * stage and job execution order, without executing any pipeline logic.
 *
 * <p>The formatted output follows a consistent hierarchical structure:
 * <ul>
 *   <li>Stages are rendered as top-level keys.</li>
 *   <li>Jobs are nested under their corresponding stages.</li>
 *   <li>Job properties such as {@code image}, {@code script}, and {@code needs}
 *       are rendered using fixed indentation levels.</li>
 * </ul>
 *
 * <p>The execution order of stages and jobs strictly follows the order defined
 * in the provided {@link ExecutionPlan}. Missing or invalid elements (such as
 * null stages, jobs, or blank names) are safely skipped.
 *
 * <p>The generated output is intended for CLI display and inspection only and
 * is not required to be a valid pipeline configuration file.
 *
 * @implNote This formatter is stateless and thread-safe. All formatting state
 *            is confined to local method scope.
 */
public class DefaultDryrunFormatter implements DryrunFormatter {

  /**
   * Indentation for a job under a stage (4 spaces).
   */
  private static final String INDENT_JOB = "    ";

  /**
   * Indentation for job properties (8 spaces).
   */
  private static final String INDENT_PROP = "        ";

  /**
   * Indentation for list items under a property (12 spaces).
   */
  private static final String INDENT_LIST_ITEM = "            ";

  /**
   * Formats the given execution plan into a YAML-style string.
   *
   * <p>Stages are printed in the order provided by the execution plan. Jobs are printed in the
   * resolved execution order within each stage. Job properties are emitted only if present.
   *
   * @param pipeline validated pipeline model (included for interface compatibility)
   * @param plan     execution plan containing ordered stages and jobs
   * @return YAML-style formatted dry-run output
   * @throws NullPointerException if {@code pipeline} or {@code plan} is {@code null}
   */
  @Override
  public String format(Pipeline pipeline, ExecutionPlan plan) {
    Objects.requireNonNull(pipeline, "pipeline");
    Objects.requireNonNull(plan, "plan");

    StringBuilder out = new StringBuilder();

    List<StageExecution> stages = plan.getStages();
    if (stages == null || stages.isEmpty()) {
      return "";
    }

    for (StageExecution stageExecution : stages) {
      if (stageExecution == null) {
        continue;
      }

      String stageName = stageExecution.getStageName();
      if (stageName == null || stageName.isBlank()) {
        continue;
      }

      out.append(stageName).append(":\n");

      List<Job> jobs = stageExecution.getJobs();
      if (jobs == null || jobs.isEmpty()) {
        continue;
      }

      for (Job job : jobs) {
        if (job == null) {
          continue;
        }

        String jobName = job.getName();
        if (jobName == null || jobName.isBlank()) {
          continue;
        }

        out.append(INDENT_JOB).append(jobName).append(":\n");

        appendImage(out, job);
        appendScript(out, job);
        appendNeeds(out, job);
        appendFailures(out, job);
      }
    }

    return out.toString();
  }

  /**
   * Appends the {@code image} property if present.
   *
   * @param out output builder
   * @param job job to render
   */
  private static void appendImage(StringBuilder out, Job job) {
    String image = job.getImage();
    if (image == null || image.isBlank()) {
      return;
    }
    out.append(INDENT_PROP).append("image: ").append(image).append("\n");
  }

  /**
   * Appends the {@code script} property as a YAML list if present.
   *
   * @param out output builder
   * @param job job to render
   */
  private static void appendScript(StringBuilder out, Job job) {
    List<String> commands = job.getScriptCommands();
    if (commands == null || commands.isEmpty()) {
      return;
    }

    out.append(INDENT_PROP).append("script:\n");
    for (String command : commands) {
      if (command == null) {
        continue;
      }
      out.append(INDENT_LIST_ITEM).append("- ").append(command).append("\n");
    }
  }

  /**
   * Appends the {@code failures} property for every job.
   *
   * <p>Always emitted (both {@code true} and {@code false}) so that report
   * consumers never need to infer a default.
   *
   * @param out output builder
   * @param job job to render
   */
  private static void appendFailures(StringBuilder out, Job job) {
    out.append(INDENT_PROP).append("failures: ").append(job.isFailures()).append("\n");
  }

  /**
   * Appends the {@code needs} property as a YAML list if present.
   *
   * @param out output builder
   * @param job job to render
   */
  private static void appendNeeds(StringBuilder out, Job job) {
    List<String> needs = job.getNeeds();
    if (needs == null || needs.isEmpty()) {
      return;
    }

    out.append(INDENT_PROP).append("needs:\n");
    for (String need : needs) {
      if (need == null || need.isBlank()) {
        continue;
      }
      out.append(INDENT_LIST_ITEM).append("- ").append(need).append("\n");
    }
  }
}
