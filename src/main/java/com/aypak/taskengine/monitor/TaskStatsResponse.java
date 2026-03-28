package com.aypak.taskengine.monitor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST response for task statistics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatsResponse {

    /**
     * Task name.
     */
    private String taskName;

    /**
     * Task type (INIT, CRON, HIGH_FREQ, BACKGROUND).
     */
    private String taskType;

    /**
     * Current QPS (queries per second).
     */
    private double currentQps;

    /**
     * Average response time in milliseconds (EWMA).
     */
    private long avgResponseTime;

    /**
     * Total successful executions.
     */
    private long successCount;

    /**
     * Total failed executions.
     */
    private long failureCount;

    /**
     * Current queue depth.
     */
    private int queueDepth;

    /**
     * Currently active threads.
     */
    private int activeThreads;

    /**
     * Peak thread count.
     */
    private int peakThreads;

    /**
     * Current max pool size.
     */
    private int currentMaxPoolSize;

    /**
     * Queue capacity (calculated).
     */
    private int queueCapacity;

    /**
     * Queue utilization percentage.
     */
    private double queueUtilization;

    /**
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