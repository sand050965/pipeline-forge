package edu.northeastern.cs7580.cicd.executionservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ExecutionServiceApplicationTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("cicd")
          .withUsername("postgres")
          .withPassword("cicd");

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);

    registry.add("spring.r2dbc.host", POSTGRES::getHost);
    registry.add("spring.r2dbc.port", () -> POSTGRES.getMappedPort(5432));
    registry.add("spring.r2dbc.database", POSTGRES::getDatabaseName);
    registry.add("spring.r2dbc.username", POSTGRES::getUsername);
    registry.add("spring.r2dbc.password", POSTGRES::getPassword);

    registry.add("spring.flyway.enabled", () -> true);
    registry.add("management.health.rabbit.enabled", () -> false);
    registry.add("management.health.redis.enabled", () -> false);
  }

  @Test
  void contextLoads() {
  }
}