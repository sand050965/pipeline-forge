package edu.northeastern.cs7580.cicd.pipeline.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineService;
import edu.northeastern.cs7580.cicd.pipelinelib.api.PipelineServiceFactory;
import org.junit.jupiter.api.Test;


class PipelineServiceFactoryTest {

  @Test
  void createShouldReturnNonNullService() {
    PipelineService service = PipelineServiceFactory.create();
    assertNotNull(service);
  }

  @Test
  void createShouldReturnNewInstanceEachTime() {
    PipelineService service1 = PipelineServiceFactory.create();
    PipelineService service2 = PipelineServiceFactory.create();

    assertNotSame(service1, service2);
  }

}