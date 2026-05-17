package edu.northeastern.cs7580.cicd.reportservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Report Service Spring Boot application.
 *
 * <p>The Report Service is a read-only microservice in the CI/CD system
 * that provides REST endpoints for querying pipeline execution history
 * stored in PostgreSQL. It uses Spring WebFlux and R2DBC for fully
 * reactive, non-blocking request handling and database access.
 *
 * <p>The service exposes the following capabilities:
 * <ul>
 *   <li>Query all past runs for a given pipeline by name.</li>
 *   <li>Query details of a specific pipeline run, including its stages.</li>
 *   <li>Query details of a specific stage within a run, including its jobs.</li>
 *   <li>Query details of a specific job within a stage.</li>
 * </ul>
 *
 * <p>This service is intended to be called by the API Gateway, which
 * routes {@code cicd report} CLI requests to the appropriate endpoints.
 * The service returns JSON responses; YAML formatting is the
 * responsibility of the CLI.
 */
@SpringBootApplication
public class ReportServiceApplication {

  /**
   * Starts the Report Service application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(ReportServiceApplication.class, args);
  }
}
