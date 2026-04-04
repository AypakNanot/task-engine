package com.aypak.taskengine.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 任务执行事件的基类（已废弃）。
 * Base class for task execution events. DEPRECATED.
 *
 * @deprecated 使用 {@link TaskEventRecord} 替代，基于轻量级记录实现，无 Spring 依赖。
 * <p>
 * Deprecated: Use {@link TaskEventRecord} instead, which is a lightweight record without Spring dependency.
 *
 * @param <T> 任务负载类型 / task payload type
 */
@Deprecated(since = "2026-04-04", forRemoval = true)
@Getter
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
     * 获取时间戳（从 Spring ApplicationEvent 继承）。
     * Get timestamp (inherited from Spring ApplicationEvent).
     *
     * @return 时间戳 / timestamp
     */
    public long getEventTimestamp() {
        return getTimestamp();
    }
}
