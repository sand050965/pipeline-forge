package edu.northeastern.cs7580.cicd.cli.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DefaultPathResolverTest {
  private static final String DEFAULT_PATH = ".pipelines/pipeline.yaml";

  @Test
  void resolveReturnsDefaultWhenInputIsNull() {
    DefaultPathResolver resolver = new DefaultPathResolver();

    String resolved = resolver.resolve(null);

    assertEquals(DEFAULT_PATH, resolved);
  }

  @Test
  void resolveReturnsDefaultWhenInputIsEmpty() {
    DefaultPathResolver resolver = new DefaultPathResolver();

    String resolved = resolver.resolve("");

    assertEquals(DEFAULT_PATH, resolved);
  }

  @Test
  void resolveReturnsDefaultWhenInputIsWhitespaceOnly() {
    DefaultPathResolver resolver = new DefaultPathResolver();

    String resolved = resolver.resolve("   ");

    assertEquals(DEFAULT_PATH, resolved);
  }

  @Test
  void resolveReturnsTrimmedUserProvidedPath() {
    DefaultPathResolver resolver = new DefaultPathResolver();

    String resolved = resolver.resolve("  .pipelines/release.yaml  ");

    assertEquals(".pipelines/release.yaml", resolved);
  }
}