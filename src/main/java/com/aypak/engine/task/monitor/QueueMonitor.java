package com.aypak.engine.task.monitor;

import com.aypak.engine.task.executor.TaskExecutor;
import com.aypak.engine.task.executor.TaskEngineImpl;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 队列深度监控器，带有告警触发功能。
 * Queue depth monitor with alert triggering.
 */
@Slf4j
public class QueueMonitor {

    private final TaskEngineImpl taskEngine;
    private final int defaultThreshold;
    private final ScheduledExecutorService monitorExecutor;

    public QueueMonitor(TaskEngineImpl taskEngine, int defaultThreshold) {
        this.taskEngine = taskEngine;
        this.defaultThreshold = defaultThreshold;
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "QueueMonitor-thread")
        );
    }

    /**
     * 启动定期队列监控。
     * Start periodic queue monitoring.
     *
     * @param intervalMs 监控间隔（毫秒）/ monitoring interval in milliseconds
     */
    public void start(long intervalMs) {
        monitorExecutor.scheduleAtFixedRate(
                this::checkQueues,
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
        log.info("Queue monitor started with interval {}ms", intervalMs);
    }

    /**
     * 检查所有队列并触发告警。
     * Check all queues and trigger alerts.
     */
    private void checkQueues() {
        Map<String, TaskExecutor> executors = taskEngine.getExecutors();

        for (Map.Entry<String, TaskExecutor> entry : executors.entrySet()) {
            String taskName = entry.getKey();
            TaskExecutor executor = entry.getValue();

            int queueDepth = executor.getQueueSize();
            int queueCapacity = executor.getQueueCapacity();

            if (queueCapacity > 0) {
                double utilization = (queueDepth * 100.0) / queueCapacity;

                if (utilization >= defaultThreshold) {
                    log.warn("[{}] Queue depth: {} / {} ({}% > threshold {}%) ALERT",
                            taskName, queueDepth, queueCapacity,
                            String.format("%.1f", utilization), defaultThreshold);
                } else {
                    log.debug("[{}] Queue depth: {} / {} ({:.1f}%)",
                            taskName, queueDepth, queueCapacity, utilization);
                }
            }
        }
    }

    /**
     * 停止监控。
     * Stop monitoring.
     */
    public void stop() {
        monitorExecutor.shutdown();
        try {
            if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Queue monitor stopped");
    }
}