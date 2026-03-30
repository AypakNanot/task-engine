package com.aypak.engine.flow.core;

/**
 * 拒绝策略枚举。
 * Rejection policy enum.
 *
 * <p>当 Worker 队列满时，决定如何处理新提交的事件。</p>
 * <p>Decides how to handle newly submitted events when Worker queue is full.</p>
 */
public enum RejectPolicy {

    /**
     * 丢弃新事件。
     * Drop new event.
     * 队列满时直接丢弃新事件，不阻塞调用者。
     * Drops new event when queue is full, does not block caller.
     */
    DROP,

    /**
     * 丢弃最旧事件。
     * Drop oldest event.
     * 队列满时丢弃队列中最旧的事件，插入新事件。
     * Drops oldest event in queue when full, inserts new event.
     * 注意：需要队列支持此操作。
     * Note: Queue must support this operation.
     */
    DROP_OLDEST,

    /**
     * 阻塞等待。
     * Block and wait.
     * 队列满时阻塞调用者线程，直到有空间或超时。
     * Blocks caller thread when queue is full until space available or timeout.
     */
    BLOCK,

    /**
     * 调用者运行。
     * Caller runs.
     * 队列满时在调用者线程中直接处理事件，不放入队列。
     * Processes event directly in caller thread when queue is full, does not enqueue.
     */
    CALLER_RUNS
}
