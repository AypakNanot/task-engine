package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.ITaskProcessor;
import com.aypak.taskengine.core.TaskConfig;
import com.aypak.taskengine.monitor.TaskMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for task processors.
 * Uses ConcurrentHashMap for concurrent registration and lookup.
 */
@Slf4j
public class TaskRegistry {

    private final Map<String, TaskRegistration<?>> registrations = new ConcurrentHashMap<>();
    private final Map<String, TaskMetrics> metrics = new ConcurrentHashMap<>();

    /**
     * Register a task processor with its configuration and pre-created metrics.
     *
     * @param config    task configuration
     * @param processor task processor implementation
     * @param metrics   pre-created metrics (if null, new metrics will be created)
     * @throws IllegalArgumentException if registration already exists
     */
    public <T> void registerWithMetrics(TaskConfig config, ITaskProcessor<T> processor, TaskMetrics metrics) {
        String taskName = config.getTaskName();

        if (registrations.containsKey(taskName)) {
            throw new IllegalArgumentException("Task already registered: " + taskName);
        }

        TaskRegistration<T> registration = new TaskRegistration<>(config, processor);
        registrations.put(taskName, registration);

        if (metrics == null) {
            metrics = new TaskMetrics(taskName, config.getTaskType());
        }
        this.metrics.put(taskName, metrics);

        log.info("Registered task: {} [type={}, priority={}]",
                taskName, config.getTaskType(), config.getPriority());
    }

    /**
     * Register a task processor with its configuration.
     *
     * @param config    task configuration
     * @param processor task processor implementation
     * @throws IllegalArgumentException if registration already exists
     */
    public <T> void register(TaskConfig config, ITaskProcessor<T> processor) {
        registerWithMetrics(config, processor, null);
    }

    /**
     * Get task registration by name.
     *
     * @param taskName task name
     * @return registration or null if not found
     */
    public TaskRegistration<?> getRegistration(String taskName) {
        return registrations.get(taskName);
    }

    /**
     * Get task metrics by name.
     *
     * @param taskName task name
     * @return metrics or null if not found
     */
    public TaskMetrics getMetrics(String taskName) {
        return metrics.get(taskName);
    }

    /**
     * Get all registrations.
     */
    public Collection<TaskRegistration<?>> getAllRegistrations() {
        return Collections.unmodifiableCollection(registrations.values());
    }

    /**
     * Get all metrics.
     */
    public Map<String, TaskMetrics> getAllMetrics() {
        return Collections.unmodifiableMap(metrics);
    }

    /**
     * Check if task is registered.
     */
    public boolean isRegistered(String taskName) {
        return registrations.containsKey(taskName);
    }

    /**
     * Deregister a task (for cleanup).
     */
    public void deregister(String taskName) {
        TaskRegistration<?> registration = registrations.remove(taskName);
        metrics.remove(taskName);

        if (registration != null) {
            log.info("Deregistered task: {}", taskName);
        }
    }

    /**
     * Get total registered task count.
     */
    public int getTaskCount() {
        return registrations.size();
    }

    /**
     * Task registration container.
     */
    @lombok.Getter
    public static class TaskRegistration<T> {
        private final TaskConfig config;
        private final ITaskProcessor<T> processor;

        public TaskRegistration(TaskConfig config, ITaskProcessor<T> processor) {
            this.config = config;
            this.processor = processor;
        }
    }
}