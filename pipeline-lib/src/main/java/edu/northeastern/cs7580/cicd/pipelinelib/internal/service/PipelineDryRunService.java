package edu.northeastern.cs7580.cicd.pipelinelib.internal.service;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.builder.ExecutionPlanBuilder;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

/**
 * The {@code PipelineDryRunService} class provides dry-run services for
 * CI/CD pipeline configuration files. All dry-run operations are coordinated
 * through instances of this service.
 *
 * <p>This service orchestrates pipeline validation and execution plan generation
 * to preview how a pipeline would execute without actually running it. It handles
 * YAML validation, dependency resolution, and execution order calculation to
 * provide users with a clear view of their pipeline's execution flow.
 *
 * <p>Here are some examples of how this service can be used:
 * <blockquote><pre>
 *     PipelineDryRunService service = new PipelineDryRunService(
 *         validationService, executionPlanBuilder
 *     );
 *
 *     Path configFile = Path.of(".pipelines/default.yaml");
 *     ExecutionPlan plan = service.buildExecutionPlan(configFile);
 *
 *     for (StageExecution stage : plan.getStages()) {
 *         System.out.println("Stage: " + stage.getStageName());
 *         for (Job job : stage.getJobs()) {
 *             System.out.println("  Job: " + job.getName());
 *         }
 *     }
 * </pre></blockquote>
 *
 * <p>The class {@code PipelineDryRunService} coordinates dry-run operations through
 * two main components: {@link PipelineValidationService} for comprehensive validation
 * and {@link ExecutionPlanBuilder} for dependency resolution and execution order
 * calculation. The service ensures that only valid pipeline configurations proceed
 * to execution plan generation.
 *
 * <p>Execution plans organize pipeline stages and jobs in their correct execution
 * order, respecting job dependencies declared through the {@code needs} keyword.
 * Jobs within each stage are topologically sorted to ensure dependencies are
 * satisfied before dependent jobs execute.
 *
 * <p>Validation failures are reported using IDE-friendly error messages that
 * include file path, line number, column number, and descriptive text. Multiple
 * errors are collected and reported together, improving the developer experience
 * by showing all issues at once rather than requiring iterative fixes.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to methods in
 * this class will cause a {@link NullPointerException} to be thrown.
 *
 * @implNote This service is stateless and thread-safe. All dry-run operations
 *     are performed on the provided input without maintaining any internal state
 *     between invocations. The service uses dependency injection to receive
 *     component instances, promoting modularity and testability. Execution plans
 *     are generated from validated pipelines only, ensuring structural integrity.
 * @see PipelineValidationService
 * @see ExecutionPlanBuilder
 * @see ExecutionPlan
 * @see StageExecution
 * @see Pipeline
 * @see Job
 * @see ValidationException
 */

@RequiredArgsConstructor
public class PipelineDryRunService {

  /**
   * Service for validating pipeline configuration files.
   *
   * <p>Responsible for comprehensive validation including YAML parsing,
   * structure validation, stage validation, job validation, and dependency
   * validation. This service ensures that only valid pipeline configurations
   * proceed to execution plan generation.
   *
   * @see PipelineValidationService
   * @see #buildExecutionPlan(Path)
   */
  private final PipelineValidationService validationService;

  /**
   * Builder for constructing execution plans from validated pipelines.
   *
   * <p>Responsible for organizing stages and jobs in their correct execution
   * order, respecting job dependencies declared through the {@code needs}
   * keyword. Jobs within each stage are topologically sorted to ensure
   * dependencies are satisfied before dependent jobs execute.
   *
   * @see ExecutionPlanBuilder
   * @see #buildExecutionPlan(Path)
   */
  private final ExecutionPlanBuilder executionPlanBuilder;

  /**
   * Builds an execution plan from a pipeline configuration file.
   *
   * <p>This method validates the pipeline configuration and generates an
   * execution plan showing the order in which stages and jobs would execute.
   * The execution plan organizes jobs within each stage according to their
   * dependencies, ensuring that jobs with {@code needs} declarations execute
   * after their dependencies.
   *
   * <p>The method performs the following operations in order:
   * <ol>
   *   <li>Validates the pipeline configuration file using {@link PipelineValidationService}</li>
   *   <li>Builds an execution plan with topologically sorted jobs using
   *       {@link ExecutionPlanBuilder}</li>
   *   <li>Returns the complete execution plan ready for display or execution</li>
   * </ol>
   *
   * <p>If validation fails at any stage, a {@link ValidationException} is thrown
   * containing all collected errors with precise line and column information.
   * The execution plan is only generated for valid pipeline configurations.
   *
   * <p>Example usage:
   * <blockquote><pre>
   *     Path configFile = Path.of(".pipelines/default.yaml");
   *     ExecutionPlan plan = service.buildExecutionPlan(configFile);
   *     System.out.println("Pipeline has " + plan.getStages().size() + " stages");
   * </pre></blockquote>
   *
   * @param configFile the path to the pipeline configuration file to process
   * @return an {@code ExecutionPlan} containing stages and jobs in execution order
   * @throws ValidationException if the pipeline configuration is invalid, containing
   *     all collected validation errors with file path, line number, column number,
   *     and descriptive messages
   * @see PipelineValidationService#validateAndParse(Path)
   * @see ExecutionPlanBuilder#build(Pipeline)
   * @see ExecutionPlan
   */
  public ExecutionPlan buildExecutionPlan(Path configFile)
       throws ValidationException {
    // 1. Validate and parse (reuse validation service)
    Pipeline pipeline = validationService.validateAndParse(configFile);

    // 2. Build execution plan
    return executionPlanBuilder.build(pipeline);
  }

}
