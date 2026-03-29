package com.aypak.taskengine.monitor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务统计信息的 REST 响应。
 * REST response for task statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatsResponse {

    /**
     * 任务名称。
     * Task name.
     */
    private String taskName;

    /**
     * 任务类型（INIT、CRON、HIGH_FREQ、BACKGROUND）。
     * Task type (INIT, CRON, HIGH_FREQ, BACKGROUND).
     */
    private String taskType;

    /**
     * 当前 QPS（每秒查询数）。
     * Current QPS (queries per second).
     */
    private double currentQps;

    /**
     * 平均响应时间（毫秒，EWMA）。
     * Average response time in milliseconds (EWMA).
     */
    private long avgResponseTime;

    /**
     * 成功执行总数。
     * Total successful executions.
     */
    private long successCount;

    /**
     * 失败执行总数。
     * Total failed executions.
     */
    private long failureCount;

    /**
     * 当前队列深度。
     * Current queue depth.
     */
    private int queueDepth;

    /**
     * 当前活动线程数。
     * Currently active threads.
     */
    private int activeThreads;

    /**
     * 峰值线程数。
     * Peak thread count.
     */
    private int peakThreads;

    /**
     * 当前最大线程池大小。
     * Current max pool size.
     */
    private int currentMaxPoolSize;

    /**
     * 队列容量（计算得出）。
     * Queue capacity (calculated).
     */
    private int queueCapacity;

    /**
     * 队列利用率百分比。
     * Queue utilization percentage.
     */
    private double queueUtilization;

    /**
     * 计算派生字段。
     * Calculate derived fields.
     */
    public void calculateDerivedFields(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        if (queueCapacity > 0) {
            this.queueUtilization = (queueDepth * 100.0) / queueCapacity;
        } else {
            this.queueUtilization = 0;
        }
    }
}