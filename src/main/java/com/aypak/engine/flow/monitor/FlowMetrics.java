package com.aypak.engine.flow.monitor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 流式指标 - 线程安全的指标容器。
 * Flow Metrics - thread-safe metrics container.
 *
 * <p>使用 LongAdder 处理高争用计数器，使用 AtomicLong 处理单写入者值。</p>
 * <p>Uses LongAdder for high-contention counters and AtomicLong for single-writer values.</p>
 */
public class FlowMetrics {

    /** 流名称 / Flow name */
    private final String flowName;

    // 高争用计数器 - 使用 LongAdder
    // High-contention counters - use LongAdder
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder droppedCount = new LongAdder();

    // 接收计数 / Receive count
    private final LongAdder receivedCount = new LongAdder();

    // QPS 跟踪（滑动窗口）
    // QPS tracking (sliding window)
    private final AtomicLong windowStartTime = new AtomicLong(System.currentTimeMillis());
    private final LongAdder executionsInWindow = new LongAdder();

    // EWMA 平均响应时间
    // EWMA for average response time
    private final AtomicLong ewmaResponseTime = new AtomicLong(0);
    private static final double EWMA_ALPHA = 0.3;

    // 队列深度 / Queue depth
    private final AtomicInteger queueDepth = new AtomicInteger(0);

    // 活跃 Worker 数 / Active Worker count
    private final AtomicInteger activeWorkers = new AtomicInteger(0);

    /**
     * 创建流指标。
     * Create flow metrics.
     *
     * @param flowName 流名称 / flow name
     */
    public FlowMetrics(String flowName) {
        this.flowName = flowName;
    }

    /**
     * 记录成功。
     * Record success.
     */
    public void recordSuccess() {
        successCount.increment();
        executionsInWindow.increment();
    }

    /**
     * 记录成功（带执行时间）。
     * Record success with execution time.
     *
     * @param executionTimeMs 执行时间（毫秒） / execution time in milliseconds
     */
    public void recordSuccess(long executionTimeMs) {
        recordSuccess();
        updateEwma(executionTimeMs);
    }

    /**
     * 记录失败。
     * Record failure.
     */
    public void recordFailure() {
        failureCount.increment();
        executionsInWindow.increment();
    }

    /**
     * 记录丢弃。
     * Record drop.
     */
    public void recordDrop() {
        droppedCount.increment();
    }

    /**
     * 记录接收。
     * Record receive.
     */
    public void recordReceive() {
        receivedCount.increment();
    }

    /**
     * 记录接收（批量）。
     * Record receive in batch.
     *
     * @param count 接收数量 / receive count
     */
    public void recordReceive(int count) {
        receivedCount.add(count);
    }

    /**
     * 更新 EWMA 平均响应时间。
     * Update EWMA for average response time.
     *
     * @param newValue 新值 / new value
     */
    private void updateEwma(long newValue) {
        long current = ewmaResponseTime.get();
        long updated = (long) (EWMA_ALPHA * newValue + (1 - EWMA_ALPHA) * current);
        ewmaResponseTime.set(updated);
    }

    /**
     * 计算 QPS。
     * Calculate QPS.
     *
     * @param windowSizeMs 窗口大小（毫秒） / window size in milliseconds
     * @return QPS
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
     * 获取当前 QPS。
     * Get current QPS.
     *
     * @return QPS
     */
    public double getQps() {
        return calculateQps(60000);
    }

    /**
     * 获取平均响应时间（EWMA）。
     * Get average response time (EWMA).
     *
     * @return 平均响应时间（毫秒） / average response time in milliseconds
     */
    public long getAvgResponseTime() {
        return ewmaResponseTime.get();
    }

    // ==================== Getters ====================

    /**
     * 获取流名称。
     * Get flow name.
     */
    public String getFlowName() {
        return flowName;
    }

    /**
     * 获取成功计数。
     * Get success count.
     */
    public LongAdder getSuccessCount() {
        return successCount;
    }

    /**
     * 获取失败计数。
     * Get failure count.
     */
    public LongAdder getFailureCount() {
        return failureCount;
    }

    /**
     * 获取丢弃计数。
     * Get dropped count.
     */
    public LongAdder getDroppedCount() {
        return droppedCount;
    }

    /**
     * 获取接收计数。
     * Get received count.
     */
    public LongAdder getReceivedCount() {
        return receivedCount;
    }

    /**
     * 获取队列深度。
     * Get queue depth.
     */
    public AtomicInteger getQueueDepth() {
        return queueDepth;
    }

    /**
     * 设置队列深度。
     * Set queue depth.
     *
     * @param depth 深度 / depth
     */
    public void setQueueDepth(int depth) {
        queueDepth.set(depth);
    }

    /**
     * 获取活跃 Worker 数。
     * Get active worker count.
     */
    public AtomicInteger getActiveWorkers() {
        return activeWorkers;
    }

    /**
     * 设置活跃 Worker 数。
     * Set active worker count.
     *
     * @param count 数量 / count
     */
    public void setActiveWorkers(int count) {
        activeWorkers.set(count);
    }

    /**
     * 重置所有指标。
     * Reset all metrics.
     */
    public void reset() {
        successCount.reset();
        failureCount.reset();
        droppedCount.reset();
        receivedCount.reset();
        executionsInWindow.reset();
        ewmaResponseTime.set(0);
        queueDepth.set(0);
        activeWorkers.set(0);
    }

    /**
     * 获取成功总数。
     * Get total success count.
     *
     * @return 成功数 / success count
     */
    public long getTotalSuccess() {
        return successCount.sum();
    }

    /**
     * 获取失败总数。
     * Get total failure count.
     *
     * @return 失败数 / failure count
     */
    public long getTotalFailure() {
        return failureCount.sum();
    }

    /**
     * 获取丢弃总数。
     * Get total dropped count.
     *
     * @return 丢弃数 / dropped count
     */
    public long getTotalDropped() {
        return droppedCount.sum();
    }

    /**
     * 获取成功率。
     * Get success rate.
     *
     * @return 成功率 (0-1) / success rate (0-1)
     */
    public double getSuccessRate() {
        long success = successCount.sum();
        long failure = failureCount.sum();
        long total = success + failure;
        if (total == 0) {
            return 1.0;
        }
        return (double) success / total;
    }
}
