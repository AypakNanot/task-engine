package com.aypak.taskengine.executor;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.monitor.EwmaPredictor;
import com.aypak.taskengine.monitor.TaskMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 EWMA 预测的自适应线程池扩展器。
 * Adaptive thread pool scaler based on EWMA prediction.
 *
 * <p>核心特性：</p>
 * <ul>
 *     <li>使用 EWMA 预测 QPS 和队列深度趋势</li>
 *     <li>提前扩展：在队列堆积前增加线程</li>
 *     <li>缓慢缩容：避免频繁线程创建/销毁</li>
 *     <li>流量突变检测：快速响应突发流量</li>
 * </ul>
 *
 * <p>扩展策略：</p>
 * <pre>
 * 1. QPS 快速上升 + 队列深度上升 → 立即扩展
 * 2. QPS 平稳 + 队列深度 > 50% → 按需扩展
 * 3. QPS 下降 + 队列深度 < 20% → 缓慢缩容
 * </pre>
 */
@Slf4j
public class AdaptiveScaler {

    private final TaskEngineImpl taskEngine;
    private final TaskEngineProperties properties;
    private final AtomicInteger totalThreads = new AtomicInteger(0);
    private final ScheduledExecutorService scalerExecutor;

    // EWMA 预测器配置
    private static final double QPS_ALPHA = 0.3;        // QPS 预测平滑因子
    private static final double QUEUE_ALPHA = 0.2;      // 队列深度预测平滑因子
    private static final int RAPID_TREND_THRESHOLD = 3; // 快速趋势判定阈值

    // 每个任务的 EWMA 预测器
    private final EwmaPredictor qpsPredictor;
    private final EwmaPredictor queuePredictor;

    // 上次扩展时间（用于防抖）
    private volatile long lastScaleTime = 0;
    private static final long SCALE_COOLDOWN_MS = 2000; // 2 秒冷却时间

    public AdaptiveScaler(TaskEngineImpl taskEngine, TaskEngineProperties properties) {
        this.taskEngine = taskEngine;
        this.properties = properties;
        this.scalerExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "AdaptiveScaler-thread")
        );
        this.qpsPredictor = new EwmaPredictor(QPS_ALPHA);
        this.queuePredictor = new EwmaPredictor(QUEUE_ALPHA);
    }

    /**
     * 启动自适应扩展。
     * Start adaptive scaling.
     *
     * @param intervalMs 评估间隔（毫秒）/ evaluation interval
     */
    public void start(long intervalMs) {
        scalerExecutor.scheduleAtFixedRate(
                this::evaluateAndScale,
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
        log.info("Adaptive scaler started with interval {}ms, QPS α={}, Queue α={}",
                intervalMs, QPS_ALPHA, QUEUE_ALPHA);
    }

    /**
     * 评估所有执行器并进行扩展/缩容。
     * Evaluate all executors and scale up/down.
     */
    private void evaluateAndScale() {
        Map<String, TaskExecutor> executors = taskEngine.getExecutors();

        for (Map.Entry<String, TaskExecutor> entry : executors.entrySet()) {
            String taskName = entry.getKey();
            TaskExecutor executor = entry.getValue();
            TaskMetrics metrics = taskEngine.getStats(taskName);

            if (metrics == null) continue;

            evaluateTaskScaling(taskName, executor, metrics);
        }
    }

    /**
     * 评估单个任务的扩展需求。
     * Evaluate scaling needs for a single task.
     */
    private void evaluateTaskScaling(String taskName, TaskExecutor executor, TaskMetrics metrics) {
        // 检查冷却时间
        if (System.currentTimeMillis() - lastScaleTime < SCALE_COOLDOWN_MS) {
            return;
        }

        int queueDepth = executor.getQueueSize();
        int queueCapacity = executor.getQueueCapacity();
        int activeThreads = executor.getActiveThreads();
        int currentMax = executor.getMaxPoolSize();

        if (queueCapacity <= 0) return;

        // 更新 EWMA 预测
        double currentQps = metrics.getQps();
        double queueUtilization = (queueDepth * 100.0) / queueCapacity;

        qpsPredictor.update((long) currentQps);
        queuePredictor.update(queueDepth);

        // 检测流量突变
        if (detectTrafficSpike(taskName, currentQps, queueUtilization)) {
            scaleUpImmediately(taskName, executor, currentMax, "traffic spike detected");
            return;
        }

        // 基于预测的扩展决策
        if (shouldPredictiveScaleUp(taskName, metrics, queueUtilization, activeThreads, currentMax)) {
            scaleUp(taskName, executor, currentMax);
        } else if (shouldScaleDown(taskName, metrics, queueUtilization, activeThreads, currentMax)) {
            scaleDown(taskName, executor, currentMax);
        }
    }

    /**
     * 检测流量突变。
     * Detect traffic spike.
     */
    private boolean detectTrafficSpike(String taskName, double currentQps, double queueUtilization) {
        // QPS 增长率超过 50% 且队列深度在上升
        double qpsGrowthRate = qpsPredictor.getPredictedGrowthRate();
        boolean isSpike = qpsGrowthRate > 50 && queuePredictor.getTrendDirection() == 1;

        if (isSpike) {
            log.debug("[{}] Traffic spike detected: QPS growth rate={}%, queue trend={}",
                    taskName, String.format("%.1f", qpsGrowthRate), queuePredictor.getTrendDirection());
        }
        return isSpike;
    }

    /**
     * 基于预测判断是否需要扩展。
     * Check if predictive scale-up is needed.
     */
    private boolean shouldPredictiveScaleUp(String taskName, TaskMetrics metrics,
                                             double queueUtilization, int activeThreads, int currentMax) {
        // 策略 1: QPS 快速上升且连续 3 次 → 提前扩展
        if (qpsPredictor.isRapidUptrend(RAPID_TREND_THRESHOLD)) {
            log.debug("[{}] Predictive scale-up: QPS rapid uptrend ({} consecutive)",
                    taskName, qpsPredictor.getConsecutiveTrendCount());
            return true;
        }

        // 策略 2: 预测的下一周期 QPS 超过当前处理能力的 80%
        long predictedQps = qpsPredictor.predictNext();
        double currentCapacity = currentMax * (1000.0 / Math.max(metrics.getAvgResponseTime(), 1));

        if (predictedQps > currentCapacity * 0.8) {
            log.debug("[{}] Predictive scale-up: predicted QPS {} > capacity {}%",
                    taskName, predictedQps, (int) (currentCapacity * 0.8));
            return true;
        }

        // 策略 3: 队列深度超过阈值且活跃线程已满
        if (queueUtilization >= properties.getScaleUpThreshold() && activeThreads >= currentMax) {
            log.debug("[{}] Scale-up: queue utilization {}% >= threshold {}%",
                    taskName, String.format("%.1f", queueUtilization), properties.getScaleUpThreshold());
            return true;
        }

        return false;
    }

    /**
     * 判断是否需要缩容。
     * Check if scale-down is needed.
     */
    private boolean shouldScaleDown(String taskName, TaskMetrics metrics,
                                     double queueUtilization, int activeThreads, int currentMax) {
        // 策略 1: QPS 快速下降且连续 3 次 → 考虑缩容
        if (queuePredictor.isRapidDowntrend(RAPID_TREND_THRESHOLD)) {
            // 但要确保队列深度很低
            if (queueUtilization < 10) {
                log.debug("[{}] Scale-down: queue rapid downtrend and low utilization", taskName);
                return true;
            }
        }

        // 策略 2: 队列深度 < 10% 且活跃线程 < 最大值的 30%
        int originalMax = metrics.getOriginalMaxPoolSize().get();
        if (queueUtilization < 10 && activeThreads < currentMax * 0.3 && currentMax > originalMax) {
            log.debug("[{}] Scale-down: low utilization ({}%) and active threads ({}) < 30%",
                    taskName, String.format("%.1f", queueUtilization), activeThreads);
            return true;
        }

        return false;
    }

    /**
     * 执行扩展。
     * Perform scale-up.
     */
    private void scaleUp(String taskName, TaskExecutor executor, int currentMax) {
        int currentTotal = totalThreads.get();

        if (currentTotal >= properties.getGlobalMaxThreads()) {
            log.warn("[{}] Scale-up blocked: global thread limit reached ({})",
                    taskName, properties.getGlobalMaxThreads());
            return;
        }

        int newMax = Math.min(
                currentMax + properties.getScaleFactor(),
                properties.getGlobalMaxThreads() - currentTotal + currentMax
        );
        executor.setMaxPoolSize(newMax);
        totalThreads.addAndGet(newMax - currentMax);
        lastScaleTime = System.currentTimeMillis();

        log.info("[{}] Scale UP: maxPoolSize {} -> {} (QPS trend: {}, queue: {}%)",
                taskName, currentMax, newMax,
                qpsPredictor.getTrendDirection(),
                (int) ((executor.getQueueSize() * 100.0) / executor.getQueueCapacity()));
    }

    /**
     * 立即扩展（用于流量突变）。
     * Perform immediate scale-up for traffic spikes.
     */
    private void scaleUpImmediately(String taskName, TaskExecutor executor, int currentMax, String reason) {
        int currentTotal = totalThreads.get();

        if (currentTotal >= properties.getGlobalMaxThreads()) {
            log.warn("[{}] Immediate scale-up blocked: global thread limit reached ({})",
                    taskName, properties.getGlobalMaxThreads());
            return;
        }

        // 直接扩展 2 倍 scaleFactor 以快速响应
        int scaleFactor = properties.getScaleFactor() * 2;
        int newMax = Math.min(
                currentMax + scaleFactor,
                properties.getGlobalMaxThreads() - currentTotal + currentMax
        );
        executor.setMaxPoolSize(newMax);
        totalThreads.addAndGet(newMax - currentMax);
        lastScaleTime = System.currentTimeMillis();

        log.info("[{}] Immediate scale UP: {} -> {} (reason: {})",
                taskName, currentMax, newMax, reason);
    }

    /**
     * 执行缩容。
     * Perform scale-down.
     */
    private void scaleDown(String taskName, TaskExecutor executor, int currentMax) {
        TaskMetrics metrics = taskEngine.getStats(taskName);
        int originalMax = metrics.getOriginalMaxPoolSize().get();

        if (currentMax <= originalMax) {
            return; // 已经是最小值
        }

        // 缓慢缩容：每次只减少 1 个线程
        int newMax = Math.max(originalMax, currentMax - 1);
        executor.setMaxPoolSize(newMax);
        totalThreads.addAndGet(-(currentMax - newMax));
        lastScaleTime = System.currentTimeMillis();

        log.info("[{}] Scale DOWN: maxPoolSize {} -> {} (slow scale-down)",
                taskName, currentMax, newMax);
    }

    /**
     * 停止自适应扩展。
     * Stop adaptive scaling.
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
        log.info("Adaptive scaler stopped");
    }

    /**
     * 获取当前总线程数。
     * Get current total thread count.
     */
    public int getTotalThreads() {
        return totalThreads.get();
    }

    /**
     * 获取 QPS 预测器。
     * Get QPS predictor.
     */
    public EwmaPredictor getQpsPredictor() {
        return qpsPredictor;
    }

    /**
     * 获取队列预测器。
     * Get queue predictor.
     */
    public EwmaPredictor getQueuePredictor() {
        return queuePredictor;
    }
}
