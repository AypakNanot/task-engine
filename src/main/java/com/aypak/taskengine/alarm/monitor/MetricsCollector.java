package com.aypak.taskengine.alarm.monitor;

import com.aypak.taskengine.alarm.dispatcher.ShardDispatcher;
import com.aypak.taskengine.alarm.receiver.AlarmReceiver;
import com.aypak.taskengine.alarm.batch.BatchDBExecutor;

/**
 * 指标收集器
 * 收集全链路各节点的指标数据
 */
public class MetricsCollector {

    /** 告警指标 */
    private final AlarmMetrics metrics;

    /** 告警接收器 */
    private final AlarmReceiver receiver;

    /** 分片调度器 */
    private final ShardDispatcher dispatcher;

    /** 批量数据库执行器 */
    private final BatchDBExecutor dbExecutor;

    /**
     * 创建指标收集器
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
     */
    public void reset() {
        metrics.reset();
    }

    /**
     * 告警引擎统计信息
     */
    public static class AlarmEngineStats {
        /** 进入总数 */
        public final long incomingCount;
        /** 成功总数 */
        public final long successCount;
        /** 失败总数 */
        public final long failureCount;
        /** 丢弃总数 */
        public final long droppedCount;
        /** QPS */
        public final long qps;
        /** 平均处理时延（毫秒）*/
        public final long avgProcessingRT;
        /** 平均 DB 延迟（毫秒）*/
        public final long avgDBLatency;
        /** 成功率（%）*/
        public final double successRate;
        /** 失败率（%）*/
        public final double failureRate;
        /** 接收队列深度 */
        public final int receiverQueueDepth;
        /** 接收器丢弃数 */
        public final long receiverDropped;
        /** Worker 总队列深度 */
        public final int workerQueueDepth;
        /** Worker 丢弃数 */
        public final int workerDropped;
        /** 数据库插入次数 */
        public final long dbInsertCount;
        /** 数据库插入记录数 */
        public final long dbInsertedRecords;
        /** 数据库失败次数 */
        public final long dbFailureCount;
        /** 数据库平均延迟（毫秒）*/
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
