package edu.northeastern.cs7580.cicd.pipelinelib.internal.validator;

import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.parser.PositionAwareYamlParser.Position;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.ValidationErrorCollector;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Job;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@code NeedValidator} class validates job dependency rules and constraints
 * in CI/CD pipeline configurations. All job dependency validation operations are
 * performed through instances of this validator.
 *
 * <p>This validator ensures that job dependencies ({@code needs} declarations)
 * are correctly specified and reference valid jobs within the same stage.
 * Dependency validation occurs after job validation in the validation pipeline,
 * ensuring that all referenced jobs exist before checking relationships.
 *
 * <p>The class {@code NeedValidator} enforces the following rules:
 * <ul>
 *   <li>If a {@code needs} list is specified, it must not be empty</li>
 *   <li>All jobs referenced in {@code needs} must exist in the pipeline</li>
 *   <li>All jobs referenced in {@code needs} must be in the same stage</li>
 *   <li>Dependencies must not form cycles within a stage</li>
 * </ul>
 *
 * <p>Here are some examples of validation scenarios:
 * <blockquote><pre>
 *     NeedValidator validator = new NeedValidator();
 *     Pipeline pipeline = ...;
 *     Path configFile = Path.of(".pipelines/default.yaml");
 *     PositionAwareYamlParser parser = new PositionAwareYamlParser();
 *     ValidationErrorCollector errorCollector = new ValidationErrorCollector(configFile);
 *
 *     validator.validateNeeds(configFile, pipeline, parser, errorCollector);
 *     errorCollector.throwIfHasErrors();
 * </pre></blockquote>
 *
 * <p>Example valid dependency configuration:
 * <blockquote><pre>
 * unit-tests:
 *   - stage: test
 *   - image: gradle:jdk21
 *   - script: gradle test
 *
 * integration-tests:
 *   - stage: test
 *   - image: gradle:jdk21
 *   - script: gradle integrationTest
 *   - needs: [unit-tests]
 * </pre></blockquote>
 *
 * <p>Example invalid dependency (circular):
 * <blockquote><pre>
 * job1:
 *   - stage: test
 *   - needs: [job2]
 *
 * job2:
 *   - stage: test
 *   - needs: [job1]
 * </pre></blockquote>
 * Would collect error: {@code "test.yaml:14:1: ERROR, Cycle detected in 'needs'
 * requirements: job2 -> job1 -> job2"}
 *
 * <p>Validation errors are collected rather than thrown immediately, allowing
 * multiple dependency issues to be reported in a single validation pass. Errors
 * include precise line and column information from the {@link PositionAwareYamlParser}.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to methods in
 * this class will cause a {@link NullPointerException} to be thrown.
 *
 * @implNote This validator is stateless and can be safely used concurrently.
 *     Jobs without dependencies ({@code needs} is {@code null}) are skipped
 *     during validation. The same-stage constraint ensures that dependencies
 *     only control execution order within a stage, not across stage boundaries.
 *     Cycle detection uses depth-first search with path tracking to identify
 *     and report the complete cycle path when circular dependencies exist.
 * @see JobValidator
 * @see StageValidator
 * @see Pipeline
 * @see Job
 * @see ValidationException
 * @see ValidationErrorCollector
 * @see PositionAwareYamlParser
 */
@Slf4j
public class NeedValidator {

  /**
   * Validates all job dependency rules for the pipeline.
   *
   * <p>This method performs comprehensive validation of {@code needs} declarations
   * across all jobs, ensuring dependencies are correctly specified, reference valid
   * jobs, stay within stage boundaries, and do not create cycles.
   *
   * <p>The validation process executes in the following order:
   * <ol>
   *   <li>For each job with dependencies:
   *     <ul>
   *       <li>Verify needs list is not empty</li>
   *       <li>Verify all referenced jobs exist</li>
   *       <li>Verify all referenced jobs are in the same stage</li>
   *     </ul>
   *   </li>
   *   <li>Detect circular dependencies across all jobs</li>
   * </ol>
   *
   * <p>Jobs without dependencies ({@code needs} is {@code null}) are skipped.
   * An empty {@code needs} list ({@code needs: []}) is treated as an error, as
   * it indicates an incomplete or malformed configuration.
   *
   * <p>All validation errors are collected rather than thrown immediately,
   * allowing multiple dependency issues to be identified and reported together.
   *
   * @param pipeline the pipeline containing all job definitions to validate
   * @param parser the position-aware parser for error location tracking
   * @param errorCollector the collector for validation errors
   * @see #validateNonEmptyNeedsList(String, List, PositionAwareYamlParser,
   *     ValidationErrorCollector)
   * @see #validateReferencedJobsExist(String, Job, List, Map, PositionAwareYamlParser,
   *     ValidationErrorCollector)
   * @see #validateSameStage(String, Job, List, Map, PositionAwareYamlParser,
   *     ValidationErrorCollector)
   * @see #validateNoCycles(Map, PositionAwareYamlParser, ValidationErrorCollector)
   */
  public void validateNeeds(
      Pipeline pipeline,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    Map<String, Job> jobs = pipeline.getJobs();

    for (Map.Entry<String, Job> entry : jobs.entrySet()) {
      String jobName = entry.getKey();
      Job job = entry.getValue();
      List<String> needs = job.getNeeds();

      if (needs == null) {
        continue;
      }

      validateNonEmptyNeedsList(jobName, needs, parser, errorCollector);

      validateReferencedJobsExist(jobName, job, needs, jobs, parser, errorCollector);

      validateSameStage(jobName, job, needs, jobs, parser, errorCollector);
    }

    validateNoCycles(jobs, parser, errorCollector);
  }

  /**
   * Validates that a needs list is not empty.
   *
   * <p>This method checks whether a job's {@code needs} list contains at least
   * one dependency. An empty list indicates the {@code needs} keyword was specified
   * but no dependencies were provided, which is considered a configuration error.
   *
   * <p>Note the distinction between:
   * <ul>
   *   <li>{@code needs: null} or omitted - Valid, means no dependencies</li>
   *   <li>{@code needs: []} - Invalid, empty list triggers this validation</li>
   *   <li>{@code needs: [job1]} - Valid, has dependencies</li>
   * </ul>
   *
   * @param jobName the name of the job being validated
   * @param needs the needs list to check (never null when called)
   * @param parser the position-aware parser for error location tracking
   * @param errorCollector the collector for validation errors
   */
  private void validateNonEmptyNeedsList(
      String jobName,
      List<String> needs,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    if (needs.isEmpty()) {
      Position pos = findNeedsFieldPosition(parser, jobName);
      errorCollector.addError(pos,
          "Job '" + jobName + "' has empty 'needs' list");
    }
  }

  /**
   * Validates that all jobs referenced in needs declarations exist.
   *
   * <p>This method checks each job name in the {@code needs} list against the
   * complete set of jobs defined in the pipeline. Any reference to a non-existent
   * job is reported as an error with the position of the needs entry if available.
   *
   * <p>Example invalid configuration:
   * <blockquote><pre>
   * test-job:
   *   - stage: test
   *   - image: alpine
   *   - script: echo test
   *   - needs: [nonexistent-job]  # Error: job doesn't exist
   * </pre></blockquote>
   *
   * @param jobName the name of the job containing the needs declaration
   * @param job the job object being validated (currently unused but kept for consistency)
   * @param needs the list of job names that this job depends on
   * @param allJobs the complete map of all jobs in the pipeline
   * @param parser the position-aware parser for error location tracking
   * @param errorCollector the collector for validation errors
   */
  private void validateReferencedJobsExist(
      String jobName,
      Job job,
      List<String> needs,
      Map<String, Job> allJobs,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    for (int i = 0; i < needs.size(); i++) {
      String neededJob = needs.get(i);

      if (!allJobs.containsKey(neededJob)) {
        // Try to get position of the specific needs item
        Position pos = findNeedsFieldPosition(parser, jobName);
        errorCollector.addError(pos,
            "Job '"
                + jobName
                + "' references non-existent job '"
                + neededJob
                + "' in 'needs'");
      }
    }
  }

  /**
   * Validates that all dependency references are within the same stage.
   *
   * <p>This method enforces the constraint that job dependencies can only reference
   * jobs in the same stage. This ensures dependencies control execution order within
   * a stage but do not create cross-stage dependencies, which would violate the
   * pipeline's stage-based execution model.
   *
   * <p>For each dependency, the method compares the dependent job's stage with the
   * referenced job's stage and reports any mismatches.
   *
   * <p>Example invalid configuration:
   * <blockquote><pre>
   * build-job:
   *   - stage: build
   *   - image: gradle
   *   - script: gradle build
   *
   * test-job:
   *   - stage: test
   *   - image: gradle
   *   - script: gradle test
   *   - needs: [build-job]  # Error: build-job is in 'build', not 'test'
   * </pre></blockquote>
   *
   * @param jobName the name of the job containing the needs declaration
   * @param job the job object being validated
   * @param needs the list of job names that this job depends on
   * @param allJobs the complete map of all jobs in the pipeline
   * @param parser the position-aware parser for error location tracking
   * @param errorCollector the collector for validation errors
   */
  private void validateSameStage(
      String jobName,
      Job job,
      List<String> needs,
      Map<String, Job> allJobs,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    String jobStage = job.getStage();

    for (int i = 0; i < needs.size(); i++) {
      String neededJob = needs.get(i);
      Job neededJobObj = allJobs.get(neededJob);

      if (neededJobObj != null) {
        String neededJobStage = neededJobObj.getStage();

        if (!jobStage.equals(neededJobStage)) {
          Position pos = findNeedsItemPosition(parser, jobName, i);

          errorCollector.addError(pos,
              "Job '" + jobName + "' (stage: '"
                  + jobStage + "') cannot depend on job '"
                  + neededJob + "' (stage: '" + neededJobStage
                  + "'). Dependencies must be in the same stage");
        }
      }
    }
  }

  /**
   * Validates that job dependencies do not form circular references.
   *
   * <p>This method builds a dependency graph from all jobs' needs declarations
   * and uses depth-first search to detect cycles. When a cycle is found, the
   * complete cycle path is reported to help developers understand the circular
   * dependency.
   *
   * <p>The cycle detection algorithm:
   * <ol>
   *   <li>Constructs a directed graph where edges represent dependencies</li>
   *   <li>Performs DFS from each unvisited job</li>
   *   <li>Tracks recursion stack to detect back edges (cycles)</li>
   *   <li>Reports the first cycle found with complete path</li>
   * </ol>
   *
   * <p>Example invalid configuration (circular dependency):
   * <blockquote><pre>
   * job1:
   *   - stage: test
   *   - needs: [job2]
   *
   * job2:
   *   - stage: test
   *   - needs: [job3]
   *
   * job3:
   *   - stage: test
   *   - needs: [job1]  # Creates cycle: job1 -> job2 -> job3 -> job1
   * </pre></blockquote>
   * Would add error: {@code "config.yaml:14:1: ERROR, Cycle detected in 'needs'
   * requirements: job1 -> job2 -> job3 -> job1"}
   *
   * @param jobs the complete map of all jobs in the pipeline
   * @param parser the position-aware parser for error location tracking
   * @param errorCollector the collector for validation errors
   * @see #detectCycle(String, Map, Set, Set, List)
   */
  private void validateNoCycles(
      Map<String, Job> jobs,
      PositionAwareYamlParser parser,
      ValidationErrorCollector errorCollector) {

    // Build dependency graph
    Map<String, List<String>> graph = new HashMap<>();
    for (Map.Entry<String, Job> entry : jobs.entrySet()) {
      String jobName = entry.getKey();
      List<String> needs = entry.getValue().getNeeds();
      graph.put(jobName, needs == null ? new ArrayList<>() : new ArrayList<>(needs));
    }

    // Check for cycles using DFS
    Set<String> visited = new HashSet<>();
    Set<String> recursionStack = new HashSet<>();

    for (String jobName : graph.keySet()) {
      if (!visited.contains(jobName)) {
        List<String> cycle = detectCycle(
            jobName, graph, visited, recursionStack, new ArrayList<>());
        if (cycle != null) {
          // Found a cycle, report it
          String cycleString = String.join(" -> ", cycle) + " -> " + cycle.get(0);
          Position pos = findNeedsFieldPosition(parser, cycle.get(0));
          errorCollector.addError(pos,
              "Cycle detected in 'needs' requirements: " + cycleString);
          return;
        }
      }
    }
  }

  /**
   * Detects cycles in the dependency graph using depth-first search.
   *
   * <p>This method performs a single DFS traversal starting from the given node,
   * tracking visited nodes and the current recursion stack to detect back edges
   * that indicate cycles. When a cycle is detected, the method returns the subset
   * of the path that forms the cycle.
   *
   * <p>The algorithm uses:
   * <ul>
   *   <li>{@code visited}: Tracks all nodes seen in the entire search</li>
   *   <li>{@code recursionStack}: Tracks nodes in the current DFS path</li>
   *   <li>{@code path}: Accumulates the current path for cycle reporting</li>
   * </ul>
   *
   * <p>When a back edge is detected (visiting a node already in the recursion
   * stack), the portion of the path from that node to the current position
   * forms the cycle.
   *
   * @param node the current node being visited in the DFS traversal
   * @param graph the dependency graph as an adjacency list
   * @param visited the set of all nodes visited across all DFS calls
   * @param recursionStack the set of nodes in the current recursion path
   * @param path the accumulated path from the DFS root to current node
   * @return a list of job names forming the cycle if one is detected, or
   *     {@code null} if no cycle is found from this node
   */
  private List<String> detectCycle(
      String node,
      Map<String, List<String>> graph,
      Set<String> visited,
      Set<String> recursionStack,
      List<String> path) {

    visited.add(node);
    recursionStack.add(node);
    path.add(node);

    List<String> neighbors = graph.get(node);
    if (neighbors != null) {
      for (String neighbor : neighbors) {
        if (!visited.contains(neighbor)) {
          List<String> cycle = detectCycle(
              neighbor, graph, visited, recursionStack, new ArrayList<>(path));
          if (cycle != null) {
            return cycle;
          }
        } else if (recursionStack.contains(neighbor)) {
          // Found a cycle
          int cycleStart = path.indexOf(neighbor);
          return new ArrayList<>(path.subList(cycleStart, path.size()));
        }
      }
    }

    recursionStack.remove(node);
    return null;
  }

  /**
   * Finds the position of the needs field for a job.
   *
   * <p>Searches through the job's array structure to locate where the needs
   * field is defined, using the same path notation as the parser.
   *
   * @param parser the position-aware parser
   * @param jobName the name of the job
   * @return the position of the needs field, or the job name position as fallback
   */
  private Position findNeedsFieldPosition(
      PositionAwareYamlParser parser,
      String jobName) {

    Map<String, Position> valuePositions = parser.getValuePositions();

    for (Map.Entry<String, Position> entry : valuePositions.entrySet()) {
      String path = entry.getKey();
      if (path.startsWith(jobName + "[") && path.endsWith(".needs")) {
        return entry.getValue();
      }
    }

    // Fallback to job name
    Position pos = parser.getKeyPosition(jobName);
    return pos != null ? pos : new Position(0, 0);
  }
  /**
   * Finds the position of a specific item in a job's needs list.
   *
   * <p>Locates the exact position of the needs item at the given index,
   * using the parser's position tracking.
   *
   * @param parser the position-aware parser
   * @param jobName the name of the job
   * @param itemIndex the index of the needs item
   * @return the position of the needs item, or fallback position
   */

  private Position findNeedsItemPosition(
      PositionAwareYamlParser parser,
      String jobName,
      int itemIndex) {

    Map<String, Position> valuePositions = parser.getValuePositions();

    for (Map.Entry<String, Position> entry : valuePositions.entrySet()) {
      String path = entry.getKey();
      if (path.startsWith(jobName + "[")
          && path.contains(".needs[" + itemIndex + "]")) {
        return entry.getValue();
      }
    }

    // Fallback to needs field position
    Position pos = findNeedsFieldPosition(parser, jobName);
    return pos != null ? pos : new Position(0, 0);
  }
}