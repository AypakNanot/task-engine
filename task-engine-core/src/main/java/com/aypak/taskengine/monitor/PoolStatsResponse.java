package com.aypak.taskengine.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线程池统计响应。
 * Thread pool statistics response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoolStatsResponse {

    /**
     * 任务类型。
     * Task type.
     */
    private String taskType;

    /**
     * 核心线程数。
     * Core pool size.
     */
    private int corePoolSize;

    /**
     * 最大线程数。
     * Maximum pool size.
     */
    private int maxPoolSize;

    /**
     * 当前活跃线程数。
     * Number of active threads.
     */
    private int activeThreads;

    /**
     * 队列大小。
     * Current queue size.
     */
    private int queueSize;

    /**
     * 队列容量。
     * Queue capacity.
     */
    private int queueCapacity;

    /**
     * 队列使用率百分比。
     * Queue utilization percentage.
     */
    private double queueUtilization;

    /**
     * 线程池使用率百分比。
     * Thread pool utilization percentage.
     */
    private double threadUtilization;

    /**
     * 已完成任务总数。
     * Total number of completed tasks.
     */
    private long completedTaskCount;

    /**
     * 是否正在关闭。
     * Whether the pool is shutting down.
     */
    private boolean shuttingDown;

    /**
     * 是否已终止。
     * Whether the pool has terminated.
     */
    private boolean terminated;
}
