package com.aypak.taskengine.event;

import lombok.Getter;

/**
 * 任务注册事件记录。
 * Task registered event record.
 */
@Getter
public class TaskRegisteredRecord extends TaskEventRecord {

    private final String taskType;

    public TaskRegisteredRecord(String taskName, String taskType) {
        super(System.currentTimeMillis(), taskName);
        this.taskType = taskType;
    }

    @Override
    public String getEventType() {
        return "TASK_REGISTERED";
    }
}
