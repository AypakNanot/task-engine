package com.aypak.taskengine.event;

import lombok.Getter;

/**
 * 任务失败事件记录。
 * Task failure event record.
 */
@Getter
public class TaskFailureRecord extends TaskEventRecord {

    private final String errorMessage;
    private final String errorType;

    public TaskFailureRecord(String taskName, Throwable error) {
        super(System.currentTimeMillis(), taskName);
        this.errorMessage = error != null ? error.getMessage() : "Unknown error";
        this.errorType = error != null ? error.getClass().getSimpleName() : "Unknown";
    }

    @Override
    public String getEventType() {
        return "TASK_FAILURE";
    }
}
