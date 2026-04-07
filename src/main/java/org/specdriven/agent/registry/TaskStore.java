package org.specdriven.agent.registry;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for task CRUD and queries.
 */
public interface TaskStore {

    /**
     * Saves a task. If the task has no ID, a UUID is generated.
     *
     * @return the task ID
     */
    String save(Task task);

    /**
     * Loads a task by ID.
     */
    Optional<Task> load(String taskId);

    /**
     * Updates a task's status with transition validation.
     *
     * @return the updated task
     * @throws java.util.NoSuchElementException if the task does not exist
     */
    Task update(String taskId, TaskStatus newStatus);

    /**
     * Updates a task's title and description.
     *
     * @return the updated task
     * @throws java.util.NoSuchElementException if the task does not exist
     */
    Task update(String taskId, String title, String description);

    /**
     * Transitions a task to DELETED status.
     *
     * @throws java.util.NoSuchElementException if the task does not exist
     */
    void delete(String taskId);

    /**
     * Returns all non-deleted tasks ordered by createdAt ascending.
     */
    List<Task> list();

    /**
     * Returns non-deleted tasks matching the given status.
     */
    List<Task> queryByStatus(TaskStatus status);

    /**
     * Returns non-deleted tasks matching the given owner.
     */
    List<Task> queryByOwner(String owner);
}
