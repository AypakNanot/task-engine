package com.aypak.taskengine.core;

/**
 * 任务类型枚举，用于隔离策略。
 * 每种类型都有专用的线程池，具有特定的特性。
 * Task type enumeration for isolation strategy.
 * Each type has dedicated thread pool with specific characteristics.
 */
public enum TaskType {

    /**
     * 类型 1：初始化任务。
     * 系统启动一次性执行，完成后销毁。
     * 线程池：core=1, max=CPU 核心数，短生命周期的线程。
     * Type 1: Initialization tasks.
     * System startup one-time execution, destroyed after completion.
     * Pool: core=1, max=CPU cores, short-lived.
     */
    INIT("INIT"),

    /**
     * 类型 2：定期/定时任务。
     * 由 Cron 表达式或固定速率/延迟触发。
     * 线程池：ThreadPoolTaskScheduler。
     * Type 2: Periodic/cron tasks.
     * Cron expression or fixed rate/delay triggered.
     * Pool: ThreadPoolTaskScheduler.
     */
    CRON("CRON"),

    /**
     * 类型 3：高频流式任务。
     * 高 QPS（最高 10000/秒）业务逻辑处理。
     * 线程池：core=CPU*2, max=CPU*4，有界队列。
     * Type 3: High-frequency streaming tasks.
     * High QPS (up to 10000/s) business logic.
     * Pool: core=CPU*2, max=CPU*4, bounded queue.
     */
    HIGH_FREQ("HIGH_FREQ"),

    /**
     * 类型 4：后台维护任务。
     * 低优先级：日志清理、数据归档等。
     * 线程池：共享池，core=2, max=4。
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