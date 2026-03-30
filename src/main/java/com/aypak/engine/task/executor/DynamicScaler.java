package com.aypak.engine.task.executor;

import com.aypak.engine.task.config.TaskEngineProperties;
import com.aypak.engine.task.monitor.TaskMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 根据队列压力动态扩展线程池。
 * Dynamic scaler for thread pools based on queue pressure.
 */
@Slf4j
public class DynamicScaler {

    private final TaskEngineImpl taskEngine;
    private final TaskEngineProperties properties;
    private final AtomicInteger totalThreads = new AtomicInteger(0);
    private final ScheduledExecutorService scalerExecutor;

    public DynamicScaler(TaskEngineImpl taskEngine, TaskEngineProperties properties) {
        this.taskEngine = taskEngine;
        this.properties = properties;
        this.scalerExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "DynamicScaler-thread")
        );
    }

    /**
     * 启动定期扩展评估。
     * Start periodic scaling evaluation.
     *
     * @param intervalMs 评估间隔（毫秒）/ evaluation interval in milliseconds
     */
    public void start(long intervalMs) {
        scalerExecutor.scheduleAtFixedRate(
                this::evaluateScaling,
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
        log.info("Dynamic scaler started with interval {}ms", intervalMs);
    }

    /**
     * 评估所有执行器的扩展需求。
     * Evaluate scaling needs for all executors.
     */
    private void evaluateScaling() {
        Map<String, TaskExecutor> executors = taskEngine.getExecutors();

        for (Map.Entry<String, TaskExecutor> entry : executors.entrySet()) {
            String taskName = entry.getKey();
            TaskExecutor executor = entry.getValue();
            TaskMetrics metrics = taskEngine.getStats(taskName);

            if (metrics == null) continue;

            int queueDepth = executor.getQueueSize();
            int queueCapacity = executor.getQueueCapacity();
            int activeThreads = executor.getActiveThreads();
            int currentMax = executor.getMaxPoolSize();

            // 计算总线程数以检查全局限制
            // Calculate total threads for global limit check
            int currentTotal = totalThreads.get();

            // 检查是否需要扩展
            // Scale-up check
            if (shouldScaleUp(queueDepth, queueCapacity, activeThreads, currentMax)) {
                if (currentTotal < properties.getGlobalMaxThreads()) {
                    int newMax = Math.min(
                            currentMax + properties.getScaleFactor(),
                            properties.getGlobalMaxThreads() - currentTotal + currentMax
                    );
                    executor.setMaxPoolSize(newMax);
                    totalThreads.addAndGet(newMax - currentMax);
                    log.info("[{}] Scale UP: maxPoolSize {} -> {} (queue: {}%, active: {})",
                            taskName, currentMax, newMax,
                            (queueDepth * 100 / queueCapacity), activeThreads);
                } else {
                    log.warn("[{}] Scale UP blocked: global thread limit reached ({})",
                            taskName, properties.getGlobalMaxThreads());
                }
            }

            // 检查是否需要缩小（简化 - 基于低利用率）
            // Scale-down check (simplified - based on low utilization)
            if (shouldScaleDown(queueDepth, queueCapacity, activeThreads, currentMax, metrics)) {
                int originalMax = metrics.getOriginalMaxPoolSize().get();
                if (currentMax > originalMax) {
                    int newMax = Math.max(originalMax, currentMax - properties.getScaleFactor());
                    executor.setMaxPoolSize(newMax);
                    totalThreads.addAndGet(-(currentMax - newMax));
                    log.info("[{}] Scale DOWN: maxPoolSize {} -> {} (idle)",
                            taskName, currentMax, newMax);
                }
            }
        }
    }

    /**
     * 检查是否需要扩展。
     * Check if scale-up is needed.
     */
    private boolean shouldScaleUp(int queueDepth, int queueCapacity, int activeThreads, int currentMax) {
        if (queueCapacity <= 0) return false;

        double utilization = (queueDepth * 100.0) / queueCapacity;
        return utilization >= properties.getScaleUpThreshold()
                && activeThreads >= currentMax;
    }

    /**
     * 检查是否需要缩小。
     * Check if scale-down is needed.
     */
    private boolean shouldScaleDown(int queueDepth, int queueCapacity, int activeThreads,
                                     int currentMax, TaskMetrics metrics) {
        if (queueCapacity <= 0) return false;

        double utilization = (queueDepth * 100.0) / queueCapacity;
        int originalMax = metrics.getOriginalMaxPoolSize().get();

        return utilization < 20 && activeThreads < currentMax / 2 && currentMax > originalMax;
    }

    /**
     * 停止扩展监控。
     * Stop scaling monitor.
     */
    public void stop() {
        scalerExecutor.shutdown();
        try {
            if (!scalerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scalerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scalerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Dynamic scaler stopped");
    }

    /**
     * 获取当前总线程数。
     * Get current total thread count.
     */
    public int getTotalThreads() {
        return totalThreads.get();
    }
}