package com.aypak.engine.task.event;

import org.springframework.context.ApplicationEvent;

/**
 * 任务执行事件的基类。
 * Base class for task execution events.
 *
 * @param <T> 任务负载类型 / task payload type
 */
public abstract class TaskEvent<T> extends ApplicationEvent {

    private final String taskName;
    private final T payload;

    /**
     * 创建任务事件。
     * Create task event.
     *
     * @param source    事件源（通常是 TaskEngine） / event source (usually TaskEngine)
     * @param taskName  任务名称 / task name
     * @param payload   任务负载 / task payload
     */
    public TaskEvent(Object source, String taskName, T payload) {
        super(source);
        this.taskName = taskName;
        this.payload = payload;
    }

    /**
     * 获取任务名称。
     * Get task name.
     *
     * @return 任务名称 / task name
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * 获取任务负载。
     * Get task payload.
     *
     * @return 任务负载 / task payload
     */
    public T getPayload() {
        return payload;
    }

    /**
     * 获取时间戳（从 Spring ApplicationEvent 继承）。
     * Get timestamp (inherited from Spring ApplicationEvent).
     *
     * @return 时间戳 / timestamp
     */
    public long getEventTimestamp() {
        return getTimestamp();
    }
}
