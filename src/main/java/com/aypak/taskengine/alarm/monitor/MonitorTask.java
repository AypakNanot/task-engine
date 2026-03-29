package com.aypak.taskengine.alarm.monitor;

import com.aypak.taskengine.alarm.dispatcher.ShardDispatcher;
import com.aypak.taskengine.alarm.receiver.AlarmReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 监控任务
 * 每秒输出全链路监控快照
 */
public class MonitorTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MonitorTask.class);

    /** 指标收集器 */
    private final AlarmMetrics metrics;

    /** 告警接收器 */
    private final AlarmReceiver receiver;

    /** 分片调度器 */
    private final ShardDispatcher dispatcher;

    /** 监控间隔（秒）*/
    private final long intervalSec;

    /** 调度器 */
    private final ScheduledExecutorService scheduler;

    /** 运行标志 */
    private volatile boolean running = true;

    /**
     * 创建监控任务
     */
    public MonitorTask(AlarmMetrics metrics, AlarmReceiver receiver,
                       ShardDispatcher dispatcher) {
        this(metrics, receiver, dispatcher, 1);
    }

    /**
     * 创建监控任务
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
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this, intervalSec, intervalSec, TimeUnit.SECONDS);
        log.info("MonitorTask started with interval {}s", intervalSec);
    }

    /**
     * 停止监控
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
            updateMetrics();

            // 输出快照
            printSnapshot();

        } catch (Exception e) {
            log.error("MonitorTask failed", e);
        }
    }

    /**
     * 更新指标
     */
    private void updateMetrics() {
        // 更新队列深度
        metrics.setReceiverQueueDepth(receiver.getQueueSize());
        metrics.setWorkerQueueDepths(dispatcher.getWorkerQueueDepths());

        // 更新丢弃计数
        metrics.recordDropped(); // 实际应该从 receiver 获取
    }

    /**
     * 打印监控快照
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
