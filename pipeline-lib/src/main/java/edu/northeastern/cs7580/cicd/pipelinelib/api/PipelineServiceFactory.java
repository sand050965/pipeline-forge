package edu.northeastern.cs7580.cicd.pipelinelib.api;

import edu.northeastern.cs7580.cicd.pipelinelib.internal.builder.ExecutionPlanBuilder;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.PipelineDryRunService;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.PipelineServiceImpl;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.service.PipelineValidationService;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.JobValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.NeedValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.StageValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.internal.validator.YamlValidator;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;

/**
 * Factory for creating {@link PipelineService} instances with default configuration.
 *
 * <p>The {@code PipelineServiceFactory} class provides a simple, centralized way to
 * obtain fully configured {@code PipelineService} instances without needing to
 * understand internal dependencies, validators, or implementation details. This
 * factory encapsulates the wiring of all internal components required for pipeline
 * validation and execution planning.
 *
 * <p>This factory creates {@code PipelineService} instances that are configured with:
 * <ul>
 *   <li>YAML structure validation</li>
 *   <li>Stage validation (existence, uniqueness, non-empty)</li>
 *   <li>Job validation (required fields, valid stage references)</li>
 *   <li>Dependency validation (circular dependency detection, same-stage constraints)</li>
 *   <li>Execution plan generation with topological sorting</li>
 * </ul>
 *
 * <p><b>Design Pattern:</b> This class implements the Factory Method pattern,
 * providing a single static method for object creation. The factory hides the
 * complexity of internal dependency wiring from consumers of the library.
 *
 * <p><b>Thread Safety:</b> The {@code create()} method is thread-safe and can be
 * called concurrently from multiple threads. Each call returns a new instance with
 * its own internal state. The returned {@code PipelineService} instances are
 * thread-safe for concurrent read operations.
 *
 * <p><b>Basic Usage Example:</b>
 * <blockquote><pre>
 *     // Create a service instance
 *     PipelineService service = PipelineServiceFactory.create();
 *
 *     // Use the service
 *     Pipeline pipeline = service.validatePipeline(
 *         Path.of(".pipelines/default.yaml")
 *     );
 * </pre></blockquote>
 *
 * <p><b>Singleton Pattern Example (Optional):</b>
 *
 * <p>If you want to reuse the same service instance across your application:
 * <blockquote><pre>
 *     public class PipelineManager {
 *         private static final PipelineService SERVICE =
 *             PipelineServiceFactory.create();
 *
 *         public Pipeline validate(Path file) {
 *             return SERVICE.validatePipeline(file);
 *         }
 *     }
 * </pre></blockquote>
 *
 * <p><b>Dependency Injection Example:</b>
 *
 * <p>For frameworks like Spring or CDI:
 * <blockquote><pre>
 *     &#64;Configuration
 *     public class PipelineConfig {
 *         &#64;Bean
 *         public PipelineService pipelineService() {
 *             return PipelineServiceFactory.create();
 *         }
 *     }
 * </pre></blockquote>
 *
 * <p><b>Testing Example:</b>
 *
 * <p>Create fresh instances for each test to ensure isolation:
 * <blockquote><pre>
 *     &#64;Test
 *     void testPipelineValidation() {
 *         PipelineService service = PipelineServiceFactory.create();
 *         Pipeline pipeline = service.validatePipeline(testFile);
 *         assertNotNull(pipeline);
 *     }
 * </pre></blockquote>
 *
 * <p>This class cannot be instantiated. Attempting to create an instance will
 * result in an {@code UnsupportedOperationException}. All functionality is
 * provided through the static {@link #create()} method.
 *
 * @implNote The factory creates instances with the following internal components:
 *     <ul>
 *       <li>{@code YamlValidator} - validates basic YAML structure</li>
 *       <li>{@code StageValidator} - validates stage rules</li>
 *       <li>{@code JobValidator} - validates job rules</li>
 *       <li>{@code NeedValidator} - validates dependency rules</li>
 *       <li>{@code ExecutionPlanBuilder} - generates execution plans</li>
 *       <li>{@code PipelineValidationService} - coordinates validation</li>
 *       <li>{@code PipelineDryRunService} - generates execution plans</li>
 *     </ul>
 *     These internal components are wired together and wrapped in a
 *     {@code PipelineServiceImpl} instance.
 * @since 1.0.0
 * @see PipelineService
 * @see Pipeline
 * @see ExecutionPlan
 */
public final class PipelineServiceFactory {

  /**
   * Creates a new {@code PipelineService} instance with default configuration.
   *
   * <p>This method instantiates and wires together all necessary internal
   * components (validators, parsers, builders) to create a fully functional
   * {@code PipelineService}. The returned service is immediately ready for use
   * and requires no additional configuration.
   *
   * <p>Each call to this method creates a new instance with fresh internal state.
   * If you need to reuse the same service instance across multiple operations,
   * store the returned reference rather than calling this method repeatedly.
   *
   * <p><b>Instance Reuse Example:</b>
   * <blockquote><pre>
   *     // Good: Create once, reuse many times
   *     PipelineService service = PipelineServiceFactory.create();
   *     Pipeline p1 = service.validatePipeline(file1);
   *     Pipeline p2 = service.validatePipeline(file2);
   *     ExecutionPlan plan = service.createExecutionPlan(file3);
   * </pre></blockquote>
   *
   * <p><b>Not Recommended:</b>
   * <blockquote><pre>
   *     // Inefficient: Creates new instance each time
   *     Pipeline p1 = PipelineServiceFactory.create().validatePipeline(file1);
   *     Pipeline p2 = PipelineServiceFactory.create().validatePipeline(file2);
   * </pre></blockquote>
   *
   * <p>The returned {@code PipelineService} is:
   * <ul>
   *   <li>Thread-safe for concurrent validation and plan generation operations</li>
   *   <li>Stateless - maintains no mutable state between method calls</li>
   *   <li>Fully configured with all necessary validators and builders</li>
   *   <li>Ready for immediate use without additional setup</li>
   * </ul>
   *
   * @return a fully configured {@code PipelineService} instance capable of
   *     validating pipeline configurations and generating execution plans.
   *     The instance is thread-safe and stateless.
   * @see PipelineService
   */
  public static PipelineService create() {
    // Wire up all dependencies
    YamlValidator yamlValidator = new YamlValidator();
    StageValidator stageValidator = new StageValidator();
    JobValidator jobValidator = new JobValidator();
    NeedValidator needValidator = new NeedValidator();

    ExecutionPlanBuilder executionPlanBuilder = new ExecutionPlanBuilder();

    PipelineValidationService validationService = new PipelineValidationService(
        yamlValidator,
        stageValidator,
        jobValidator,
        needValidator
    );

    PipelineDryRunService dryRunService = new PipelineDryRunService(
        validationService,
        executionPlanBuilder
    );

    return new PipelineServiceImpl(validationService, dryRunService);
  }

  /**
   * Private constructor to prevent instantiation.
   *
   * <p>This factory class provides only static utility methods and should never
   * be instantiated. All functionality is accessed through the static
   * {@link #create()} method.
   *
   * @throws UnsupportedOperationException always, to prevent instantiation
   */
  private PipelineServiceFactory() {
    throw new UnsupportedOperationException("Factory class cannot be instantiated");
  }
}
