package com.aypak.taskengine.core;

import lombok.Builder;
import lombok.Getter;

/**
 * Task configuration for registration.
 * Contains all parameters needed for thread pool creation and task handling.
 */
@Getter
@Builder
public class TaskConfig {

    /**
     * Unique task identifier.
     * Required - used for thread naming and registry key.
     */
    private final String taskName;

    /**
     * Task type for pool isolation.
     * Required - determines which pool type to use.
     */
    private final TaskType taskType;

    /**
     * Task priority for scheduling.
     * Required - affects scheduling decisions.
     */
    private final TaskPriority priority;

    /**
     * Core pool size override.
     * Optional - uses type defaults if not specified.
     */
    private final Integer corePoolSize;

    /**
     * Maximum pool size override.
     * Optional - uses type defaults if not specified.
     */
    private final Integer maxPoolSize;

    /**
     * Queue capacity for HIGH_FREQ type.
     * Optional - uses type defaults if not specified.
     */
    private final Integer queueCapacity;

    /**
     * Rejection policy when queue is full.
     * Optional - defaults to ABORT_WITH_ALERT.
     */
    private final RejectionPolicy rejectionPolicy;

    /**
     * Queue alert threshold percentage (0-100).
     * Optional - defaults to 80%.
     */
    private final Integer queueAlertThreshold;

    /**
     * For CRON tasks: cron expression.
     */
    private final String cronExpression;

    /**
     * For CRON tasks: fixed rate in milliseconds.
     */
    private final Long fixedRate;

    /**
     * For CRON tasks: fixed delay in milliseconds.
     */
    private final Long fixedDelay;

    /**
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