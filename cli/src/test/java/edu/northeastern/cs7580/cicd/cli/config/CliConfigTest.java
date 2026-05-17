package edu.northeastern.cs7580.cicd.cli.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CliConfigTest {

  @AfterEach
  void tearDown() {
    System.clearProperty(CliConfig.API_GATEWAY_BASE_URL_PROP);
  }

  @Test
  void apiGatewayBaseUrl_returnsDefault_whenNothingSet() {
    assertEquals("http://localhost:8080", CliConfig.apiGatewayBaseUrl());
  }

  @Test
  void apiGatewayBaseUrl_returnsSystemProperty_whenSet() {
    System.setProperty(CliConfig.API_GATEWAY_BASE_URL_PROP, "http://my-server:9090");
    assertEquals("http://my-server:9090", CliConfig.apiGatewayBaseUrl());
  }

  @Test
  void apiGatewayBaseUrl_trimsTrailingSlash_fromSystemProperty() {
    System.setProperty(CliConfig.API_GATEWAY_BASE_URL_PROP, "http://my-server:9090/");
    assertEquals("http://my-server:9090", CliConfig.apiGatewayBaseUrl());
  }

  @Test
  void apiGatewayBaseUrl_ignoresBlankSystemProperty_andFallsToDefault() {
    System.setProperty(CliConfig.API_GATEWAY_BASE_URL_PROP, "   ");
    assertEquals("http://localhost:8080", CliConfig.apiGatewayBaseUrl());
  }
}