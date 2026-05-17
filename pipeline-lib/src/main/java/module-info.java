/**
 * CI/CD Pipeline Library module.
 *
 * <p>This module provides pipeline validation and execution planning
 * capabilities for CI/CD systems.
 *
 * <p><b>Public API:</b>
 * <ul>
 *   <li>{@link edu.northeastern.cs7580.cicd.pipelinelib.api} - Main service interface</li>
 *   <li>{@link edu.northeastern.cs7580.cicd.pipelinelib.model} - Data models (DTOs)</li>
 *   <li>{@link edu.northeastern.cs7580.cicd.pipelinelib.exception} - Public exceptions</li>
 * </ul>
 *
 * <p><b>Internal packages</b> (not exported):
 * <ul>
 *   <li>edu.northeastern.cs7580.cicd.pipeline.internal.* - Implementation details</li>
 * </ul>
 */
module edu.northeastern.cs7580.cicd.pipelinelib {
  // ========== EXPORTS (Public API) ==========
  exports edu.northeastern.cs7580.cicd.pipelinelib.api;
  exports edu.northeastern.cs7580.cicd.pipelinelib.model;
  exports edu.northeastern.cs7580.cicd.pipelinelib.exception;

  // ========== REQUIRES (Dependencies) ==========
  // External libraries this module depends on
  requires static lombok;
  requires org.yaml.snakeyaml;
  requires org.slf4j;
  requires java.base;
}