package edu.northeastern.cs7580.cicd.executionservice.repository;

import edu.northeastern.cs7580.cicd.executionservice.entity.JobRunEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive repository for managing {@link JobRunEntity} persistence operations.
 *
 * <p>Extends {@link ReactiveCrudRepository} to provide non-blocking, reactive CRUD operations
 * for job run records using Project Reactor's {@code Mono} and {@code Flux} return types.</p>
 *
 * <p>Spring Data automatically provides implementations for standard operations including:</p>
 * <ul>
 *   <li>{@code save(entity)} – insert or update a job run record</li>
 *   <li>{@code findById(id)} – retrieve a job run by its primary key</li>
 *   <li>{@code findAll()} – retrieve all job run records</li>
 *   <li>{@code deleteById(id)} – delete a job run by its primary key</li>
 *   <li>{@code count()} – return the total number of job run records</li>
 * </ul>
 *
 * @see JobRunEntity
 * @see ReactiveCrudRepository
 */
@Repository
public interface JobRunRepository extends ReactiveCrudRepository<JobRunEntity, Long> {
}