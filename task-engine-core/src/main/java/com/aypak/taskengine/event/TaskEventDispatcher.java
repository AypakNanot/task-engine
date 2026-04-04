package com.aypak.taskengine.event;

import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;

/**
 * 任务事件调度器 - 统一管理任务事件的异步处理。
 * Task event dispatcher for unified async event processing.
 *
 * <p>使用独立的缓冲队列处理以下事件：</p>
 * <ul>
 *     <li>{@link TaskRegisteredRecord} - 任务注册事件（低频，仅日志）</li>
 *     <li>{@link TaskSuccessRecord} - 任务成功事件（高频，可丢弃）</li>
 *     <li>{@link TaskFailureRecord} - 任务失败事件（低频，重要告警）</li>
 * </ul>
 *
 * <p>设计特点：</p>
 * <ul>
 *     <li>成功事件使用独立队列，支持高吞吐量和有损丢弃</li>
 *     <li>失败事件直接日志记录，确保不丢失</li>
 *     <li>注册事件直接日志记录，仅启动时触发</li>
 * </ul>
 */
@Slf4j
public class TaskEventDispatcher {

    private static final String EVENT_LOG_NAME = "TaskEvents";

    private final AsyncEventQueue<TaskSuccessRecord> successQueue;
    private final int successQueueCapacity;

    /**
     * 创建任务事件调度器。
     * Create task event dispatcher.
     *
     * @param successQueueCapacity 成功事件队列容量（默认 10000）/ success event queue capacity
     * @param discardThreshold   丢弃阈值（队列深度超过此值开始丢弃，0=满时才丢弃）/ discard threshold
     */
    public TaskEventDispatcher(int successQueueCapacity, long discardThreshold) {
        this.successQueueCapacity = successQueueCapacity;

        this.successQueue = new AsyncEventQueue<>(
                "success",
                successQueueCapacity,
                discardThreshold,
                this::handleSuccessEvent
        );

        log.info("TaskEventDispatcher initialized with capacity={}, discardThreshold={}",
                successQueueCapacity, discardThreshold);
    }

    /**
     * 创建默认任务事件调度器。
     * Create default task event dispatcher.
     */
    public TaskEventDispatcher() {
        this(10000, 0);
    }

    /**
     * 发布任务注册事件（直接记录日志）。
     * Publish task registered event (direct logging).
     *
     * @param taskName 任务名称 / task name
     * @param taskType 任务类型 / task type
     */
    public void publishTaskRegistered(String taskName, String taskType) {
        org.slf4j.LoggerFactory.getLogger(EVENT_LOG_NAME).info("TASK_REGISTERED: name={}, type={}", taskName, taskType);
        log.debug("Task registered: {} [type={}]", taskName, taskType);
    }

    /**
     * 发布任务成功事件（异步队列）。
     * Publish task success event (async queue).
     *
     * @param taskName      任务名称 / task name
     * @param executionTime 执行时间（毫秒）/ execution time in ms
     * @return 是否提交成功 / whether submitted successfully
     */
    public boolean publishTaskSuccess(String taskName, long executionTime) {
        return successQueue.offer(new TaskSuccessRecord(taskName, executionTime));
    }

    /**
     * 发布任务失败事件（直接记录日志）。
     * Publish task failure event (direct logging).
     *
     * @param taskName 任务名称 / task name
     * @param error    异常 / exception
     */
    public void publishTaskFailure(String taskName, Throwable error) {
        String errorMessage = error != null ? error.getMessage() : "Unknown error";
        String errorType = error != null ? error.getClass().getSimpleName() : "Unknown";
        String stackTrace = error != null ? getStackTraceSummary(error) : "N/A";

        // 结构化日志：便于日志系统解析和告警
        org.slf4j.LoggerFactory.getLogger(EVENT_LOG_NAME).warn(
            "TASK_FAILURE: name={}, type={}, message={}, stackTrace={}",
            taskName, errorType, errorMessage, stackTrace);

        log.debug("[{}] Task failed: name={}, type={}, message={}",
            Thread.currentThread().getName(), taskName, errorType, errorMessage);
    }

    /**
     * 获取异常堆栈摘要（前 5 行）。
     * Get stack trace summary (first 5 lines).
     *
     * @param throwable 异常 / exception
     * @return 堆栈摘要 / stack trace summary
     */
    private String getStackTraceSummary(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        int limit = Math.min(stackTrace.length, 5);

        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append("; ");
            sb.append(stackTrace[i].toString());
        }

        if (stackTrace.length > 5) {
            sb.append("... (").append(stackTrace.length - 5).append(" more)");
        }

        return sb.toString();
    }

    private void handleSuccessEvent(TaskSuccessRecord record) {
        org.slf4j.LoggerFactory.getLogger(EVENT_LOG_NAME).debug("TASK_SUCCESS: name={}, executionTime={}ms",
                record.getTaskName(), record.getExecutionTimeMs());
    }

    /**
     * 获取成功事件队列深度。
     * Get success event queue depth.
     *
     * @return 队列深度 / queue depth
     */
    public int getSuccessQueueSize() {
        return successQueue.getQueueSize();
    }

    /**
     * 获取成功事件队列容量。
     * Get success event queue capacity.
     *
     * @return 队列容量 / queue capacity
     */
    public int getSuccessQueueCapacity() {
        return successQueueCapacity;
    }

    /**
     * 获取总成功事件数。
     * Get total success events count.
     *
     * @return 总事件数 / total events count
     */
    public long getTotalSuccessEvents() {
        return successQueue.getTotalEvents();
    }

    /**
     * 获取丢弃成功事件数。
     * Get discarded success events count.
     *
     * @return 丢弃事件数 / discarded events count
     */
    public long getDiscardedSuccessEvents() {
        return successQueue.getDiscardedEvents();
    }

    /**
     * 获取丢弃率。
     * Get discard rate.
     *
     * @return 丢弃率（0-100）/ discard rate (0-100)
     */
    public double getDiscardRate() {
        return successQueue.getDiscardRate();
    }

    /**
     * 停止事件调度器。
     * Stop event dispatcher.
     */
    @PreDestroy
    public void stop() {
        log.info("Stopping TaskEventDispatcher...");
        successQueue.stop();
        log.info("TaskEventDispatcher stopped");
    }
}
