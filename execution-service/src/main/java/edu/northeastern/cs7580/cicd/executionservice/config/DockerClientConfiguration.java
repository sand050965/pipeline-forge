package edu.northeastern.cs7580.cicd.executionservice.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link DockerClient} used by the Execution Service.
 *
 * <p>This configuration centralizes Docker client construction so production code
 * can use a real Docker daemon while unit tests can inject a mocked client.
 *
 * <p>The client connects to the Docker host resolved from the default Docker
 * client configuration (e.g., DOCKER_HOST or platform defaults).
 */
@Configuration
public class DockerClientConfiguration {

  /**
   * Creates a Docker client backed by an Apache HTTP transport.
   *
   * @return a Docker client instance
   */
  @Bean
  public DockerClient dockerClient() {
    DefaultDockerClientConfig config =
        DefaultDockerClientConfig.createDefaultConfigBuilder().build();

    DockerHttpClient httpClient =
        new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .build();

    return DockerClientImpl.getInstance(config, httpClient);
  }
}
