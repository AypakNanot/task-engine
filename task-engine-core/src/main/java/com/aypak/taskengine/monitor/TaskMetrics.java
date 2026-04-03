package com.aypak.taskengine.monitor;

import com.aypak.taskengine.core.TaskType;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 高性能线程安全的任务指标容器。
 * 使用 LongAdder 处理高争用计数器，使用 AtomicLong 处理单写入者值。
 * High-performance thread-safe task metrics container.
 * Uses LongAdder for high-contention counters and AtomicLong for single-writer values.
 */
@Getter
public class TaskMetrics {

    private final String taskName;
    private final TaskType taskType;

    // 高争用计数器 - 使用 LongAdder 以获得更好的性能
    // High-contention counters - use LongAdder for better performance
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder executionsInWindow = new LongAdder();

    // 平均执行时间计算的总执行时间
    // Total execution time for average calculation
    private final LongAdder totalExecutionTimeMs = new LongAdder();

    // QPS 跟踪（滑动窗口）
    // QPS tracking (sliding window)
    private final AtomicLong windowStartTime = new AtomicLong(System.currentTimeMillis());

    // 平均响应时间的 EWMA - 每个任务单写入者，AtomicLong 足够
    // EWMA for average response time - single writer per task, AtomicLong is fine
    private final AtomicLong ewmaResponseTime = new AtomicLong(0);
    private static final double EWMA_ALPHA = 0.3;

    // 线程池状态 - 相对较低争用
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
     * 记录成功执行 - 针对高吞吐量优化。
     * Record successful execution - optimized for high throughput.
     *
     * @param executionTimeMs 执行持续时间（毫秒）/ execution duration in milliseconds
     */
    public void recordSuccess(long executionTimeMs) {
        successCount.increment();
        totalExecutionTimeMs.add(executionTimeMs);
        executionsInWindow.increment();
        updateEwma(executionTimeMs);
    }

    /**
     * 记录失败执行。
     * Record failed execution.
     */
    public void recordFailure() {
        failureCount.increment();
        executionsInWindow.increment();
    }

    /**
     * 更新 EWMA 平均响应时间 - 无锁操作。
     * Update EWMA for average response time - lock-free.
     */
    private void updateEwma(long newValue) {
        long current = ewmaResponseTime.get();
        long updated = (long) (EWMA_ALPHA * newValue + (1 - EWMA_ALPHA) * current);
        ewmaResponseTime.set(updated);
    }

    /**
     * 基于滑动窗口计算当前 QPS。
     * Calculate current QPS based on sliding window.
     *
     * @param windowSizeMs 窗口大小（毫秒）/ window size in milliseconds
     * @return 当前 QPS / current QPS
     */
    public double calculateQps(long windowSizeMs) {
        long now = System.currentTimeMillis();
        long windowStart = windowStartTime.get();
        long windowDuration = now - windowStart;

        if (windowDuration >= windowSizeMs) {
            // 重置窗口 / Reset window
            long executions = executionsInWindow.sumThenReset();
            windowStartTime.set(now);
            if (windowDuration > 0) {
                return executions * 1000.0 / windowDuration;
            }
            return 0;
        }

        // 返回当前窗口的 QPS / Return current window QPS
        if (windowDuration > 0) {
            return executionsInWindow.sum() * 1000.0 / windowDuration;
        }
        return 0;
    }

    /**
     * 获取平均响应时间（EWMA）。
     * Get average response time (EWMA).
     */
    public long getAvgResponseTime() {
        return ewmaResponseTime.get();
    }

    /**
     * 获取当前 QPS（只读访问）。
     * Get current QPS (read-only access).
     *
     * @return 当前 QPS / current QPS
     */
    public double getQps() {
        return calculateQps(60000);
    }

    /**
     * 更新队列深度。
     * Update queue depth.
     */
    public void setQueueDepth(int depth) {
        queueDepth.set(depth);
    }

    /**
     * 更新活动线程数。
     * Update active thread count.
     */
    public void setActiveThreads(int count) {
        activeThreads.set(count);
        updatePeak(count);
    }

    /**
     * 更新峰值线程数。
     * Update peak thread count.
     */
    private void updatePeak(int current) {
        int peak = peakThreads.get();
        if (current > peak) {
            peakThreads.compareAndSet(peak, current);
        }
    }

    /**
     * 设置线程池大小配置。
     * Set pool size configuration.
     */
    public void setPoolSizes(int original, int current) {
        originalMaxPoolSize.set(original);
        currentMaxPoolSize.set(current);
    }

    /**
     * 更新当前最大线程池大小（用于动态扩展）。
     * Update current max pool size (for scaling).
     */
    public void updateCurrentMaxPoolSize(int newSize) {
        currentMaxPoolSize.set(newSize);
        updatePeak(newSize);
    }

    /**
     * 重置所有指标为零。
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
     * 获取总任务数（成功 + 失败）。
     * Get total task count (success + failure).
     */
    public long getTotalCount() {
        return successCount.sum() + failureCount.sum();
    }

    // Getter methods that return long for LongAdder
    public LongAdder getSuccessCount() { return successCount; }
    public LongAdder getFailureCount() { return failureCount; }
}