package com.aypak.taskengine.core;

/**
 * 拒绝策略枚举，用于处理队列满时的任务拒绝。
 * Rejection policy enumeration for handling task rejection when queue is full.
 */
public enum RejectionPolicy {

    /**
     * 中止并告警 - 丢弃任务，记录错误，增加失败指标。
     * 适用于：必须通知数据丢失的关键告警任务。
     * Abort with alert - drop task, log error, increment failure metric.
     * Use for: critical alert tasks where data loss must be notified.
     */
    ABORT_WITH_ALERT,

    /**
     * 调用者运行 - 在调用者线程中执行任务。
     * 适用于：调用者可以处理执行的清理任务。
     * Caller runs - execute task in caller thread.
     * Use for: cleanup tasks where caller can handle execution.
     */
    CALLER_RUNS,

    /**
     * 阻塞等待 - 阻塞调用者直到队列空间可用。
     * 适用于：需要保证交付的场景。
     * Block wait - block caller until queue space available.
     * Use for: guaranteed delivery scenarios.
     */
    BLOCK_WAIT,

    /**
     * 丢弃最旧 - 移除队列中最旧的任务，添加新任务。
     * 适用于：非关键数据，优先考虑新鲜度。
     * Discard oldest - remove oldest queued task, add new one.
     * Use for: non-critical data where freshness is preferred.
     */
    DISCARD_OLDEST
}