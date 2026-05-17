package edu.northeastern.cs7580.cicd.cli.config;

/**
 * Configuration settings for the CI/CD CLI application.
 *
 * <p>This class holds configuration values such as API Gateway URLs,
 * default paths, timeouts, and other CLI options. Configuration can be
 * loaded from environment variables, configuration files, or command-line
 * arguments.
 */
public final class CliConfig {

  /** System property name for API Gateway base URL. */
  public static final String API_GATEWAY_BASE_URL_PROP = "cicd.gateway.baseUrl";

  /** Environment variable name for API Gateway base URL. */
  public static final String API_GATEWAY_BASE_URL_ENV = "CICD_GATEWAY_BASE_URL";

  private CliConfig() {}

  /**
   * Returns the API Gateway base URL.
   *
   * <p>Resolution order:
   * <ol>
   *   <li>System property {@value #API_GATEWAY_BASE_URL_PROP}</li>
   *   <li>Environment variable {@value #API_GATEWAY_BASE_URL_ENV}</li>
   *   <li>Default {@code http://localhost:8080}</li>
   * </ol>
   *
   * @return base URL for API Gateway
   */
  public static String apiGatewayBaseUrl() {
    String prop = System.getProperty(API_GATEWAY_BASE_URL_PROP);
    if (prop != null && !prop.isBlank()) {
      return trimTrailingSlash(prop);
    }
    String env = System.getenv(API_GATEWAY_BASE_URL_ENV);
    if (env != null && !env.isBlank()) {
      return trimTrailingSlash(env);
    }
    return "http://localhost:8080";
  }

  private static String trimTrailingSlash(String url) {
    if (url.endsWith("/")) {
      return url.substring(0, url.length() - 1);
    }
    return url;
  }
}
