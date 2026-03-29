package com.aypak.taskengine.executor;

import lombok.extern.slf4j.Slf4j;

/**
 * 用于结构化日志的任务执行记录器。
 * Task execution logger for structured logging.
 */
@Slf4j
public class TaskExecutionLogger {

    /**
     * 记录任务执行开始。
     * Log task execution start.
     *
     * @param taskName   任务名称 / task name
     * @param threadName 当前线程名称 / current thread name
     * @param traceId    追踪 ID（如果可用）/ trace ID (if available)
     */
    public void logStart(String taskName, String threadName, String traceId) {
        if (traceId != null) {
            log.info("[{}] Starting task execution, TraceId: {}", threadName, traceId);
        } else {
            log.info("[{}] Starting task execution", threadName);
        }
    }

    /**
     * 记录任务执行完成。
     * Log task execution completion.
     *
     * @param taskName      任务名称 / task name
     * @param threadName    当前线程名称 / current thread name
     * @param executionMs   执行时间（毫秒）/ execution time in milliseconds
     * @param queueDepth    当前队列深度 / current queue depth
     * @param queueCapacity 队列容量 / queue capacity
     * @param qps           当前 QPS / current QPS
     */
    public void logSuccess(String taskName, String threadName, long executionMs,
                           int queueDepth, int queueCapacity, double qps) {
        log.info("[{}] Task executed in {}ms, Queue: {} / {}, QPS: {}",
                threadName, executionMs, queueDepth, queueCapacity, String.format("%.1f", qps));
    }

    /**
     * 记录任务执行失败。
     * Log task execution failure.
     *
     * @param taskName   任务名称 / task name
     * @param threadName 当前线程名称 / current thread name
     * @param error      错误消息 / error message
     * @param traceId    追踪 ID（如果可用）/ trace ID (if available)
     */
    public void logFailure(String taskName, String threadName, String error, String traceId) {
        if (traceId != null) {
            log.error("[{}] Task failed: {}, TraceId: {}", threadName, error, traceId);
        } else {
            log.error("[{}] Task failed: {}", threadName, error);
        }
    }

    /**
     * 记录队列告警。
     * Log queue alert.
     *
     * @param taskName   任务名称 / task name
     * @param queueDepth 当前队列深度 / current queue depth
     * @param capacity   队列容量 / queue capacity
     * @param threshold  告警阈值百分比 / alert threshold percentage
     */
    public void logQueueAlert(String taskName, int queueDepth, int capacity, int threshold) {
        double utilization = (queueDepth * 100.0) / capacity;
        log.warn("[{}] Queue depth: {} / {} ({}% > threshold {}%) ALERT",
                taskName, queueDepth, capacity, String.format("%.1f", utilization), threshold);
    }

    /**
     * 记录扩展事件。
     * Log scaling event.
     *
     * @param taskName 任务名称 / task name
     * @param oldSize  之前的最大线程池大小 / previous max pool size
     * @param newSize  新的最大线程池大小 / new max pool size
     * @param reason   扩展原因 / scaling reason
     */
    public void logScaling(String taskName, int oldSize, int newSize, String reason) {
        log.info("[{}] Pool scaling: {} -> {} (reason: {})", taskName, oldSize, newSize, reason);
    }
}