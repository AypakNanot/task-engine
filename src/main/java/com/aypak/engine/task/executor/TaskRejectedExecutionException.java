package com.aypak.engine.task.executor;

import java.util.concurrent.RejectedExecutionException;

/**
 * 带任务上下文的自定义任务拒绝异常。
 * Custom exception for task rejection with task context.
 */
public class TaskRejectedExecutionException extends RejectedExecutionException {

    private final String taskName;
    private final int queueDepth;
    private final int activeThreads;

    /**
     * 构造函数。
     * Constructor.
     *
     * @param taskName      任务名称 / task name
     * @param queueDepth    队列深度 / queue depth
     * @param activeThreads 活动线程数 / active thread count
     */
    public TaskRejectedExecutionException(String taskName, int queueDepth, int activeThreads) {
        super(String.format("Task '%s' rejected: queue=%d, activeThreads=%d",
                taskName, queueDepth, activeThreads));
        this.taskName = taskName;
        this.queueDepth = queueDepth;
        this.activeThreads = activeThreads;
    }

    /**
     * 获取任务名称。
     * Get task name.
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * 获取队列深度。
     * Get queue depth.
     */
    public int getQueueDepth() {
        return queueDepth;
    }

    /**
     * 获取活动线程数。
     * Get active thread count.
     */
    public int getActiveThreads() {
        return activeThreads;
    }
}