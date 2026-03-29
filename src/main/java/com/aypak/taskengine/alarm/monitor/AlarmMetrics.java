package com.aypak.taskengine.alarm.monitor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 告警指标收集器
 * 线程安全的指标统计
 */
public class AlarmMetrics {

    // 吞吐指标
    private final LongAdder incomingCount = new LongAdder();
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder droppedCount = new LongAdder();

    // 时延指标（EWMA）
    private final AtomicLong avgProcessingRT = new AtomicLong(0);
    private final AtomicLong avgDBLatency = new AtomicLong(0);

    // 队列指标
    private final AtomicInteger receiverQueueDepth = new AtomicInteger(0);
    private volatile int[] workerQueueDepths = new int[0];

    // QPS 计算
    private final AtomicLong qps = new AtomicLong(0);
    private final AtomicLong windowStartTime = new AtomicLong(0);
    private final AtomicLong windowCount = new AtomicLong(0);

    // EWMA 参数
    private static final double EWMA_ALPHA = 0.3;

    /**
     * 记录进入的告警
     */
    public void recordIncoming() {
        incomingCount.increment();
        updateQPS();
    }

    /**
     * 记录成功处理
     */
    public void recordSuccess() {
        successCount.increment();
    }

    /**
     * 记录失败
     */
    public void recordFailure() {
        failureCount.increment();
    }

    /**
     * 记录丢弃
     */
    public void recordDropped() {
        droppedCount.increment();
    }

    /**
     * 更新处理时延
     */
    public void updateProcessingRT(long latencyMs) {
        updateEWMA(avgProcessingRT, latencyMs);
    }

    /**
     * 更新 DB 延迟
     */
    public void updateDBLatency(long latencyMs) {
        updateEWMA(avgDBLatency, latencyMs);
    }

    /**
     * 更新接收队列深度
     */
    public void setReceiverQueueDepth(int depth) {
        receiverQueueDepth.set(depth);
    }

    /**
     * 更新 Worker 队列深度
     */
    public void setWorkerQueueDepths(int[] depths) {
        this.workerQueueDepths = depths.clone();
    }

    /**
     * 获取进入计数
     */
    public long getIncomingCount() {
        return incomingCount.sum();
    }

    /**
     * 获取成功计数
     */
    public long getSuccessCount() {
        return successCount.sum();
    }

    /**
     * 获取失败计数
     */
    public long getFailureCount() {
        return failureCount.sum();
    }

    /**
     * 获取丢弃计数
     */
    public long getDroppedCount() {
        return droppedCount.sum();
    }

    /**
     * 获取平均处理时延
     */
    public long getAvgProcessingRT() {
        return avgProcessingRT.get();
    }

    /**
     * 获取平均 DB 延迟
     */
    public long getAvgDBLatency() {
        return avgDBLatency.get();
    }

    /**
     * 获取接收队列深度
     */
    public int getReceiverQueueDepth() {
        return receiverQueueDepth.get();
    }

    /**
     * 获取 Worker 队列深度
     */
    public int[] getWorkerQueueDepths() {
        return workerQueueDepths.clone();
    }

    /**
     * 获取总 Worker 队列深度
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
     */
    public long getQPS() {
        return qps.get();
    }

    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        long total = successCount.sum() + failureCount.sum();
        return total > 0 ? (double) successCount.sum() / total * 100 : 0;
    }

    /**
     * 获取错误率
     */
    public double getFailureRate() {
        long total = successCount.sum() + failureCount.sum();
        return total > 0 ? (double) failureCount.sum() / total * 100 : 0;
    }

    /**
     * 更新 QPS（每秒调用）
     */
    private void updateQPS() {
        long now = System.currentTimeMillis();
        long start = windowStartTime.get();

        if (now - start >= 1000) {
            // 新的秒窗口
            long count = windowCount.getAndSet(0);
            qps.set(count);
            windowStartTime.set(now);
        }

        windowCount.incrementAndGet();
    }

    /**
     * 更新 EWMA
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
