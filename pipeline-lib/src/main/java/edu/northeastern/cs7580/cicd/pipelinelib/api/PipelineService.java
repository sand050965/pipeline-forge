package edu.northeastern.cs7580.cicd.pipelinelib.api;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import java.nio.file.Path;

/**
 * Public API for pipeline validation and execution planning.
 *
 * <p>The {@code PipelineService} interface provides the primary entry point for
 * validating CI/CD pipeline configurations and generating execution plans. All
 * pipeline validation and planning operations are performed through instances
 * obtained from {@link PipelineServiceFactory}.
 *
 * <p>This service handles comprehensive validation including:
 * <ul>
 *   <li>YAML syntax and structure validation</li>
 *   <li>Required field presence (pipeline name, stages, jobs)</li>
 *   <li>Job dependency analysis and circular dependency detection</li>
 *   <li>Stage and job name uniqueness validation</li>
 *   <li>Type correctness for all configuration fields</li>
 *   <li>Cross-stage dependency validation</li>
 * </ul>
 *
 * <p>Validation errors include precise file position information in the format:
 * {@code filename:line:column: ERROR, message}, enabling developers to quickly
 * locate and fix configuration issues.
 *
 * <p><b>Thread Safety:</b> Implementations of this interface are thread-safe
 * for concurrent read operations (validation and plan generation). However,
 * individual {@link ExecutionPlan} instances should not be shared across threads
 * during modification.
 *
 * <p><b>Usage Example - Single File Validation:</b>
 * <blockquote><pre>
 *     PipelineService service = PipelineServiceFactory.create();
 *
 *     try {
 *         Pipeline pipeline = service.validatePipeline(
 *             Path.of(".pipelines/default.yaml")
 *         );
 *         System.out.println("Pipeline '" + pipeline.getPipeline().getName()
 *             + "' is valid");
 *     } catch (ValidationException e) {
 *         System.err.println("Validation failed:");
 *         System.err.println(e.getMessage());
 *     }
 * </pre></blockquote>
 *
 * <p><b>Usage Example - Directory Validation:</b>
 * <blockquote><pre>
 *     PipelineService service = PipelineServiceFactory.create();
 *
 *     try {
 *         service.validateDirectory(Path.of(".pipelines"));
 *         System.out.println("All pipelines are valid");
 *     } catch (ValidationException e) {
 *         System.err.println("Validation errors:");
 *         System.err.println(e.getMessage());
 *     }
 * </pre></blockquote>
 *
 * <p><b>Usage Example - Execution Plan Generation:</b>
 * <blockquote><pre>
 *     PipelineService service = PipelineServiceFactory.create();
 *     ExecutionPlan plan = service.createExecutionPlan(
 *         Path.of(".pipelines/build.yaml")
 *     );
 *
 *     // Sequential execution
 *     List&lt;Job&gt; allJobs = plan.getStages().stream()
 *         .flatMap(stage -&gt; stage.getJobs().stream())
 *         .collect(Collectors.toList());
 *
 *     for (Job job : allJobs) {
 *         System.out.println("Executing: " + job.getName());
 *         executeJob(job);
 *     }
 * </pre></blockquote>
 *
 * <p><b>Usage Example - Parallel Execution with Dependencies:</b>
 * <blockquote><pre>
 *     ExecutionPlan plan = service.createExecutionPlan(configFile);
 *     Map&lt;String, List&lt;String&gt;&gt; dependencies = plan.getJobDependencies();
 *     Set&lt;String&gt; completed = new HashSet&lt;&gt;();
 *
 *     for (StageExecution stage : plan.getStages()) {
 *         for (Job job : stage.getJobs()) {
 *             List&lt;String&gt; jobDeps = dependencies.get(job.getName());
 *
 *             // Wait until all dependencies are completed
 *             while (!completed.containsAll(jobDeps)) {
 *                 Thread.sleep(100);
 *             }
 *
 *             // Execute job asynchronously
 *             executor.submit(() -&gt; {
 *                 executeJob(job);
 *                 completed.add(job.getName());
 *             });
 *         }
 *     }
 * </pre></blockquote>
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to methods in
 * this interface will cause a {@link NullPointerException} to be thrown by
 * the implementing class.
 *
 * @since 1.0.0
 * @see PipelineServiceFactory
 * @see Pipeline
 * @see ExecutionPlan
 * @see ValidationException
 */
public interface PipelineService {

  /**
   * Validates a pipeline configuration file.
   *
   * <p>This method performs comprehensive validation of a single pipeline
   * configuration file, including YAML syntax checking, structure validation,
   * stage and job validation, and dependency analysis. Unlike fail-fast
   * validation, this method collects multiple errors before throwing, allowing
   * developers to see all issues at once.
   *
   * <p>The validation process executes in the following order:
   * <ol>
   *   <li>Parse YAML file with position tracking</li>
   *   <li>Validate basic YAML structure (pipeline section, required fields)</li>
   *   <li>Build internal Pipeline model from YAML data</li>
   *   <li>Validate stage rules (existence, uniqueness, non-empty)</li>
   *   <li>Validate job rules (required fields, valid stage references)</li>
   *   <li>Validate dependency rules (needs references, circular dependencies)</li>
   * </ol>
   *
   * <p>Validation errors include precise line and column information in the
   * format: {@code filename:line:column: ERROR, message}. For example:
   * <blockquote><pre>
   * .pipelines/build.yaml:15:3: ERROR, Duplicate job name 'compile'
   * .pipelines/build.yaml:22:5: ERROR, Job 'test' references undefined job 'build'
   * </pre></blockquote>
   *
   * <p>Example validation scenarios:
   * <blockquote><pre>
   *     PipelineService service = PipelineServiceFactory.create();
   *
   *     // Valid pipeline
   *     Pipeline pipeline = service.validatePipeline(
   *         Path.of(".pipelines/valid.yaml")
   *     );
   *
   *     // Invalid pipeline - throws ValidationException
   *     try {
   *         service.validatePipeline(Path.of(".pipelines/invalid.yaml"));
   *     } catch (ValidationException e) {
   *         // e.getMessage() contains all validation errors with line numbers
   *         System.err.println(e.getMessage());
   *     }
   * </pre></blockquote>
   *
   * @param configFile the path to the pipeline configuration file to validate.
   *     Must be a readable YAML file with .yaml or .yml extension.
   * @return a fully validated {@code Pipeline} object representing the
   *     configuration. All fields are guaranteed to be structurally valid
   *     and semantically correct.
   * @throws ValidationException if the pipeline configuration is invalid,
   *     containing all collected errors with file path, line number, column
   *     number, and descriptive messages. The exception message contains a
   *     newline-separated list of all validation errors.
   * @throws NullPointerException if {@code configFile} is null
   * @see Pipeline
   * @see ValidationException
   * @see #validateDirectory(Path)
   * @see #createExecutionPlan(Path)
   */
  Pipeline validatePipeline(Path configFile) throws ValidationException;

  /**
   * Validates all pipeline configuration files in a directory.
   *
   * <p>This method scans the specified directory for YAML files (.yaml and .yml
   * extensions), validates each one individually, and ensures that pipeline names
   * are unique across all files in the directory. This is typically used to validate
   * the {@code .pipelines} directory containing all pipeline configurations for a
   * repository.
   *
   * <p>Unlike single-file validation, this method does not fail fast. Instead, it:
   * <ol>
   *   <li>Scans the directory for YAML files (non-recursive)</li>
   *   <li>Validates each file independently</li>
   *   <li>Collects all validation errors across all files</li>
   *   <li>Checks for duplicate pipeline names across files</li>
   *   <li>Throws a single {@link ValidationException} containing all errors</li>
   * </ol>
   *
   * <p>Only files directly in the specified directory are checked; subdirectories
   * are not recursively scanned. This aligns with the requirement that all pipeline
   * configurations reside directly in the {@code .pipelines} folder.
   *
   * <p>Pipeline name uniqueness is enforced across all files in the directory.
   * For example, if {@code build.yaml} and {@code deploy.yaml} both define a
   * pipeline named "default", a validation error is reported.
   *
   * <p>Example usage:
   * <blockquote><pre>
   *     PipelineService service = PipelineServiceFactory.create();
   *
   *     try {
   *         service.validateDirectory(Path.of(".pipelines"));
   *         System.out.println("All pipelines are valid and names are unique");
   *     } catch (ValidationException e) {
   *         // e.getMessage() contains all errors from all files
   *         System.err.println("Validation errors found:");
   *         System.err.println(e.getMessage());
   *     }
   * </pre></blockquote>
   *
   * <p>Example error output when multiple files have issues:
   * <blockquote><pre>
   * .pipelines/build.yaml:5:3: ERROR, Missing 'stage' field in job 'compile'
   * .pipelines/test.yaml:10:1: ERROR, Circular dependency detected: test -&gt; lint -&gt; test
   * .pipelines/deploy.yaml:0:0: ERROR, Duplicate pipeline name 'default'
   * </pre></blockquote>
   *
   * @param directory the directory containing pipeline configuration files.
   *     Must be a readable directory. Only .yaml and .yml files directly in
   *     this directory (not subdirectories) are validated.
   * @throws ValidationException if no YAML files are found in the directory,
   *     if any pipeline configuration is invalid, or if duplicate pipeline
   *     names are detected across files. The exception message contains a
   *     newline-separated list of all validation errors from all files.
   * @throws NullPointerException if {@code directory} is null
   * @see #validatePipeline(Path)
   */
  void validateDirectory(Path directory) throws ValidationException;

  /**
   * Creates an execution plan for a pipeline configuration file.
   *
   * <p>This method validates the pipeline configuration and generates an execution
   * plan that can be used by execution services to run the pipeline. The execution
   * plan contains stages with jobs in topological (dependency) order and a complete
   * dependency map for parallel execution scheduling.
   *
   * <p>The execution plan respects job dependencies declared through the {@code needs}
   * keyword and ensures jobs execute in a valid order where all dependencies are
   * satisfied before dependent jobs run. The plan includes:
   * <ul>
   *   <li>Stages with jobs in topologically sorted order</li>
   *   <li>Job dependency map (job name → list of dependency job names)</li>
   *   <li>Complete validation of all pipeline constraints</li>
   * </ul>
   *
   * <p><b>Sequential Execution Pattern:</b>
   *
   * <p>For sequential execution where jobs run one at a time in dependency order,
   * flatten the stages to get all jobs in topological order:
   * <blockquote><pre>
   *     PipelineService service = PipelineServiceFactory.create();
   *     ExecutionPlan plan = service.createExecutionPlan(
   *         Path.of(".pipelines/build.yaml")
   *     );
   *
   *     // Get all jobs in dependency order
   *     List&lt;Job&gt; allJobs = plan.getStages().stream()
   *         .flatMap(stage -&gt; stage.getJobs().stream())
   *         .collect(Collectors.toList());
   *
   *     // Execute jobs sequentially
   *     for (Job job : allJobs) {
   *         System.out.println("Executing: " + job.getName());
   *         JobResult result = executeJob(job);
   *         if (result.failed()) {
   *             System.err.println("Job failed, stopping pipeline");
   *             break;
   *         }
   *     }
   * </pre></blockquote>
   *
   * <p><b>Parallel Execution Pattern:</b>
   *
   * <p>For parallel execution where independent jobs can run concurrently, use
   * the dependency map to determine when jobs are ready:
   * <blockquote><pre>
   *     ExecutionPlan plan = service.createExecutionPlan(configFile);
   *     Map&lt;String, List&lt;String&gt;&gt; dependencies = plan.getJobDependencies();
   *     Set&lt;String&gt; completed = new HashSet&lt;&gt;();
   *     ExecutorService executor = Executors.newFixedThreadPool(4);
   *
   *     for (StageExecution stage : plan.getStages()) {
   *         for (Job job : stage.getJobs()) {
   *             List&lt;String&gt; jobDeps = dependencies.get(job.getName());
   *
   *             // Schedule job when all dependencies are satisfied
   *             if (completed.containsAll(jobDeps)) {
   *                 executor.submit(() -&gt; {
   *                     executeJob(job);
   *                     completed.add(job.getName());
   *                 });
   *             }
   *         }
   *     }
   * </pre></blockquote>
   *
   * <p><b>Accessing Dependency Information:</b>
   * <blockquote><pre>
   *     ExecutionPlan plan = service.createExecutionPlan(configFile);
   *     Map&lt;String, List&lt;String&gt;&gt; deps = plan.getJobDependencies();
   *
   *     // Check what each job depends on
   *     for (Map.Entry&lt;String, List&lt;String&gt;&gt; entry : deps.entrySet()) {
   *         String jobName = entry.getKey();
   *         List&lt;String&gt; dependencies = entry.getValue();
   *
   *         if (dependencies.isEmpty()) {
   *             System.out.println(jobName + " has no dependencies");
   *         } else {
   *             System.out.println(jobName + " depends on: "
   *                 + String.join(", ", dependencies));
   *         }
   *     }
   * </pre></blockquote>
   *
   * @param configFile the path to the pipeline configuration file. Must be a
   *     readable YAML file with valid pipeline configuration.
   * @return an {@code ExecutionPlan} containing stages with topologically sorted
   *     jobs and a complete job dependency map. The plan is ready for immediate
   *     use by execution services. Jobs within each stage are ordered to respect
   *     dependencies, and the dependency map provides the information needed for
   *     parallel execution scheduling.
   * @throws ValidationException if the pipeline configuration is invalid. This
   *     includes any validation errors that would be caught by
   *     {@link #validatePipeline(Path)}, such as missing required fields,
   *     circular dependencies, invalid stage references, or malformed YAML.
   * @throws NullPointerException if {@code configFile} is null
   * @see ExecutionPlan
   * @see ExecutionPlan#getStages()
   * @see ExecutionPlan#getJobDependencies()
   * @see #validatePipeline(Path)
   */
  ExecutionPlan createExecutionPlan(Path configFile) throws ValidationException;

}

