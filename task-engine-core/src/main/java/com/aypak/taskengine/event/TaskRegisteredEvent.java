package com.aypak.taskengine.event;

import lombok.Getter;

/**
 * 任务注册事件。
 * Event fired when a task is registered.
 *
 * @param <T> 任务负载类型 / task payload type
 */
@Getter
public class TaskRegisteredEvent<T> extends TaskEvent<T> {

    private final String taskType;

    /**
     * 创建任务注册事件。
     * Create task registered event.
     *
     * @param source    事件源 / event source
     * @param taskName  任务名称 / task name
     * @param taskType  任务类型 / task type
     * @param payload   任务负载 / task payload
     */
    public TaskRegisteredEvent(Object source, String taskName, String taskType, T payload) {
        super(source, taskName, payload);
        this.taskType = taskType;
    }
}
