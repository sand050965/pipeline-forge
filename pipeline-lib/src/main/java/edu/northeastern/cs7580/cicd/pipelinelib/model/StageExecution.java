package edu.northeastern.cs7580.cicd.pipelinelib.model;

import edu.northeastern.cs7580.cicd.pipelinelib.internal.builder.ExecutionPlanBuilder;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * The {@code StageExecution} class represents a single stage and its jobs
 * in a pipeline execution plan. All stage representations in execution plans
 * are instances of this class.
 *
 * <p>A stage execution groups together all jobs that belong to a specific
 * pipeline stage, with jobs ordered according to their dependencies. Jobs
 * with no dependencies or whose dependencies are satisfied execute before
 * jobs that depend on them.
 *
 * <p>Here are some examples of how stage executions can be used:
 * <blockquote><pre>
 *     List&lt;Job&gt; testJobs = Arrays.asList(unitTestJob, integrationTestJob);
 *
 *     StageExecution testStage = StageExecution.builder()
 *         .stageName("test")
 *         .jobs(testJobs)
 *         .build();
 *
 *     System.out.println("Stage: " + testStage.getStageName());
 *     System.out.println("Jobs: " + testStage.getJobs().size());
 * </pre></blockquote>
 *
 * <p>The class {@code StageExecution} maintains both the stage name and the
 * ordered list of jobs for that stage. The job order is determined by
 * topological sorting of job dependencies, ensuring that jobs execute only
 * after their dependencies have completed.
 *
 * <p>Stage executions are typically created by {@link ExecutionPlanBuilder}
 * when constructing an {@link ExecutionPlan} from a validated {@link Pipeline}.
 * The builder ensures that jobs are properly ordered within each stage according
 * to their {@code needs} declarations.
 *
 * <p>Unless otherwise noted, passing a {@code null} value for required
 * fields will result in a {@link NullPointerException}.
 *
 * @implNote This class is immutable and uses Lombok's {@code @Builder} pattern
 *     for construction. All fields are final and the jobs list should be
 *     treated as read-only to maintain immutability guarantees.
 * @see ExecutionPlan
 * @see ExecutionPlanBuilder
 * @see Job
 * @see Pipeline
 */
@Data
@Builder
public class StageExecution {

  /**
   * The name of this stage.
   *
   * <p>Stage names correspond to the stages defined in the pipeline
   * configuration and are used to group related jobs together in the
   * execution sequence.
   */
  private final String stageName;

  /**
   * The ordered list of jobs in this stage.
   *
   * <p>Jobs are ordered using topological sorting based on their
   * {@code needs} dependencies. Jobs with no dependencies or whose
   * dependencies are all satisfied appear before jobs that depend
   * on them. This ordering ensures that dependency constraints are
   * respected during pipeline execution.
   *
   * @see Job
   * @see Job#getNeeds()
   */
  private final List<Job> jobs;

  /**
   * Returns an unmodifiable view of the jobs in this stage.
   *
   * <p>Jobs are in topological order based on dependencies.
   *
   * @return an unmodifiable list of jobs in dependency order
   */
  public List<Job> getJobs() {
    return Collections.unmodifiableList(jobs);
  }

}
