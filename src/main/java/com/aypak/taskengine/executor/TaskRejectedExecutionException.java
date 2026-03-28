package com.aypak.taskengine.executor;

import java.util.concurrent.RejectedExecutionException;

/**
 * Custom exception for task rejection with task context.
 */
public class TaskRejectedExecutionException extends RejectedExecutionException {

    private final String taskName;
    private final int queueDepth;
    private final int activeThreads;

    public TaskRejectedExecutionException(String taskName, int queueDepth, int activeThreads) {
        super(String.format("Task '%s' rejected: queue=%d, activeThreads=%d",
                taskName, queueDepth, activeThreads));
        this.taskName = taskName;
        this.queueDepth = queueDepth;
        this.activeThreads = activeThreads;
    }

    public String getTaskName() {
        return taskName;
    }

    public int getQueueDepth() {
        return queueDepth;
    }

    public int getActiveThreads() {
        return activeThreads;
    }
}