package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.DynamicConfig;
import com.aypak.taskengine.core.ITaskProcessor;
import com.aypak.taskengine.core.TaskConfig;
import com.aypak.taskengine.monitor.TaskMetrics;

import java.util.Collection;
import java.util.Map;

/**
 * Task Engine interface for task management.
 */
public interface TaskEngine {

    /**
     * Register a task processor with configuration.
     *
     * @param config    task configuration
     * @param processor task processor implementation
     */
    <T> void register(TaskConfig config, ITaskProcessor<T> processor);

    /**
     * Execute a registered task with payload.
     *
     * @param taskName registered task name
     * @param payload  task payload/context
     */
    <T> void execute(String taskName, T payload);

    /**
     * Get all task statistics.
     *
     * @return map of task name to metrics
     */
    Map<String, TaskMetrics> getStats();

    /**
     * Get statistics for specific task.
     *
     * @param taskName task name
     * @return task metrics or null if not found
     */
    TaskMetrics getStats(String taskName);

    /**
     * Update task configuration dynamically.
     *
     * @param taskName task name
     * @param config   dynamic configuration to apply
     */
    void updateConfig(String taskName, DynamicConfig config);

    /**
     * Reset metrics for a specific task.
     *
     * @param taskName task name
     */
    void resetMetrics(String taskName);

    /**
     * Reset all task metrics.
     */
    void resetAllMetrics();

    /**
     * Get all task registrations.
     */
    Collection<TaskRegistry.TaskRegistration<?>> getAllRegistrations();
}