package edu.northeastern.cs7580.cicd.executionservice.repository;

import edu.northeastern.cs7580.cicd.executionservice.entity.PipelineRunEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for managing {@link PipelineRunEntity} persistence operations.
 *
 * <p>Extends {@link ReactiveCrudRepository} to provide non-blocking, reactive CRUD operations
 * for pipeline run records using Project Reactor's {@code Mono} and {@code Flux} return types.</p>
 *
 * <p>Spring Data automatically provides implementations for standard operations including:</p>
 * <ul>
 *   <li>{@code save(entity)} – insert or update a pipeline run record</li>
 *   <li>{@code findById(id)} – retrieve a pipeline run by its primary key</li>
 *   <li>{@code findAll()} – retrieve all pipeline run records</li>
 *   <li>{@code deleteById(id)} – delete a pipeline run by its primary key</li>
 *   <li>{@code count()} – return the total number of pipeline run records</li>
 * </ul>
 *
 * @see PipelineRunEntity
 * @see ReactiveCrudRepository
 */
@Repository
public interface PipelineRunRepository extends ReactiveCrudRepository<PipelineRunEntity, Long> {
}