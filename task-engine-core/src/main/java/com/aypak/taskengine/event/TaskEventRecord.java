package com.aypak.taskengine.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 任务事件记录 - 轻量级事件数据载体。
 * Lightweight task event data carrier.
 */
@Getter
@RequiredArgsConstructor
public abstract class TaskEventRecord {

    protected final long timestamp;
    protected final String taskName;

    /**
     * 获取事件类型名称。
     * Get event type name.
     *
     * @return 事件类型 / event type
     */
    public abstract String getEventType();
}
