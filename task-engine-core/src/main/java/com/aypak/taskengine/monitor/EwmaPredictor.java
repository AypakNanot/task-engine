package com.aypak.taskengine.monitor;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 指数加权移动平均 (EWMA) 实现，用于流量趋势预测。
 * Exponential Weighted Moving Average (EWMA) implementation for traffic trend prediction.
 *
 * <p>公式：EWMA(t) = α × 当前值 + (1-α) × EWMA(t-1)</p>
 *
 * <p>典型使用场景：</p>
 * <ul>
 *     <li>QPS 趋势预测</li>
 *     <li>队列深度趋势预测</li>
 *     <li>响应时间趋势预测</li>
 * </ul>
 *
 * 设计特点：
 * <ul>
 *     <li>无锁实现，使用 AtomicLong</li>
 *     <li>可动态调整 α 参数</li>
 *     <li>支持趋势方向检测（上升/下降/平稳）</li>
 * </ul>
 */
@Slf4j
public class EwmaPredictor {

    /**
     * 默认平滑因子 α = 0.3
     * 平衡响应速度和噪声过滤。
     */
    public static final double DEFAULT_ALPHA = 0.3;

    /**
     * 快速响应 α = 0.5
     * 适用于需要快速检测突变的场景。
     */
    public static final double FAST_ALPHA = 0.5;

    /**
     * 平滑过滤 α = 0.1
     * 适用于噪声较多的场景。
     */
    public static final double SMOOTH_ALPHA = 0.1;

    private final AtomicLong ewmaValue = new AtomicLong(0);
    private final double alpha;
    private final long complementAlpha; // (1 - α) × 1000000 的定点数表示

    // 上一次的值，用于趋势检测
    private volatile long lastValue = 0;
    // 趋势方向：1=上升，-1=下降，0=平稳
    private volatile int trendDirection = 0;
    // 连续同方向的次数
    private volatile int consecutiveTrendCount = 0;

    /**
     * 创建 EWMA 预测器。
     * Create EWMA predictor.
     *
     * @param alpha 平滑因子 (0, 1) / smoothing factor
     */
    public EwmaPredictor(double alpha) {
        if (alpha <= 0 || alpha >= 1) {
            throw new IllegalArgumentException("Alpha must be between 0 and 1");
        }
        this.alpha = alpha;
        // 使用定点数避免浮点运算
        this.complementAlpha = (long) ((1.0 - alpha) * 1_000_000);
    }

    /**
     * 创建默认 EWMA 预测器（α = 0.3）。
     * Create default EWMA predictor.
     *
     * @return EWMA 预测器 / EWMA predictor
     */
    public static EwmaPredictor defaultPredictor() {
        return new EwmaPredictor(DEFAULT_ALPHA);
    }

    /**
     * 更新 EWMA 值并检测趋势。
     * Update EWMA value and detect trend.
     *
     * @param newValue 新的观测值 / new observed value
     * @return 更新后的 EWMA 值 / updated EWMA value
     */
    public long update(long newValue) {
        lastValue = ewmaValue.get();

        // EWMA 计算：使用定点数运算避免浮点数
        // EWMA = α × newValue + (1-α) × lastValue
        long delta = newValue - lastValue;
        long scaledDelta = (long) (alpha * delta * 1_000_000);
        long newEwma = lastValue + scaledDelta / 1_000_000;

        ewmaValue.set(newEwma);

        // 检测趋势方向
        updateTrendDirection(newValue);

        return newEwma;
    }

    /**
     * 批量更新（适用于批量数据处理）。
     * Batch update for batch data processing.
     *
     * @param values 一批观测值 / batch of observed values
     * @return 最终的 EWMA 值 / final EWMA value
     */
    public long batchUpdate(long... values) {
        long result = 0;
        for (long value : values) {
            result = update(value);
        }
        return result;
    }

    /**
     * 获取当前 EWMA 值。
     * Get current EWMA value.
     *
     * @return 当前 EWMA 值 / current EWMA value
     */
    public long getEwma() {
        return ewmaValue.get();
    }

    /**
     * 获取趋势方向。
     * Get trend direction.
     *
     * @return 1=上升，-1=下降，0=平稳 / 1=up, -1=down, 0=stable
     */
    public int getTrendDirection() {
        return trendDirection;
    }

    /**
     * 获取连续同方向趋势的次数。
     * Get consecutive trend count in the same direction.
     *
     * @return 连续次数 / consecutive count
     */
    public int getConsecutiveTrendCount() {
        return consecutiveTrendCount;
    }

    /**
     * 判断是否处于快速上升趋势。
     * Check if in rapid uptrend.
     *
     * @param threshold 连续上升次数阈值 / consecutive uptrend threshold
     * @return 是否快速上升 / whether in rapid uptrend
     */
    public boolean isRapidUptrend(int threshold) {
        return trendDirection == 1 && consecutiveTrendCount >= threshold;
    }

    /**
     * 判断是否处于快速下降趋势。
     * Check if in rapid downtrend.
     *
     * @param threshold 连续下降次数阈值 / consecutive downtrend threshold
     * @return 是否快速下降 / whether in rapid downtrend
     */
    public boolean isRapidDowntrend(int threshold) {
        return trendDirection == -1 && consecutiveTrendCount >= threshold;
    }

    /**
     * 预测下一个值（基于当前趋势）。
     * Predict next value based on current trend.
     *
     * @return 预测值 / predicted value
     */
    public long predictNext() {
        long current = ewmaValue.get();
        long last = lastValue;

        if (last == 0) {
            return current;
        }

        // 计算变化率
        double changeRate = (double) (current - last) / last;

        // 基于趋势方向调整预测
        return switch (trendDirection) {
            case 1 -> (long) (current * (1 + changeRate * 1.2)); // 上升趋势，略微高估
            case -1 -> (long) (current * (1 + changeRate * 0.8)); // 下降趋势，略微低估
            default -> current; // 平稳趋势，保持当前值
        };
    }

    /**
     * 计算预测增长率（百分比）。
     * Calculate predicted growth rate (percentage).
     *
     * @return 增长率（-100 到 +100）/ growth rate
     */
    public double getPredictedGrowthRate() {
        long current = ewmaValue.get();
        long last = lastValue;

        if (last == 0 || current == 0) {
            return 0;
        }

        return (double) (current - last) / last * 100;
    }

    /**
     * 重置 EWMA 和趋势状态。
     * Reset EWMA and trend state.
     *
     * @param initialValue 初始值 / initial value
     */
    public void reset(long initialValue) {
        ewmaValue.set(initialValue);
        lastValue = initialValue;
        trendDirection = 0;
        consecutiveTrendCount = 0;
    }

    /**
     * 获取平滑因子 α。
     * Get smoothing factor alpha.
     *
     * @return α 值 / alpha value
     */
    public double getAlpha() {
        return alpha;
    }

    private void updateTrendDirection(long newValue) {
        long diff = newValue - lastValue;

        // 使用 5% 作为平稳阈值，避免噪声干扰
        double stableThreshold = Math.abs(lastValue) * 0.05;

        if (diff > stableThreshold) {
            if (trendDirection == 1) {
                consecutiveTrendCount++;
            } else {
                trendDirection = 1;
                consecutiveTrendCount = 1;
            }
        } else if (diff < -stableThreshold) {
            if (trendDirection == -1) {
                consecutiveTrendCount++;
            } else {
                trendDirection = -1;
                consecutiveTrendCount = 1;
            }
        } else {
            trendDirection = 0;
            consecutiveTrendCount = 0;
        }
    }
}
