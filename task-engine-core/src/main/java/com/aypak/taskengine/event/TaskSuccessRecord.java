package com.aypak.taskengine.event;

import lombok.Getter;

/**
 * 任务成功事件记录。
 * Task success event record.
 */
@Getter
public class TaskSuccessRecord extends TaskEventRecord {

    private final long executionTimeMs;

    public TaskSuccessRecord(String taskName, long executionTimeMs) {
        super(System.currentTimeMillis(), taskName);
        this.executionTimeMs = executionTimeMs;
    }

    @Override
    public String getEventType() {
        return "TASK_SUCCESS";
    }
}
