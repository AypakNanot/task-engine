package com.aypak.taskengine.event;

import lombok.Getter;

/**
 * 任务执行成功事件。
 * Event fired when a task execution succeeds.
 *
 * @param <T> 任务负载类型 / task payload type
 */
@Getter
public class TaskSuccessEvent<T> extends TaskEvent<T> {

    private final long executionTimeMs;

    /**
     * 创建任务成功事件。
     * Create task success event.
     *
     * @param source           事件源 / event source
     * @param taskName         任务名称 / task name
     * @param payload          任务负载 / task payload
     * @param executionTimeMs  执行时间（毫秒） / execution time in ms
     */
    public TaskSuccessEvent(Object source, String taskName, T payload,
            long executionTimeMs) {
        super(source, taskName, payload);
        this.executionTimeMs = executionTimeMs;
    }
}
