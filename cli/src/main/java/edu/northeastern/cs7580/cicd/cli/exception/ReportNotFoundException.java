package edu.northeastern.cs7580.cicd.cli.exception;

import edu.northeastern.cs7580.cicd.cli.client.ApiGatewayReportClient;

/**
 * Unchecked exception thrown when the API Gateway returns HTTP 404 in response
 * to a report request, indicating that the requested pipeline, run, stage, or
 * job does not exist in the system's execution history.
 *
 * <p>This exception is thrown by {@link ApiGatewayReportClient} and caught by
 * {@link edu.northeastern.cs7580.cicd.cli.command.ReportCommand}, which prints
 * a user-friendly error message to {@code stderr} instead of propagating a
 * stack trace to the user.
 *
 * <p>This is intentionally unchecked (extends {@link RuntimeException}) so that
 * intermediate layers such as the client interface do not need to declare it in
 * their {@code throws} clauses, keeping the interface signatures clean. Only
 * {@code ReportCommand}, which is responsible for user-facing error handling,
 * needs to catch it explicitly.
 *
 * <p>Example message format produced by {@link ApiGatewayReportClient}:
 * <pre>
 * Not found: http://localhost:8080/api/v1/report/pipelines/default/runs/99
 * </pre>
 *
 * @see ApiGatewayReportClient
 * @see edu.northeastern.cs7580.cicd.cli.command.ReportCommand
 */
public class ReportNotFoundException extends RuntimeException {

  /**
   * Creates a new exception with the given detail message.
   *
   * <p>The message should include enough context for {@code ReportCommand} to
   * construct a meaningful error for the user — typically the full URL that
   * returned 404, as produced by {@link ApiGatewayReportClient}.
   *
   * @param message detail message describing which resource was not found;
   *                accessible via {@link #getMessage()}
   */
  public ReportNotFoundException(String message) {
    super(message);
  }
}
