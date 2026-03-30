package com.aypak.engine.alarm.core;

/**
 * 拒绝策略枚举。
 * 当队列满时采用的处理策略。
 * Rejection policy enumeration.
 * Processing strategy when queue is full.
 */
public enum RejectPolicy {

    /**
     * 丢弃新数据。
     * 直接拒绝新提交的告警，不进入队列。
     * Drop new data.
     * Directly reject newly submitted alarms, do not enter queue.
     */
    DROP,

    /**
     * 丢弃最旧数据。
     * 移除队列中最旧的告警，为新告警腾出空间。
     * Drop oldest data.
     * Remove oldest alarm from queue to make space for new alarm.
     */
    DROP_OLDEST,

    /**
     * 阻塞等待。
     * 阻塞调用者线程直到队列有空间。
     * Block and wait.
     * Block caller thread until queue has space.
     */
    BLOCK,

    /**
     * 调用者线程处理。
     * 在提交告警的调用者线程中直接处理告警。
     * Caller runs.
     * Process alarm directly in caller thread that submitted the alarm.
     */
    CALLER_RUNS
}
