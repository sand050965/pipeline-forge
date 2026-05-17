package edu.northeastern.cs7580.cicd.executionservice.config;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;

/**
 * R2DBC configuration for connecting to a PostgreSQL database using the reactive
 * {@link PostgresqlConnectionFactory}.
 *
 * <p>
 * Extends {@link AbstractR2dbcConfiguration} to integrate with Spring Data R2DBC,
 * providing a fully reactive, non-blocking database connection for the execution service.</p>
 *
 * <h2>Required Properties</h2>
 * <ul>
 *   <li>{@code spring.r2dbc.host} – database host</li>
 *   <li>{@code spring.r2dbc.port} – database port (defaults to {@code 5432})</li>
 *   <li>{@code spring.r2dbc.database} – database name</li>
 *   <li>{@code spring.r2dbc.username} – authentication username</li>
 *   <li>{@code spring.r2dbc.password} – authentication password</li>
 * </ul>
 *
 * @see AbstractR2dbcConfiguration
 * @see PostgresqlConnectionFactory
 */
@Configuration
public class R2dbcConfig extends AbstractR2dbcConfiguration {

  /**
   * Database host, injected from {@code spring.r2dbc.host}.
   */
  @Value("${spring.r2dbc.host}")
  private String host;

  /**
   * Database port, injected from {@code spring.r2dbc.port}. Defaults to {@code 5432}.
   */
  @Value("${spring.r2dbc.port:5432}")
  private int port;

  /**
   * Target database name, injected from {@code spring.r2dbc.database}.
   */
  @Value("${spring.r2dbc.database}")
  private String database;

  /**
   * Authentication username, injected from {@code spring.r2dbc.username}.
   */
  @Value("${spring.r2dbc.username}")
  private String username;

  /**
   * Authentication password, injected from {@code spring.r2dbc.password}.
   */
  @Value("${spring.r2dbc.password}")
  private String password;

  /**
   * Builds a {@link PostgresqlConnectionFactory} for reactive PostgreSQL connectivity.
   *
   * @return a configured {@link ConnectionFactory}
   */
  @Override
  public ConnectionFactory connectionFactory() {
    return new PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration.builder()
            .host(host)
            .port(port)
            .database(database)
            .username(username)
            .password(password)
            .build()
    );
  }
}