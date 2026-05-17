package edu.northeastern.cs7580.cicd.executionservice.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Wires the Spring-managed OpenTelemetry SDK into the Logback appender so
 * that log records are forwarded to the OTel Collector via OTLP.
 *
 * <p>The {@link OpenTelemetryAppender} declared in {@code logback-spring.xml}
 * cannot discover the Spring-managed {@link OpenTelemetry} bean on its own.
 * This component calls {@link OpenTelemetryAppender#install} after the
 * application context is ready, completing the wiring.</p>
 */
@Component
public class OtelLogbackInstaller implements InitializingBean {

  private final OpenTelemetry openTelemetry;

  public OtelLogbackInstaller(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void afterPropertiesSet() {
    OpenTelemetryAppender.install(openTelemetry);
  }
}
