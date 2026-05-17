package edu.northeastern.cs7580.cicd.executionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the Execution Service Spring Boot application.
 *
 * <p>The Execution Service is a CI/CD microservice responsible for running
 * pipeline definitions against a target Git repository. It receives execution
 * requests, prepares workspaces, parses pipeline definitions, executes stages
 * and jobs, and persists run results for downstream reporting.
 *
 * <p>The service exposes the following capabilities:
 * <ul>
 *   <li>Trigger a full pipeline execution from repository metadata and pipeline path.</li>
 *   <li>Execute pipeline stages and jobs in sequence with status tracking.</li>
 *   <li>Persist pipeline, stage, and job run results for history and observability.</li>
 *   <li>Return execution outcomes, including validation and runtime failures.</li>
 * </ul>
 *
 * <p>This service is intended to be called by the API Gateway, which
 * routes {@code cicd run} CLI requests to execution endpoints. The service
 * returns JSON responses; terminal presentation formatting is handled by the CLI.
 *
 * <p>The application enables asynchronous processing via {@link EnableAsync}
 * and excludes {@link R2dbcAutoConfiguration} from auto-configuration.
 */
@SpringBootApplication(exclude = R2dbcAutoConfiguration.class)
@EnableAsync
public class ExecutionServiceApplication {

  /**
   * Starts the Execution Service application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(ExecutionServiceApplication.class, args);
  }

}
