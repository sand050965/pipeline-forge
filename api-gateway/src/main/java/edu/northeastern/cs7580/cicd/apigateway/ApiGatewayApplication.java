package edu.northeastern.cs7580.cicd.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway application entry point.
 *
 * <p>This Spring Boot application serves as the entry point for all CLI requests
 * in the CI/CD system. The API Gateway acts as a routing layer that forwards
 * requests to appropriate backend microservices:
 * <ul>
 *   <li>Execution Service - handles pipeline execution requests</li>
 *   <li>Report Service - handles execution report queries</li>
 * </ul>
 *
 * <p>The gateway provides a unified API for the CLI while allowing backend
 * services to scale independently. It handles request validation, error
 * translation, and provides consistent logging across all operations.
 *
 * <p>This application uses Spring WebFlux for reactive, non-blocking I/O,
 * making it efficient for cloud deployments where multiple pipelines may
 * execute concurrently.
 */
@SpringBootApplication
public class ApiGatewayApplication {

  /**
   * Application entry point.
   *
   * <p>Starts the API Gateway service on the configured port (default: 8080).
   * The service begins accepting HTTP requests from the CLI and routes them
   * to backend microservices based on the request path.
   *
   * @param args command-line arguments (not currently used)
   */
  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayApplication.class, args);
  }
}