package com.aypak.engine.alarm.monitor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 告警指标收集器
 * 线程安全的指标统计
 * Alarm metrics collector.
 * Thread-safe metrics statistics.
 */
public class AlarmMetrics {

    // 吞吐指标
    // Throughput metrics
    private final LongAdder incomingCount = new LongAdder();
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder droppedCount = new LongAdder();

    // 时延指标（EWMA）
    // Latency metrics (EWMA)
    private final AtomicLong avgProcessingRT = new AtomicLong(0);
    private final AtomicLong avgDBLatency = new AtomicLong(0);

    // 队列指标
    // Queue metrics
    private final AtomicInteger receiverQueueDepth = new AtomicInteger(0);
    private volatile int[] workerQueueDepths = new int[0];

    // QPS 计算
    // QPS calculation
    private final AtomicLong qps = new AtomicLong(0);
    private final AtomicLong windowStartTime = new AtomicLong(0);
    private final AtomicLong windowCount = new AtomicLong(0);

    // EWMA 参数
    // EWMA parameters
    private static final double EWMA_ALPHA = 0.3;

    /**
     * 记录进入的告警
     * Record incoming alarm.
     */
    public void recordIncoming() {
        incomingCount.increment();
        updateQPS();
    }

    /**
     * 记录成功处理
     * Record successful processing.
     */
    public void recordSuccess() {
        successCount.increment();
    }

    /**
     * 记录失败
     * Record failure.
     */
    public void recordFailure() {
        failureCount.increment();
    }

    /**
     * 记录丢弃
     * Record dropped.
     */
    public void recordDropped() {
        droppedCount.increment();
    }

    /**
     * 更新处理时延
     * Update processing RT.
     */
    public void updateProcessingRT(long latencyMs) {
        updateEWMA(avgProcessingRT, latencyMs);
    }

    /**
     * 更新 DB 延迟
     * Update DB latency.
     */
    public void updateDBLatency(long latencyMs) {
        updateEWMA(avgDBLatency, latencyMs);
    }

    /**
     * 更新接收队列深度
     * Update receiver queue depth.
     */
    public void setReceiverQueueDepth(int depth) {
        receiverQueueDepth.set(depth);
    }

    /**
     * 更新 Worker 队列深度
     * Update Worker queue depths.
     */
    public void setWorkerQueueDepths(int[] depths) {
        this.workerQueueDepths = depths.clone();
    }

    /**
     * 获取进入计数
     * Get incoming count.
     */
    public long getIncomingCount() {
        return incomingCount.sum();
    }

    /**
     * 获取成功计数
     * Get success count.
     */
    public long getSuccessCount() {
        return successCount.sum();
    }

    /**
     * 获取失败计数
     * Get failure count.
     */
    public long getFailureCount() {
        return failureCount.sum();
    }

    /**
     * 获取丢弃计数
     * Get dropped count.
     */
    public long getDroppedCount() {
        return droppedCount.sum();
    }

    /**
     * 获取平均处理时延
     * Get average processing RT.
     */
    public long getAvgProcessingRT() {
        return avgProcessingRT.get();
    }

    /**
     * 获取平均 DB 延迟
     * Get average DB latency.
     */
    public long getAvgDBLatency() {
        return avgDBLatency.get();
    }

    /**
     * 获取接收队列深度
     * Get receiver queue depth.
     */
    public int getReceiverQueueDepth() {
        return receiverQueueDepth.get();
    }

    /**
     * 获取 Worker 队列深度
     * Get Worker queue depths.
     */
    public int[] getWorkerQueueDepths() {
        return workerQueueDepths.clone();
    }

    /**
     * 获取总 Worker 队列深度
     * Get total Worker queue depth.
     */
    public int getTotalWorkerQueueDepth() {
        int total = 0;
        for (int depth : workerQueueDepths) {
            total += depth;
        }
        return total;
    }

    /**
     * 获取 QPS
     * Get QPS.
     */
    public long getQPS() {
        return qps.get();
    }

    /**
     * 获取成功率
     * Get success rate.
     */
    public double getSuccessRate() {
        long total = successCount.sum() + failureCount.sum();
        return total > 0 ? (double) successCount.sum() / total * 100 : 0;
    }

    /**
     * 获取错误率
     * Get failure rate.
     */
    public double getFailureRate() {
        long total = successCount.sum() + failureCount.sum();
        return total > 0 ? (double) failureCount.sum() / total * 100 : 0;
    }

    /**
     * 更新 QPS（每秒调用）
     * Update QPS (called per second).
     */
    private void updateQPS() {
        long now = System.currentTimeMillis();
        long start = windowStartTime.get();

        if (now - start >= 1000) {
            // 新的秒窗口
            // New second window
            long count = windowCount.getAndSet(0);
            qps.set(count);
            windowStartTime.set(now);
        }

        windowCount.incrementAndGet();
    }

    /**
     * 更新 EWMA
     * Update EWMA.
     */
    private void updateEWMA(AtomicLong target, long newValue) {
        long current;
        long updated;
        do {
            current = target.get();
            updated = (long) (EWMA_ALPHA * newValue + (1 - EWMA_ALPHA) * current);
        } while (!target.compareAndSet(current, updated));
    }

    /**
     * 重置所有指标
     * Reset all metrics.
     */
    public void reset() {
        incomingCount.reset();
        successCount.reset();
        failureCount.reset();
        droppedCount.reset();
        avgProcessingRT.set(0);
        avgDBLatency.set(0);
        receiverQueueDepth.set(0);
        workerQueueDepths = new int[0];
        qps.set(0);
        windowStartTime.set(System.currentTimeMillis());
        windowCount.set(0);
    }

    /**
     * 获取指标快照
     * Get metrics snapshot.
     */
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
                getIncomingCount(),
                getSuccessCount(),
                getFailureCount(),
                getDroppedCount(),
                getQPS(),
                getAvgProcessingRT(),
                getAvgDBLatency(),
                getReceiverQueueDepth(),
                getTotalWorkerQueueDepth(),
                getSuccessRate(),
                getFailureRate()
        );
    }

    /**
     * 指标快照
     * Metrics snapshot.
     */
    public static class MetricsSnapshot {
        public final long incomingCount;
        public final long successCount;
        public final long failureCount;
        public final long droppedCount;
        public final long qps;
        public final long avgProcessingRT;
        public final long avgDBLatency;
        public final int receiverQueueDepth;
        public final int totalWorkerQueueDepth;
        public final double successRate;
        public final double failureRate;

        public MetricsSnapshot(long incomingCount, long successCount, long failureCount,
                              long droppedCount, long qps, long avgProcessingRT,
                              long avgDBLatency, int receiverQueueDepth,
                              int totalWorkerQueueDepth, double successRate,
                              double failureRate) {
            this.incomingCount = incomingCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.droppedCount = droppedCount;
            this.qps = qps;
            this.avgProcessingRT = avgProcessingRT;
            this.avgDBLatency = avgDBLatency;
            this.receiverQueueDepth = receiverQueueDepth;
            this.totalWorkerQueueDepth = totalWorkerQueueDepth;
            this.successRate = successRate;
            this.failureRate = failureRate;
        }

        @Override
        public String toString() {
            return String.format(
                "QPS: %d | Success: %d | Failure: %d | Dropped: %d | " +
                "RT: %dms | DB: %dms | Queue: R[%d] W[%d] | Rate: %.2f%%",
                qps, successCount, failureCount, droppedCount,
                avgProcessingRT, avgDBLatency,
                receiverQueueDepth, totalWorkerQueueDepth,
                successRate
            );
        }
    }
}
