package com.aypak.taskengine.monitor;

import com.aypak.taskengine.core.TaskType;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe task metrics container.
 * All counters use atomic operations for concurrent updates.
 */
@Getter
public class TaskMetrics {

    private final String taskName;
    private final TaskType taskType;

    // Execution counters
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong totalExecutionTimeMs = new AtomicLong(0);

    // QPS tracking (sliding window)
    private final AtomicLong executionsInWindow = new AtomicLong(0);
    private final AtomicLong windowStartTime = new AtomicLong(System.currentTimeMillis());

    // EWMA for average response time
    private final AtomicLong ewmaResponseTime = new AtomicLong(0);
    private static final double EWMA_ALPHA = 0.3;

    // Pool state
    private final AtomicInteger queueDepth = new AtomicInteger(0);
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private final AtomicInteger peakThreads = new AtomicInteger(0);
    private final AtomicInteger currentMaxPoolSize = new AtomicInteger(0);
    private final AtomicInteger originalMaxPoolSize = new AtomicInteger(0);

    public TaskMetrics(String taskName, TaskType taskType) {
        this.taskName = taskName;
        this.taskType = taskType;
    }

    /**
     * Record successful execution.
     *
     * @param executionTimeMs execution duration in milliseconds
     */
    public void recordSuccess(long executionTimeMs) {
        successCount.incrementAndGet();
        totalExecutionTimeMs.addAndGet(executionTimeMs);
        executionsInWindow.incrementAndGet();
        updateEwma(executionTimeMs);
    }

    /**
     * Record failed execution.
     */
    public void recordFailure() {
        failureCount.incrementAndGet();
        executionsInWindow.incrementAndGet();
    }

    /**
     * Update EWMA for average response time.
     */
    private void updateEwma(long newValue) {
        long current = ewmaResponseTime.get();
        long updated = (long) (EWMA_ALPHA * newValue + (1 - EWMA_ALPHA) * current);
        ewmaResponseTime.set(updated);
    }

    /**
     * Calculate current QPS based on sliding window.
     *
     * @param windowSizeMs window size in milliseconds
     * @return current QPS
     */
    public double calculateQps(long windowSizeMs) {
        long now = System.currentTimeMillis();
        long windowStart = windowStartTime.get();
        long windowDuration = now - windowStart;

        if (windowDuration >= windowSizeMs) {
            // Reset window
            long executions = executionsInWindow.getAndSet(0);
            windowStartTime.set(now);
            if (windowDuration > 0) {
                return executions * 1000.0 / windowDuration;
            }
            return 0;
        }

        // Return current window QPS
        if (windowDuration > 0) {
            return executionsInWindow.get() * 1000.0 / windowDuration;
        }
        return 0;
    }

    /**
     * Get average response time (EWMA).
     */
    public long getAvgResponseTime() {
        return ewmaResponseTime.get();
    }

    /**
     * Update queue depth.
     */
    public void setQueueDepth(int depth) {
        queueDepth.set(depth);
    }

    /**
     * Update active thread count.
     */
    public void setActiveThreads(int count) {
        activeThreads.set(count);
        updatePeak(count);
    }

    /**
     * Update peak thread count.
     */
    private void updatePeak(int current) {
        int peak = peakThreads.get();
        if (current > peak) {
            peakThreads.set(current);
        }
    }

    /**
     * Set pool size configuration.
     */
    public void setPoolSizes(int original, int current) {
        originalMaxPoolSize.set(original);
        currentMaxPoolSize.set(current);
    }

    /**
     * Update current max pool size (for scaling).
     */
    public void updateCurrentMaxPoolSize(int newSize) {
        currentMaxPoolSize.set(newSize);
        updatePeak(newSize);
    }

    /**
     * Reset all metrics to zero.
     */
    public void reset() {
        successCount.set(0);
        failureCount.set(0);
        totalExecutionTimeMs.set(0);
        executionsInWindow.set(0);
        windowStartTime.set(System.currentTimeMillis());
        ewmaResponseTime.set(0);
        peakThreads.set(activeThreads.get()); // Peak reset to current
    }

    /**
     * Get total task count (success + failure).
     */
    public long getTotalCount() {
        return successCount.get() + failureCount.get();
    }
}