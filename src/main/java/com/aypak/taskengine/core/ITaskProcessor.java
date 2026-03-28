package com.aypak.taskengine.core;

/**
 * Unified task processor interface.
 * All asynchronous tasks must implement this interface for standardized handling.
 *
 * @param <T> the context type for task execution
 */
public interface ITaskProcessor<T> {

    /**
     * Unique task identifier for registration and thread naming.
     *
     * @return task name (must be unique across registry)
     */
    String getTaskName();

    /**
     * Task type for pool isolation.
     *
     * @return one of INIT, CRON, HIGH_FREQ, BACKGROUND
     */
    TaskType getTaskType();

    /**
     * Task priority for scheduling decisions.
     *
     * @return one of HIGH, MEDIUM, LOW
     */
    TaskPriority getPriority();

    /**
     * Execute the task with given context.
     *
     * @param context task execution context
     */
    void process(T context);

    /**
     * Callback invoked when task execution fails.
     * Default implementation does nothing.
     *
     * @param context task execution context
     * @param error the exception that caused failure
     */
    default void onFailure(T context, Throwable error) {
        // Default: no action
    }

    /**
     * Callback invoked when task execution succeeds.
     * Default implementation does nothing.
     *
     * @param context task execution context
     */
    default void onSuccess(T context) {
        // Default: no action
    }
}