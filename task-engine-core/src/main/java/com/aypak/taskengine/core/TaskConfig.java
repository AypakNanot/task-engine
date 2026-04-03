package com.aypak.taskengine.core;

import lombok.Builder;
import lombok.Getter;

/**
 * 任务配置，用于注册。
 * 包含线程池创建和任务处理所需的所有参数。
 * Task configuration for registration.
 * Contains all parameters needed for thread pool creation and task handling.
 */
@Getter
@Builder
public class TaskConfig {

    /**
     * 唯一任务标识符。
     * 必填 - 用于线程命名和注册表键。
     * Unique task identifier.
     * Required - used for thread naming and registry key.
     */
    private final String taskName;

    /**
     * 用于池隔离的任务类型。
     * 必填 - 确定使用哪种池类型。
     * Task type for pool isolation.
     * Required - determines which pool type to use.
     */
    private final TaskType taskType;

    /**
     * 用于调度的任务优先级。
     * 必填 - 影响调度决策。
     * Task priority for scheduling.
     * Required - affects scheduling decisions.
     */
    private final TaskPriority priority;

    /**
     * 核心线程池大小覆盖。
     * 可选 - 如果未指定则使用类型默认值。
     * Core pool size override.
     * Optional - uses type defaults if not specified.
     */
    private final Integer corePoolSize;

    /**
     * 最大线程池大小覆盖。
     * 可选 - 如果未指定则使用类型默认值。
     * Maximum pool size override.
     * Optional - uses type defaults if not specified.
     */
    private final Integer maxPoolSize;

    /**
     * HIGH_FREQ 类型的队列容量。
     * 可选 - 如果未指定则使用类型默认值。
     * Queue capacity for HIGH_FREQ type.
     * Optional - uses type defaults if not specified.
     */
    private final Integer queueCapacity;

    /**
     * 队列满时的拒绝策略。
     * 可选 - 默认为 ABORT_WITH_ALERT。
     * Rejection policy when queue is full.
     * Optional - defaults to ABORT_WITH_ALERT.
     */
    private final RejectionPolicy rejectionPolicy;

    /**
     * 队列告警阈值百分比（0-100）。
     * 可选 - 默认为 80%。
     * Queue alert threshold percentage (0-100).
     * Optional - defaults to 80%.
     */
    private final Integer queueAlertThreshold;

    /**
     * CRON 任务的 Cron 表达式。
     * For CRON tasks: cron expression.
     */
    private final String cronExpression;

    /**
     * CRON 任务的固定速率（毫秒）。
     * For CRON tasks: fixed rate in milliseconds.
     */
    private final Long fixedRate;

    /**
     * CRON 任务的固定延迟（毫秒）。
     * For CRON tasks: fixed delay in milliseconds.
     */
    private final Long fixedDelay;

    /**
     * 验证必填字段。
     * 如果必填字段缺失则抛出 IllegalArgumentException。
     * Validate required fields.
     *
     * @throws IllegalArgumentException if required fields are missing
     */
    public void validate() {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName is required");
        }
        if (taskType == null) {
            throw new IllegalArgumentException("taskType is required");
        }
        if (priority == null) {
            throw new IllegalArgumentException("priority is required");
        }
        if (queueAlertThreshold != null && (queueAlertThreshold < 0 || queueAlertThreshold > 100)) {
            throw new IllegalArgumentException("queueAlertThreshold must be between 0 and 100");
        }
        if (taskType == TaskType.CRON && cronExpression == null && fixedRate == null && fixedDelay == null) {
            throw new IllegalArgumentException("CRON tasks require cronExpression, fixedRate, or fixedDelay");
        }
    }
}