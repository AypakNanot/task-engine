package com.aypak.taskengine.executor;

import lombok.extern.slf4j.Slf4j;

/**
 * Task execution logger for structured logging.
 */
@Slf4j
public class TaskExecutionLogger {

    /**
     * Log task execution start.
     *
     * @param taskName   task name
     * @param threadName current thread name
     * @param traceId    trace ID (if available)
     */
    public void logStart(String taskName, String threadName, String traceId) {
        if (traceId != null) {
            log.info("[{}] Starting task execution, TraceId: {}", threadName, traceId);
        } else {
            log.info("[{}] Starting task execution", threadName);
        }
    }

    /**
     * Log task execution completion.
     *
     * @param taskName     task name
     * @param threadName   current thread name
     * @param executionMs  execution time in milliseconds
     * @param queueDepth   current queue depth
     * @param queueCapacity queue capacity
     * @param qps          current QPS
     */
    public void logSuccess(String taskName, String threadName, long executionMs,
                           int queueDepth, int queueCapacity, double qps) {
        log.info("[{}] Task executed in {}ms, Queue: {} / {}, QPS: {}",
                threadName, executionMs, queueDepth, queueCapacity, String.format("%.1f", qps));
    }

    /**
     * Log task execution failure.
     *
     * @param taskName   task name
     * @param threadName current thread name
     * @param error      error message
     * @param traceId    trace ID (if available)
     */
    public void logFailure(String taskName, String threadName, String error, String traceId) {
        if (traceId != null) {
            log.error("[{}] Task failed: {}, TraceId: {}", threadName, error, traceId);
        } else {
            log.error("[{}] Task failed: {}", threadName, error);
        }
    }

    /**
     * Log queue alert.
     *
     * @param taskName   task name
     * @param queueDepth current queue depth
     * @param capacity   queue capacity
     * @param threshold  alert threshold percentage
     */
    public void logQueueAlert(String taskName, int queueDepth, int capacity, int threshold) {
        double utilization = (queueDepth * 100.0) / capacity;
        log.warn("[{}] Queue depth: {} / {} ({}% > threshold {}%) ALERT",
                taskName, queueDepth, capacity, String.format("%.1f", utilization), threshold);
    }

    /**
     * Log scaling event.
     *
     * @param taskName task name
     * @param oldSize  previous max pool size
     * @param newSize  new max pool size
     * @param reason   scaling reason
     */
    public void logScaling(String taskName, int oldSize, int newSize, String reason) {
        log.info("[{}] Pool scaling: {} -> {} (reason: {})", taskName, oldSize, newSize, reason);
    }
}