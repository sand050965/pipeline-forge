package edu.northeastern.cs7580.cicd.executionservice.repository;

import edu.northeastern.cs7580.cicd.executionservice.entity.StageRunEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for managing {@link StageRunEntity} persistence operations.
 *
 * <p>Extends {@link ReactiveCrudRepository} to provide non-blocking, reactive CRUD operations
 * for stage run records using Project Reactor's {@code Mono} and {@code Flux} return types.</p>
 *
 * <p>Spring Data automatically provides implementations for standard operations including:</p>
 * <ul>
 *   <li>{@code save(entity)} – insert or update a stage run record</li>
 *   <li>{@code findById(id)} – retrieve a stage run by its primary key</li>
 *   <li>{@code findAll()} – retrieve all stage run records</li>
 *   <li>{@code deleteById(id)} – delete a stage run by its primary key</li>
 *   <li>{@code count()} – return the total number of stage run records</li>
 * </ul>
 *
 * @see StageRunEntity
 * @see ReactiveCrudRepository
 */
@Repository
public interface StageRunRepository extends ReactiveCrudRepository<StageRunEntity, Long> {
}