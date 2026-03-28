package com.aypak.taskengine.core;

/**
 * Task type enumeration for isolation strategy.
 * Each type has dedicated thread pool with specific characteristics.
 */
public enum TaskType {

    /**
     * Type 1: Initialization tasks.
     * System startup one-time execution, destroyed after completion.
     * Pool: core=1, max=CPU cores, short-lived.
     */
    INIT("INIT"),

    /**
     * Type 2: Periodic/cron tasks.
     * Cron expression or fixed rate/delay triggered.
     * Pool: ThreadPoolTaskScheduler.
     */
    CRON("CRON"),

    /**
     * Type 3: High-frequency streaming tasks.
     * High QPS (up to 10000/s) business logic.
     * Pool: core=CPU*2, max=CPU*4, bounded queue.
     */
    HIGH_FREQ("HIGH_FREQ"),

    /**
     * Type 4: Background maintenance tasks.
     * Low priority: log cleanup, data archival.
     * Pool: shared pool with core=2, max=4.
     */
    BACKGROUND("BACKGROUND");

    private final String prefix;

    TaskType(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Thread name prefix for this task type.
     * Format: {prefix}-{taskName}-{id}
     */
    public String getPrefix() {
        return prefix;
    }
}