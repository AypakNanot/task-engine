package com.aypak.taskengine.event;

import lombok.Getter;

/**
 * 任务执行失败事件。
 * Event fired when a task execution fails.
 *
 * @param <T> 任务负载类型 / task payload type
 */
@Getter
public class TaskFailureEvent<T> extends TaskEvent<T> {

    private final Throwable error;
    private final String errorMessage;

    /**
     * 创建任务失败事件。
     * Create task failure event.
     *
     * @param source       事件源 / event source
     * @param taskName     任务名称 / task name
     * @param payload      任务负载 / task payload
     * @param error        异常 / exception
     */
    public TaskFailureEvent(Object source, String taskName, T payload,
            Throwable error) {
        super(source, taskName, payload);
        this.error = error;
        this.errorMessage = error != null ? error.getMessage() : "Unknown error";
    }
}
