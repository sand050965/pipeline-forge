package edu.northeastern.cs7580.cicd.pipelinelib.internal.service;

import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineServiceFactory;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import edu.northeastern.cs7580.cicd.pipelinelib.model.Pipeline;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;

/**
 * Default implementation of the {@link PipelineService} interface.
 *
 * <p><b>INTERNAL API - Do not use directly.</b> This class is an internal
 * implementation detail of the pipeline library and is subject to change without
 * notice. External code should only depend on the {@link PipelineService} interface
 * and obtain instances through {@link PipelineServiceFactory}.
 *
 * <p>The {@code PipelineServiceImpl} class delegates all operations to internal
 * validation and execution planning services, providing a clean separation between
 * the public API and internal implementation details. This implementation:
 * <ul>
 *   <li>Delegates validation operations to {@code PipelineValidationService}</li>
 *   <li>Delegates execution plan generation to {@code PipelineDryRunService}</li>
 *   <li>Provides no additional business logic beyond delegation</li>
 *   <li>Maintains no mutable state between method calls</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This implementation is thread-safe. All delegate services
 * are stateless or properly synchronized, allowing concurrent validation and plan
 * generation operations from multiple threads.
 *
 * <p><b>Architectural Role:</b> This class serves as an adapter between the public
 * {@code PipelineService} API and internal validation/planning services. It allows
 * the internal implementation to evolve independently of the public API contract.
 *
 * <p><b>Why This Class Exists:</b>
 * <ol>
 *   <li><b>Encapsulation:</b> Hides internal services from external consumers</li>
 *   <li><b>Flexibility:</b> Internal services can be refactored without affecting the API</li>
 *   <li><b>Simplicity:</b> Provides a single, clean entry point for all operations</li>
 *   <li><b>Testability:</b> Allows internal services to be tested independently</li>
 * </ol>
 *
 * <p><b>Internal Architecture:</b>
 * <pre>
 * PipelineService (public interface)
 *        ↓
 * PipelineServiceImpl (adapter)
 *        ↓
 * ├─ PipelineValidationService
 * │  └─ YamlValidator, StageValidator, JobValidator, NeedValidator
 * └─ PipelineDryRunService
 *    └─ ExecutionPlanBuilder
 * </pre>
 *
 * <p><b>Correct Usage:</b>
 * <blockquote><pre>
 *     // ✅ Correct - use the factory
 *     PipelineService service = PipelineServiceFactory.create();
 * </pre></blockquote>
 *
 * <p><b>Incorrect Usage:</b>
 * <blockquote><pre>
 *     // ❌ Wrong - don't instantiate directly
 *     PipelineServiceImpl impl = new PipelineServiceImpl(...);
 * </pre></blockquote>
 *
 * <p>This class uses constructor-based dependency injection via Lombok's
 * {@code @RequiredArgsConstructor} to ensure all required dependencies are
 * provided at instantiation time. Dependencies are immutable after construction.
 *
 * @implNote This implementation performs no validation or business logic itself.
 *     It serves purely as a facade that delegates to specialized internal services.
 *     All validation rules, dependency analysis, and execution plan generation
 *     are handled by the delegate services.
 * @see PipelineService
 * @see PipelineServiceFactory
 * @see PipelineValidationService
 * @see PipelineDryRunService
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class PipelineServiceImpl implements PipelineService {

  /**
   * Internal service responsible for comprehensive pipeline validation.
   *
   * <p>Handles YAML parsing, structure validation, stage validation, job validation,
   * and dependency validation (including circular dependency detection). This service
   * coordinates multiple specialized validators to ensure complete validation coverage.
   */
  private final PipelineValidationService validationService;

  /**
   * Internal service responsible for generating execution plans.
   *
   * <p>Takes validated pipeline configurations and produces execution plans with
   * topologically sorted jobs and complete dependency mappings. This service handles
   * the transformation from validated configuration to executable plan.
   */
  private final PipelineDryRunService dryRunService;

  /**
   * {@inheritDoc}
   *
   * <p>This implementation delegates validation to the internal
   * {@code PipelineValidationService}, which performs comprehensive validation
   * including YAML parsing, structure checking, and semantic analysis.
   */
  @Override
  public Pipeline validatePipeline(Path configFile) throws ValidationException {
    return validationService.validateAndParse(configFile);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation delegates directory validation to the internal
   * {@code PipelineValidationService}, which scans for YAML files, validates
   * each one, and checks for duplicate pipeline names across files.
   */
  @Override
  public void validateDirectory(Path directory) throws ValidationException {
    validationService.validateDirectory(directory);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation delegates execution plan generation to the internal
   * {@code PipelineDryRunService}, which creates execution plans with topologically
   * sorted jobs and complete dependency mappings.
   */
  @Override
  public ExecutionPlan createExecutionPlan(Path configFile) throws ValidationException {
    return dryRunService.buildExecutionPlan(configFile);
  }

}
