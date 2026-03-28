package com.aypak.taskengine.monitor;

import com.aypak.taskengine.executor.TaskExecutor;
import com.aypak.taskengine.executor.TaskEngineImpl;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
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
     * Start periodic queue monitoring.
     *
     * @param intervalMs monitoring interval in milliseconds
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

                // Get threshold from config or use default
                int threshold = getThreshold(taskName);

                if (utilization >= threshold) {
                    log.warn("[{}] Queue depth: {} / {} ({}% > threshold {}%) ALERT",
                            taskName, queueDepth, queueCapacity,
                            String.format("%.1f", utilization), threshold);
                } else {
                    log.debug("[{}] Queue depth: {} / {} ({:.1f}%)",
                            taskName, queueDepth, queueCapacity, utilization);
                }
            }
        }
    }

    /**
     * Get alert threshold for task (from config or default).
     */
    private int getThreshold(String taskName) {
        // Default threshold - could be customized per task in future
        return defaultThreshold;
    }

    /**
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