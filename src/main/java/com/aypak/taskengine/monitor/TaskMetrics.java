package com.aypak.taskengine.monitor;

import com.aypak.taskengine.core.TaskType;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * High-performance thread-safe task metrics container.
 * Uses LongAdder for high-contention counters and AtomicLong for single-writer values.
 */
@Getter
public class TaskMetrics {

    private final String taskName;
    private final TaskType taskType;

    // High-contention counters - use LongAdder for better performance
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder executionsInWindow = new LongAdder();

    // Total execution time for average calculation
    private final LongAdder totalExecutionTimeMs = new LongAdder();

    // QPS tracking (sliding window)
    private final AtomicLong windowStartTime = new AtomicLong(System.currentTimeMillis());

    // EWMA for average response time - single writer per task, AtomicLong is fine
    private final AtomicLong ewmaResponseTime = new AtomicLong(0);
    private static final double EWMA_ALPHA = 0.3;

    // Pool state - relatively low contention
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
     * Record successful execution - optimized for high throughput.
     *
     * @param executionTimeMs execution duration in milliseconds
     */
    public void recordSuccess(long executionTimeMs) {
        successCount.increment();
        totalExecutionTimeMs.add(executionTimeMs);
        executionsInWindow.increment();
        updateEwma(executionTimeMs);
    }

    /**
     * Record failed execution.
     */
    public void recordFailure() {
        failureCount.increment();
        executionsInWindow.increment();
    }

    /**
     * Update EWMA for average response time - lock-free.
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
            long executions = executionsInWindow.sumThenReset();
            windowStartTime.set(now);
            if (windowDuration > 0) {
                return executions * 1000.0 / windowDuration;
            }
            return 0;
        }

        // Return current window QPS
        if (windowDuration > 0) {
            return executionsInWindow.sum() * 1000.0 / windowDuration;
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
            peakThreads.compareAndSet(peak, current);
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
        successCount.reset();
        failureCount.reset();
        totalExecutionTimeMs.reset();
        executionsInWindow.reset();
        windowStartTime.set(System.currentTimeMillis());
        ewmaResponseTime.set(0);
        peakThreads.set(activeThreads.get());
    }

    /**
     * Get total task count (success + failure).
     */
    public long getTotalCount() {
        return successCount.sum() + failureCount.sum();
    }

    // Getter methods that return long for LongAdder
    public LongAdder getSuccessCount() { return successCount; }
    public LongAdder getFailureCount() { return failureCount; }
}