package com.aypak.taskengine.core;

/**
 * 统一任务处理器接口。
 * 所有异步任务必须实现此接口以便标准化处理。
 * Unified task processor interface.
 * All asynchronous tasks must implement this interface for standardized handling.
 *
 * @param <T> 任务执行的上下文类型 / the context type for task execution
 */
public interface ITaskProcessor<T> {

    /**
     * 唯一任务标识符，用于注册和线程命名。
     * 返回：任务名称（在注册表中必须唯一）。
     * Unique task identifier for registration and thread naming.
     *
     * @return task name (must be unique across registry)
     */
    String getTaskName();

    /**
     * 用于池隔离的任务类型。
     * 返回：INIT、CRON、HIGH_FREQ、BACKGROUND 之一。
     * Task type for pool isolation.
     *
     * @return one of INIT, CRON, HIGH_FREQ, BACKGROUND
     */
    TaskType getTaskType();

    /**
     * 使用给定上下文执行任务。
     * Execute the task with given context.
     *
     * @param context 任务执行上下文 / task execution context
     */
    void process(T context);

    /**
     * 任务执行失败时调用的回调。
     * 默认实现不执行任何操作。
     * Callback invoked when task execution fails.
     * Default implementation does nothing.
     *
     * @param context 任务执行上下文 / task execution context
     * @param error 导致失败的异常 / the exception that caused failure
     */
    default void onFailure(T context, Throwable error) {
        // Default: no action
    }

    /**
     * 任务执行成功时调用的回调。
     * 默认实现不执行任何操作。
     * Callback invoked when task execution succeeds.
     * Default implementation does nothing.
     *
     * @param context 任务执行上下文 / task execution context
     */
    default void onSuccess(T context) {
        // Default: no action
    }
}