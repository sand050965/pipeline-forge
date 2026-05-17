package edu.northeastern.cs7580.cicd.executionservice.controller;

import edu.northeastern.cs7580.cicd.executionservice.config.RabbitMqConfig;
import edu.northeastern.cs7580.cicd.executionservice.dto.GitMetadata;
import edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionMessage;
import edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionRequest;
import edu.northeastern.cs7580.cicd.executionservice.dto.PipelineExecutionResponse;
import edu.northeastern.cs7580.cicd.executionservice.model.ExecutionStatus;
import edu.northeastern.cs7580.cicd.executionservice.service.ExecutionPersistenceService;
import edu.northeastern.cs7580.cicd.executionservice.service.ExecutionService;
import edu.northeastern.cs7580.cicd.executionservice.service.WorkspaceService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineServiceFactory;
import edu.northeastern.cs7580.cicd.pipelinelib.exception.ValidationException;
import edu.northeastern.cs7580.cicd.pipelinelib.model.ExecutionPlan;
import jakarta.validation.Valid;
import java.util.UUID;
import org.apache.logging.log4j.internal.annotation.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * REST controller responsible for accepting and enqueuing CI/CD pipeline execution requests.
 *
 * <p>Exposes endpoints under {@code /api/v1/execution} and is built on Spring WebFlux,
 * returning reactive {@link Mono} types to avoid blocking the event loop.</p>
 *
 * <h2>Async Execution Flow</h2>
 * <ol>
 *   <li>Clone the repository to a temporary workspace (blocking, on boundedElastic).</li>
 *   <li>Parse and validate the pipeline YAML — return {@code 400} immediately if invalid.</li>
 *   <li>Initialize DB records: upsert pipeline, create {@code PENDING} run,
 *       pre-create all stage/job rows.</li>
 *   <li>Publish a {@link PipelineExecutionMessage} to RabbitMQ — if this fails,
 *       roll back the DB records and return {@code 500}.</li>
 *   <li>Clean up the workspace (the consumer will re-clone independently).</li>
 *   <li>Return {@code 202 Accepted} with {@code runNumber} and {@code pipelineName}
 *       — before any job has executed.</li>
 * </ol>
 *
 * <h2>Response Codes</h2>
 * <ul>
 *   <li>{@code 202 Accepted} — run enqueued successfully; consumer will execute async</li>
 *   <li>{@code 400 Bad Request} — pipeline YAML failed validation</li>
 *   <li>{@code 500 Internal Server Error} — DB write or queue publish failed;
 *       no run record is left in the database</li>
 * </ul>
 *
 * @see edu.northeastern.cs7580.cicd.executionservice.consumer.PipelineExecutionConsumer
 * @see ExecutionService
 * @see WorkspaceService
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "ExecutionPersistenceService and RabbitTemplate are Spring-managed singleton "
        + "beans; storing the references is intentional and safe."
)
@RestController
@RequestMapping("/api/v1/execution")
public class ExecutionController {

  private static final Logger logger = LoggerFactory.getLogger(ExecutionController.class);

  private final ExecutionService executionService;
  private final ExecutionPersistenceService persistenceService;
  private final WorkspaceService workspaceService;
  private final RabbitTemplate rabbitTemplate;
  private final PipelineService pipelineService;

  /**
   * Primary Spring-managed constructor.
   *
   * @param executionService   service for DB initialization and pipeline execution logic
   * @param persistenceService service for DB rollback on queue publish failure
   * @param workspaceService   service for cloning and cleaning up Git workspaces
   * @param rabbitTemplate     AMQP template for publishing messages to RabbitMQ
   */
  @Autowired
  public ExecutionController(ExecutionService executionService,
                             ExecutionPersistenceService persistenceService,
                             WorkspaceService workspaceService,
                             RabbitTemplate rabbitTemplate) {
    this.executionService = executionService;
    this.persistenceService = persistenceService;
    this.workspaceService = workspaceService;
    this.rabbitTemplate = rabbitTemplate;
    this.pipelineService = PipelineServiceFactory.create();
  }

  /**
   * Package-protected constructor for unit testing, allowing mock collaborators.
   *
   * @param executionService   service for DB initialization and pipeline execution logic
   * @param persistenceService service for DB rollback on queue publish failure
   * @param workspaceService   service for cloning and cleaning up Git workspaces
   * @param rabbitTemplate     AMQP template for publishing messages to RabbitMQ
   * @param pipelineService    pipeline parsing and validation service (typically a test double)
   */
  protected ExecutionController(ExecutionService executionService,
                                ExecutionPersistenceService persistenceService,
                                WorkspaceService workspaceService,
                                RabbitTemplate rabbitTemplate,
                                PipelineService pipelineService) {
    this.executionService = executionService;
    this.persistenceService = persistenceService;
    this.workspaceService = workspaceService;
    this.rabbitTemplate = rabbitTemplate;
    this.pipelineService = pipelineService;
  }

  /**
   * Validates a pipeline request, initializes DB records, and enqueues the run.
   *
   * <p>Returns immediately with the run number — the pipeline is executed
   * asynchronously by the {@link
   * edu.northeastern.cs7580.cicd.executionservice.consumer.PipelineExecutionConsumer}.</p>
   *
   * @param request the pipeline execution request; must be a valid
   *                {@link PipelineExecutionRequest}
   * @return {@code 202 Accepted} with run number and pipeline name on success,
   *         {@code 400} on validation failure, {@code 500} on infrastructure failure
   */
  @PostMapping("/execute")
  public Mono<ResponseEntity<PipelineExecutionResponse>> executePipeline(
      @Valid @RequestBody PipelineExecutionRequest request) {

    String executionId = UUID.randomUUID().toString();
    GitMetadata gitMetadata = request.getGitMetadata();

    logger.info(
        "Received execution request (ID: {}): pipeline={}, branch={}, commit={}",
        executionId, request.getPipelineName(),
        gitMetadata.getBranch(), gitMetadata.getCommitHash());

    return Mono.fromCallable(() -> workspaceService.prepareWorkspace(gitMetadata))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(workspacePath -> {
          logger.debug("Cloned workspace (ID: {}): {}", executionId, workspacePath);

          // Validate pipeline YAML — fail fast before touching the DB
          ExecutionPlan plan;
          try {
            plan = pipelineService.createExecutionPlan(
                workspacePath.resolve(request.getPipelineFilePath()));
          } catch (ValidationException e) {
            workspaceService.cleanupWorkspace(workspacePath);
            return Mono.just(validationFailedResponse(executionId, request, e.getMessage()));
          }

          logger.info("Validated execution plan (ID: {}): {} stages, {} total jobs",
              executionId,
              plan.getStages().size(),
              plan.getStages().stream().mapToInt(s -> s.getJobs().size()).sum());

          // Initialize DB records (PENDING run + all stage/job rows)
          return executionService.initializePipelineRun(
                  plan, gitMetadata, request.getPipelineName(), request.getPipelineFilePath())
              .flatMap(message -> Mono.fromCallable(() -> {
                // Publish to RabbitMQ — roll back DB records if this fails
                rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE_NAME,
                    RabbitMqConfig.ROUTING_KEY,
                    message);
                logger.info(
                    "Enqueued pipeline run (ID: {}) run_no={} pipeline={}",
                    executionId, message.getRunNo(), message.getPipelineName());
                return message;
              }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(enqueued -> {
                  workspaceService.cleanupWorkspace(workspacePath);
                  return Mono.just(acceptedResponse(executionId, request, enqueued));
                })
                .onErrorResume(e -> {
                  logger.error(
                      "Failed to publish to RabbitMQ (ID: {}), rolling back run record — {}",
                      executionId, e.getMessage());
                  return persistenceService.rollbackPipelineRun(message.getRunId())
                      .thenReturn(internalErrorResponse(executionId, request));
                })
              )
              .switchIfEmpty(Mono.fromCallable(() -> {
                // initializePipelineRun emitted empty — DB setup failed
                workspaceService.cleanupWorkspace(workspacePath);
                return internalErrorResponse(executionId, request);
              }));
        })

        .onErrorResume(ValidationException.class, e -> {
          logger.error("Pipeline validation failed (ID: {}): {}", executionId, e.getMessage());
          return Mono.just(validationFailedResponse(executionId, request, e.getMessage()));
        })

        .onErrorResume(e -> {
          logger.error("Unexpected error during pipeline submission (ID: {})", executionId, e);
          return Mono.just(internalErrorResponse(executionId, request));
        });
  }

  /**
   * Builds a {@code 202 Accepted} response indicating the run was successfully enqueued.
   *
   * @param executionId unique identifier for this submission request
   * @param request     the original pipeline execution request
   * @param message     the enqueued message containing the assigned run number
   * @return 202 response with run number and pipeline name
   */
  private ResponseEntity<PipelineExecutionResponse> acceptedResponse(
      String executionId, PipelineExecutionRequest request, PipelineExecutionMessage message) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(PipelineExecutionResponse.builder()
            .executionId(executionId)
            .pipelineName(request.getPipelineName())
            .runNumber(message.getRunNo())
            .status(ExecutionStatus.PENDING)
            .message(String.format("%s run: %d", request.getPipelineName(), message.getRunNo()))
            .build());
  }

  /**
   * Builds a {@code 400 Bad Request} response for pipeline validation failures.
   *
   * @param executionId unique identifier for this submission request
   * @param request     the original pipeline execution request
   * @param message     the validation error message
   * @return 400 response with validation failure details
   */
  private ResponseEntity<PipelineExecutionResponse> validationFailedResponse(
      String executionId, PipelineExecutionRequest request, String message) {
    return ResponseEntity.badRequest()
        .body(PipelineExecutionResponse.builder()
            .executionId(executionId)
            .pipelineName(request.getPipelineName())
            .runNumber(0)
            .status(ExecutionStatus.VALIDATION_FAILED)
            .message("Pipeline validation failed: " + message)
            .build());
  }

  /**
   * Builds a {@code 500 Internal Server Error} response for infrastructure failures
   * (DB write failure or RabbitMQ publish failure).
   *
   * @param executionId unique identifier for this submission request
   * @param request     the original pipeline execution request
   * @return 500 response prompting the user to file an issue
   */
  private ResponseEntity<PipelineExecutionResponse> internalErrorResponse(
      String executionId, PipelineExecutionRequest request) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(PipelineExecutionResponse.builder()
            .executionId(executionId)
            .pipelineName(request.getPipelineName())
            .runNumber(0)
            .status(ExecutionStatus.FAILED)
            .message("Internal error: your request was valid but the system encountered "
                + "an unexpected error. Please file an issue.")
            .build());
  }
}
