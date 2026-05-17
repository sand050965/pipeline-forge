package edu.northeastern.cs7580.cicd.apigateway.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Wires the Spring-managed OpenTelemetry SDK into the Logback appender so
 * that log records are forwarded to the OTel Collector via OTLP.
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
