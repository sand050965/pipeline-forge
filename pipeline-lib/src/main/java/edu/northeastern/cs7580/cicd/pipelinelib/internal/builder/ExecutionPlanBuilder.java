package edu.northeastern.cs7580.cicd.pipelinelib.internal.builder;

import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import edu.northeastern.cs7580.cicd.pipelinelib.model.StageExecution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@code ExecutionPlanBuilder} class constructs execution plans from
 * validated pipeline configurations. All execution plan generation operations
 * are performed through instances of this class.
 *
 * <p>This builder transforms a validated {@link Pipeline} into an
 * {@link ExecutionPlan} by organizing stages and ordering jobs within each
 * stage according to their dependencies. Jobs are topologically sorted to
 * ensure that dependencies declared through the {@code needs} keyword are
 * satisfied before dependent jobs execute.
 *
 * <p>Here are some examples of how this builder can be used:
 * <blockquote><pre>
 *     ExecutionPlanBuilder builder = new ExecutionPlanBuilder();
 *
 *     Pipeline pipeline = // ... validated pipeline
 *     ExecutionPlan plan = builder.build(pipeline);
 *
 *     for (StageExecution stage : plan.getStages()) {
 *         System.out.println("Stage: " + stage.getStageName());
 *     }
 * </pre></blockquote>
 *
 * <p>The class {@link ExecutionPlanBuilder} performs the following operations
 * when building an execution plan:
 * <ol>
 *   <li>Extracts the ordered list of stages from the pipeline</li>
 *   <li>Groups jobs by their stage assignments</li>
 *   <li>Orders jobs within each stage using topological sorting</li>
 *   <li>Creates {@link StageExecution} objects for each stage</li>
 *   <li>Assembles the complete {@link ExecutionPlan}</li>
 * </ol>
 *
 * <p>Job ordering within stages respects the dependency graph formed by
 * {@code needs} declarations. The builder assumes that circular dependencies
 * have already been detected and rejected during validation, guaranteeing
 * that a valid topological ordering exists.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to methods in
 * this class will cause a {@link NullPointerException} to be thrown.
 *
 * @implNote This class is stateless and thread-safe. All build operations
 *     are performed on the provided input without maintaining any internal
 *     state between invocations. The topological sort implementation uses
 *     an iterative algorithm that repeatedly selects jobs whose dependencies
 *     are satisfied, ensuring O(V + E) complexity where V is the number of
 *     jobs and E is the number of dependency edges.
 * @see ExecutionPlan
 * @see StageExecution
 * @see Pipeline
 * @see Job
 */
public class ExecutionPlanBuilder {

  /**
   * Builds an execution plan from a validated pipeline.
   *
   * <p>This method transforms a validated {@link Pipeline} into an
   * {@link ExecutionPlan} by organizing stages in their defined order
   * and sorting jobs within each stage according to their dependencies.
   * The resulting execution plan provides a clear preview of the pipeline's
   * execution flow.
   *
   * <p>The method performs the following operations:
   * <ol>
   *   <li>Retrieves the ordered list of stages from the pipeline</li>
   *   <li>For each stage, collects all jobs assigned to that stage</li>
   *   <li>Orders jobs within each stage using {@link #orderJobsByDependencies(List)}</li>
   *   <li>Creates a {@link StageExecution} for each non-empty stage</li>
   *   <li>Assembles the complete {@link ExecutionPlan}</li>
   * </ol>
   *
   * <p>Jobs are filtered by their {@code stage} field to determine stage
   * membership. Only jobs explicitly assigned to each stage are included
   * in that stage's execution. Empty stages (stages with no jobs) are
   * omitted from the execution plan.
   *
   * <p>Example usage:
   * <blockquote><pre>
   *     Pipeline pipeline = validationService.validateAndParse(configFile);
   *     ExecutionPlan plan = builder.build(pipeline);
   *     System.out.println("Generated plan with " + plan.getStages().size() + " stages");
   * </pre></blockquote>
   *
   * @param pipeline the validated pipeline to build an execution plan from
   * @return an {@code ExecutionPlan} containing stages and jobs in execution order
   * @throws NullPointerException if {@code pipeline} is null
   * @see #orderJobsByDependencies(List)
   * @see Pipeline#getStagesOrDefault()
   */
  public ExecutionPlan build(Pipeline pipeline) {
    List<StageExecution> stageExecutions = new ArrayList<>();
    List<String> stages = pipeline.getStagesOrDefault();

    for (String stageName : stages) {
      List<Job> jobsInStage = pipeline.getJobs().values().stream()
          .filter(job -> stageName.equals(job.getStage()))
          .collect(Collectors.toList());

      List<Job> orderedJobs = orderJobsByDependencies(jobsInStage);

      if (!jobsInStage.isEmpty()) {
        stageExecutions.add(StageExecution.builder()
            .stageName(stageName)
            .jobs(orderedJobs)
            .build());
      }
    }

    Map<String, List<String>> jobDependencies = buildJobDependencies(pipeline.getJobs());

    return ExecutionPlan.builder()
        .stages(stageExecutions)
        .jobDependencies(jobDependencies)
        .build();
  }


  /**
   * Orders jobs using topological sort based on their {@code needs} dependencies.
   *
   * <p>This method implements an iterative topological sorting algorithm that
   * repeatedly identifies jobs whose dependencies are all satisfied and adds
   * them to the ordered result. Jobs with no dependencies are processed first,
   * followed by jobs that depend on them, ensuring that dependency constraints
   * are respected in the final ordering.
   *
   * <p>The algorithm works as follows:
   * <ol>
   *   <li>Initialize tracking sets for completed and remaining jobs</li>
   *   <li>While jobs remain to be ordered:</li>
   *   <ul>
   *     <li>Find all jobs whose dependencies are satisfied (in completed set)</li>
   *     <li>Add these "ready" jobs to the ordered result</li>
   *     <li>Mark these jobs as completed</li>
   *     <li>Remove them from the remaining set</li>
   *   </ul>
   *   <li>Return the ordered list of jobs</li>
   * </ol>
   *
   * <p>This method assumes that the input jobs form a valid directed acyclic
   * graph (DAG) with no circular dependencies. This invariant is guaranteed
   * by pipeline validation, which detects and rejects circular dependencies
   * before execution plan generation.
   *
   * <p>Example usage:
   * <blockquote><pre>
   *     List&lt;Job&gt; jobs = Arrays.asList(job1, job2, job3);
   *     List&lt;Job&gt; ordered = orderJobsByDependencies(jobs);
   *     // ordered contains jobs in dependency order
   * </pre></blockquote>
   *
   * @param jobs the list of jobs to order by dependencies
   * @return a new list containing the same jobs in dependency order, where
   *     jobs with no dependencies or satisfied dependencies appear before
   *     jobs that depend on them
   * @throws AssertionError if no ready jobs are found during iteration,
   *     indicating a circular dependency that should have been caught by
   *     validation (only when assertions are enabled)
   * @see Job#getNeeds()
   * @see #build(Pipeline)
   */
  private List<Job> orderJobsByDependencies(List<Job> jobs) {
    List<Job> orderedJobs = new ArrayList<>();
    Set<String> completed = new HashSet<>();
    Set<String> remaining = jobs.stream()
        .map(Job::getName)
        .collect(Collectors.toSet());

    while (!remaining.isEmpty()) {
      // Find jobs whose dependencies are all completed (or have no dependencies)
      List<Job> readyJobs = jobs.stream()
          .filter(job -> remaining.contains(job.getName()))
          .filter(job -> {
            List<String> needs = job.getNeeds();
            return needs == null || needs.isEmpty() || completed.containsAll(needs);
          })
          .collect(Collectors.toList());

      // Should always find at least one ready job (guaranteed by validation)
      assert !readyJobs.isEmpty() : "No ready jobs found - circular dependency not caught!";

      // Add ready jobs to ordered list
      for (Job job : readyJobs) {
        orderedJobs.add(job);
        completed.add(job.getName());
        remaining.remove(job.getName());
      }
    }
    return orderedJobs;
  }

  /**
   * Builds a dependency map for all jobs in the pipeline.
   *
   * <p>This method creates a flat map from job name to its list of dependencies,
   * extracted from each job's {@code needs} field. Jobs without dependencies
   * are included with empty lists to provide a complete view of the dependency
   * graph.
   *
   * <p>The dependency map is used for parallel execution scheduling in future
   * sprints. For sequential execution, jobs are executed in the order determined
   * by topological sorting within each stage.
   *
   * <p>Example transformation:
   * <blockquote><pre>
   * Pipeline with jobs:
   *   compile: needs=[]
   *   test: needs=["compile"]
   *   deploy: needs=["test"]
   *
   * Produces map:
   *   {
   *     "compile": [],
   *     "test": ["compile"],
   *     "deploy": ["test"]
   *   }
   * </pre></blockquote>
   *
   * @param jobs the map of all jobs in the pipeline (job name → Job object)
   * @return a map from job name to list of dependency job names. Jobs with
   *     no dependencies map to empty lists. The returned lists are mutable
   *     copies to prevent accidental modification of job data.
   * @see Job#getNeeds()
   * @see ExecutionPlan#getJobDependencies()
   */
  private Map<String, List<String>> buildJobDependencies(Map<String, Job> jobs) {
    Map<String, List<String>> dependencies = new HashMap<>();

    for (Job job : jobs.values()) {
      String jobName = job.getName();
      List<String> needs = job.getNeeds();

      // Store empty list for jobs with no dependencies, or a copy of the needs list
      if (needs == null || needs.isEmpty()) {
        dependencies.put(jobName, new ArrayList<>());
      } else {
        dependencies.put(jobName, new ArrayList<>(needs));
      }
    }

    return dependencies;
  }
}
