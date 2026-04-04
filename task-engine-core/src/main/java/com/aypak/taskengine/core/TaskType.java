package com.aypak.taskengine.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 任务类型枚举，基于资源特性的隔离策略。
 * 每种类型都有专用的线程池，针对特定资源模式优化。
 * Task type enumeration for resource-based isolation strategy.
 * Each type has dedicated thread pool optimized for specific resource patterns.
 */
@Getter
@RequiredArgsConstructor
public enum TaskType {

    /**
     * CPU 密集型任务。
     * 计算密集，少 IO 操作：数据加密、图像压缩、复杂计算。
     * 线程池：core=CPU 核数，max=CPU*2，小队列。
     * CPU-bound tasks: compute-intensive with minimal I/O.
     * Pool: core=CPUs, max=CPUs*2, small queue.
     */
    CPU_BOUND("CPU"),

    /**
     * IO 密集型任务。
     * 等待密集：网络请求、文件读写、数据库操作。
     * 线程池：core=16, max=64，大队列吸收等待时间。
     * IO-bound tasks: I/O-intensive with significant wait time.
     * Pool: core=16, max=64, large queue.
     */
    IO_BOUND("IO"),

    /**
     * 混合型任务。
     * 同时涉及计算和 IO 但无法拆分的业务逻辑。
     * 线程池：core=8, max=16，平衡配置。
     * Hybrid tasks: mixed computation and I/O that cannot be split.
     * Pool: core=8, max=16, balanced configuration.
     */
    HYBRID("HYBRID"),

    /**
     * 定时/调度任务。
     * 由 Cron 表达式或固定速率/延迟触发。
     * 线程池：ThreadPoolTaskScheduler，core=4, max=4，无队列。
     * Scheduled tasks: triggered by cron expression or fixed rate/delay.
     * Pool: ThreadPoolTaskScheduler, core=4, max=4, no queue.
     */
    SCHEDULED("CRON"),

    /**
     * 批量处理任务。
     * 大批量数据处理：数据同步、批量导入导出。
     * 线程池：core=2, max=4，大队列缓冲。
     * Batch tasks: large volume data processing.
     * Pool: core=2, max=4, large buffer queue.
     */
    BATCH("BATCH");

    /**
     * Thread name prefix for this task type.
     * Format: {prefix}-{taskName}-{id}
     */
    private final String prefix;
}