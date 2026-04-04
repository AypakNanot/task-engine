package com.aypak.taskengine.monitor;

import com.aypak.taskengine.executor.TaskEngineImpl;
import com.aypak.taskengine.executor.TaskExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池耗尽告警器。
 * Pool exhaustion alarm detector.
 *
 * <p>检测线程池持续满载的情况，当以下条件满足时发出告警：</p>
 * <ul>
 *     <li>活跃线程数 >= 最大线程数的 90%</li>
 *     <li>队列利用率 >= 80%</li>
 *     <li>持续 N 个检查周期</li>
 * </ul>
 */
@Slf4j
public class PoolExhaustionAlarm {

    private final TaskEngineImpl taskEngine;
    private final ScheduledExecutorService alarmExecutor;
    private final Map<String, AtomicInteger> consecutiveAlertCounts = new ConcurrentHashMap<>();

    // 告警阈值
    private static final double THREAD_UTILIZATION_THRESHOLD = 0.9;  // 90% 线程利用率
    private static final double QUEUE_UTILIZATION_THRESHOLD = 0.8;   // 80% 队列利用率
    private static final int CONSECUTIVE_THRESHOLD = 3;              // 连续 3 次检测触发告警
    private static final long CHECK_INTERVAL_MS = 5000;              // 5 秒检查一次

    // 告警冷却时间（防止告警风暴）
    private static final long ALERT_COOLDOWN_MS = 60000;             // 1 分钟
    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();

    public PoolExhaustionAlarm(TaskEngineImpl taskEngine) {
        this.taskEngine = taskEngine;
        this.alarmExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "PoolExhaustionAlarm-thread")
        );
    }

    /**
     * 启动告警检测。
     * Start alarm detection.
     */
    public void start() {
        alarmExecutor.scheduleAtFixedRate(
                this::checkPoolExhaustion,
                CHECK_INTERVAL_MS,
                CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        log.info("PoolExhaustionAlarm started with interval {}ms", CHECK_INTERVAL_MS);
    }

    /**
     * 检查所有线程池是否耗尽。
     * Check all thread pools for exhaustion.
     */
    private void checkPoolExhaustion() {
        Map<String, TaskExecutor> executors = taskEngine.getExecutors();

        for (Map.Entry<String, TaskExecutor> entry : executors.entrySet()) {
            String taskName = entry.getKey();
            TaskExecutor executor = entry.getValue();

            int activeThreads = executor.getActiveThreads();
            int maxPoolSize = executor.getMaxPoolSize();
            int queueSize = executor.getQueueSize();
            int queueCapacity = executor.getQueueCapacity();

            // 检查线程利用率
            double threadUtilization = maxPoolSize > 0 ? (double) activeThreads / maxPoolSize : 0;
            double queueUtilization = queueCapacity > 0 ? (double) queueSize / queueCapacity : 0;

            boolean isThreadExhausted = threadUtilization >= THREAD_UTILIZATION_THRESHOLD;
            boolean isQueueExhausted = queueUtilization >= QUEUE_UTILIZATION_THRESHOLD;

            if (isThreadExhausted || isQueueExhausted) {
                incrementAlertCount(taskName);
            } else {
                resetAlertCount(taskName);
            }

            // 检查是否应该触发告警
            if (shouldTriggerAlert(taskName)) {
                triggerAlert(taskName, activeThreads, maxPoolSize, queueSize, queueCapacity,
                        threadUtilization, queueUtilization);
            }
        }
    }

    /**
     * 增加告警计数。
     * Increment alert count.
     */
    private void incrementAlertCount(String taskName) {
        consecutiveAlertCounts.computeIfAbsent(taskName, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    /**
     * 重置告警计数。
     * Reset alert count.
     */
    private void resetAlertCount(String taskName) {
        consecutiveAlertCounts.computeIfAbsent(taskName, k -> new AtomicInteger(0))
                .set(0);
    }

    /**
     * 获取告警计数。
     * Get alert count.
     */
    private int getAlertCount(String taskName) {
        return consecutiveAlertCounts.computeIfAbsent(taskName, k -> new AtomicInteger(0)).get();
    }

    /**
     * 检查是否应该触发告警。
     * Check if alert should be triggered.
     */
    private boolean shouldTriggerAlert(String taskName) {
        int count = getAlertCount(taskName);

        // 检查是否达到连续阈值
        if (count < CONSECUTIVE_THRESHOLD) {
            return false;
        }

        // 检查是否在冷却时间内
        long lastAlert = lastAlertTime.getOrDefault(taskName, 0L);
        long now = System.currentTimeMillis();

        return (now - lastAlert) >= ALERT_COOLDOWN_MS;
    }

    /**
     * 触发告警。
     * Trigger alert.
     */
    private void triggerAlert(String taskName, int activeThreads, int maxPoolSize,
                             int queueSize, int queueCapacity,
                             double threadUtilization, double queueUtilization) {

        // 更新最后告警时间
        lastAlertTime.put(taskName, System.currentTimeMillis());

        // 重置计数（告警后重新开始计数）
        resetAlertCount(taskName);

        String message = String.format(
                "POOL_EXHAUSTION_ALERT: task=%s, activeThreads=%d, maxPoolSize=%d, threadUtilization=%.1f%%, " +
                "queueSize=%d, queueCapacity=%d, queueUtilization=%.1f%%",
                taskName, activeThreads, maxPoolSize, threadUtilization * 100,
                queueSize, queueCapacity, queueUtilization * 100);

        log.warn(message);

        // 可以通过事件系统发送告警通知
        // 例如：发送到监控系统、告警平台等
    }

    /**
     * 停止告警检测。
     * Stop alarm detection.
     */
    public void stop() {
        alarmExecutor.shutdown();
        try {
            if (!alarmExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                alarmExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            alarmExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("PoolExhaustionAlarm stopped");
    }

    /**
     * 获取连续告警计数。
     * Get consecutive alert count for a task.
     *
     * @param taskName 任务名称 / task name
     * @return 连续告警计数 / consecutive alert count
     */
    public int getConsecutiveAlertCount(String taskName) {
        return getAlertCount(taskName);
    }

    /**
     * 获取最后告警时间。
     * Get last alert time for a task.
     *
     * @param taskName 任务名称 / task name
     * @return 最后告警时间（毫秒时间戳）/ last alert timestamp
     */
    public Long getLastAlertTime(String taskName) {
        return lastAlertTime.get(taskName);
    }
}
