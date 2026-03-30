package com.aypak.engine.alarm.monitor;

import com.aypak.engine.alarm.dispatcher.ShardDispatcher;
import com.aypak.engine.alarm.receiver.AlarmReceiver;
import com.aypak.engine.alarm.batch.BatchDBExecutor;

/**
 * 指标收集器
 * 收集全链路各节点的指标数据
 * Metrics collector.
 * Collects metrics data from all nodes in the full link.
 */
public class MetricsCollector {

    /** 告警指标 / Alarm metrics */
    private final AlarmMetrics metrics;

    /** 告警接收器 / Alarm receiver */
    private final AlarmReceiver receiver;

    /** 分片调度器 / Shard dispatcher */
    private final ShardDispatcher dispatcher;

    /** 批量数据库执行器 / Batch database executor */
    private final BatchDBExecutor dbExecutor;

    /**
     * 创建指标收集器
     * Create metrics collector.
     */
    public MetricsCollector(AlarmMetrics metrics, AlarmReceiver receiver,
                           ShardDispatcher dispatcher, BatchDBExecutor dbExecutor) {
        this.metrics = metrics;
        this.receiver = receiver;
        this.dispatcher = dispatcher;
        this.dbExecutor = dbExecutor;
    }

    /**
     * 获取全链路指标汇总
     * Get full-link metrics summary.
     */
    public AlarmEngineStats getStats() {
        return new AlarmEngineStats(
                metrics.getIncomingCount(),
                metrics.getSuccessCount(),
                metrics.getFailureCount(),
                metrics.getDroppedCount(),
                metrics.getQPS(),
                metrics.getAvgProcessingRT(),
                metrics.getAvgDBLatency(),
                metrics.getSuccessRate(),
                metrics.getFailureRate(),
                receiver.getQueueSize(),
                receiver.getDropCount(),
                dispatcher.getTotalQueueDepth(),
                dispatcher.getDroppedCount(),
                dbExecutor.getInsertCount(),
                dbExecutor.getTotalInsertedRecords(),
                dbExecutor.getFailureCount(),
                dbExecutor.getAverageLatency()
        );
    }

    /**
     * 重置所有指标
     * Reset all metrics.
     */
    public void reset() {
        metrics.reset();
    }

    /**
     * 告警引擎统计信息
     * Alarm engine statistics.
     */
    public static class AlarmEngineStats {
        /** 进入总数 / Total incoming count */
        public final long incomingCount;
        /** 成功总数 / Total success count */
        public final long successCount;
        /** 失败总数 / Total failure count */
        public final long failureCount;
        /** 丢弃总数 / Total dropped count */
        public final long droppedCount;
        /** QPS */
        public final long qps;
        /** 平均处理时延（毫秒）/ Average processing RT in milliseconds */
        public final long avgProcessingRT;
        /** 平均 DB 延迟（毫秒）/ Average DB latency in milliseconds */
        public final long avgDBLatency;
        /** 成功率（%）/ Success rate in percentage */
        public final double successRate;
        /** 失败率（%）/ Failure rate in percentage */
        public final double failureRate;
        /** 接收队列深度 / Receiver queue depth */
        public final int receiverQueueDepth;
        /** 接收器丢弃数 / Receiver dropped count */
        public final long receiverDropped;
        /** Worker 总队列深度 / Total Worker queue depth */
        public final int workerQueueDepth;
        /** Worker 丢弃数 / Worker dropped count */
        public final int workerDropped;
        /** 数据库插入次数 / Database insert count */
        public final long dbInsertCount;
        /** 数据库插入记录数 / Database inserted records count */
        public final long dbInsertedRecords;
        /** 数据库失败次数 / Database failure count */
        public final long dbFailureCount;
        /** 数据库平均延迟（毫秒）/ Database average latency in milliseconds */
        public final double dbAvgLatency;

        public AlarmEngineStats(long incomingCount, long successCount, long failureCount,
                               long droppedCount, long qps, long avgProcessingRT,
                               long avgDBLatency, double successRate, double failureRate,
                               int receiverQueueDepth, long receiverDropped,
                               int workerQueueDepth, int workerDropped,
                               long dbInsertCount, long dbInsertedRecords,
                               long dbFailureCount, double dbAvgLatency) {
            this.incomingCount = incomingCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.droppedCount = droppedCount;
            this.qps = qps;
            this.avgProcessingRT = avgProcessingRT;
            this.avgDBLatency = avgDBLatency;
            this.successRate = successRate;
            this.failureRate = failureRate;
            this.receiverQueueDepth = receiverQueueDepth;
            this.receiverDropped = receiverDropped;
            this.workerQueueDepth = workerQueueDepth;
            this.workerDropped = workerDropped;
            this.dbInsertCount = dbInsertCount;
            this.dbInsertedRecords = dbInsertedRecords;
            this.dbFailureCount = dbFailureCount;
            this.dbAvgLatency = dbAvgLatency;
        }

        @Override
        public String toString() {
            return String.format(
                "AlarmEngineStats[QPS=%d, Success=%d, Failure=%d, Dropped=%d, " +
                "RT=%dms, DB=%dms, Rate=%.2f%%, Queue=R[%d]W[%d]]",
                qps, successCount, failureCount, droppedCount,
                avgProcessingRT, avgDBLatency, successRate,
                receiverQueueDepth, workerQueueDepth
            );
        }
    }
}
