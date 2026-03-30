package com.aypak.engine.alarm.monitor;

import com.aypak.engine.alarm.dispatcher.ShardDispatcher;
import com.aypak.engine.alarm.receiver.AlarmReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 监控任务
 * 每秒输出全链路监控快照
 * Monitor task.
 * Outputs full-link monitoring snapshot every second.
 */
public class MonitorTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MonitorTask.class);

    /** 指标收集器 / Metrics collector */
    private final AlarmMetrics metrics;

    /** 告警接收器 / Alarm receiver */
    private final AlarmReceiver receiver;

    /** 分片调度器 / Shard dispatcher */
    private final ShardDispatcher dispatcher;

    /** 监控间隔（秒） / Monitor interval in seconds */
    private final long intervalSec;

    /** 调度器 / Scheduler */
    private final ScheduledExecutorService scheduler;

    /** 运行标志 / Running flag */
    private volatile boolean running = true;

    /**
     * 创建监控任务
     * Create monitor task.
     */
    public MonitorTask(AlarmMetrics metrics, AlarmReceiver receiver,
                       ShardDispatcher dispatcher) {
        this(metrics, receiver, dispatcher, 1);
    }

    /**
     * 创建监控任务
     * Create monitor task.
     */
    public MonitorTask(AlarmMetrics metrics, AlarmReceiver receiver,
                       ShardDispatcher dispatcher, long intervalSec) {
        this.metrics = metrics;
        this.receiver = receiver;
        this.dispatcher = dispatcher;
        this.intervalSec = intervalSec;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "AlarmMonitorTask"));
    }

    /**
     * 启动监控
     * Start monitor.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this, intervalSec, intervalSec, TimeUnit.SECONDS);
        log.info("MonitorTask started with interval {}s", intervalSec);
    }

    /**
     * 停止监控
     * Stop monitor.
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("MonitorTask stopped");
    }

    @Override
    public void run() {
        if (!running) {
            return;
        }

        try {
            // 更新指标
            // Update metrics
            updateMetrics();

            // 输出快照
            // Output snapshot
            printSnapshot();

        } catch (Exception e) {
            log.error("MonitorTask failed", e);
        }
    }

    /**
     * 更新指标
     * Update metrics.
     */
    private void updateMetrics() {
        // 更新队列深度
        // Update queue depth
        metrics.setReceiverQueueDepth(receiver.getQueueSize());
        metrics.setWorkerQueueDepths(dispatcher.getWorkerQueueDepths());

        // 更新丢弃计数
        // Update dropped count (should get from receiver in actual implementation)
        metrics.recordDropped(); // 实际应该从 receiver 获取
    }

    /**
     * 打印监控快照
     * Print monitoring snapshot.
     */
    private void printSnapshot() {
        AlarmMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();

        StringBuilder sb = new StringBuilder();
        sb.append("\n================================================================================\n");
        sb.append(String.format("AlarmEngine Monitor - %s\n", java.time.LocalDateTime.now()));
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format("Incoming QPS    : %d | Success/Error : %d / %d | Dropped : %d\n",
                snapshot.qps, snapshot.successCount, snapshot.failureCount, snapshot.droppedCount));
        sb.append(String.format("Queue Depth     : R[%d] W[%d] | Total: %d\n",
                snapshot.receiverQueueDepth, snapshot.totalWorkerQueueDepth,
                snapshot.receiverQueueDepth + snapshot.totalWorkerQueueDepth));
        sb.append(String.format("Processing RT   : %d ms (avg) | DB Latency: %d ms (avg)\n",
                snapshot.avgProcessingRT, snapshot.avgDBLatency));
        sb.append(String.format("Success Rate    : %.2f%% | Failure Rate: %.2f%%\n",
                snapshot.successRate, snapshot.failureRate));

        // Worker 状态
        // Worker status
        sb.append("Worker Status   : ");
        int[] depths = dispatcher.getWorkerQueueDepths();
        for (int i = 0; i < depths.length && i < 8; i++) {
            sb.append(String.format("W%d[%d] ", i, depths[i]));
        }
        if (depths.length > 8) {
            sb.append(String.format("... +%d more", depths.length - 8));
        }

        sb.append("\n================================================================================\n");

        log.info(sb.toString());
    }
}
