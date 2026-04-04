package com.aypak.taskengine.circuitbreaker;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器实现，用于防止级联失败。
 * Circuit breaker implementation to prevent cascade failures.
 *
 * <p>三种状态：</p>
 * <ul>
 *     <li>CLOSED - 正常状态，请求通过</li>
 *     <li>OPEN - 熔断状态，拒绝请求</li>
 *     <li>HALF_OPEN - 半开状态，允许测试请求</li>
 * </ul>
 */
@Slf4j
public class CircuitBreaker {

    /**
     * 熔断器状态枚举。
     */
    public enum State {
        /** 正常状态，请求通过 */
        CLOSED,
        /** 熔断状态，拒绝请求 */
        OPEN,
        /** 半开状态，允许测试请求 */
        HALF_OPEN
    }

    // 配置参数
    private final int failureThreshold;      // 失败阈值
    private final double failureRateThreshold; // 失败率阈值 (0-1)
    private final long openTimeout;          // 熔断超时（毫秒）
    private final int halfOpenMaxCalls;      // 半开状态最大请求数

    // 状态变量
    private volatile State state = State.CLOSED;
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong openStartTime = new AtomicLong(0);
    private final AtomicInteger halfOpenCalls = new AtomicInteger(0);

    /**
     * 创建熔断器。
     * @param failureThreshold 失败次数阈值
     * @param failureRateThreshold 失败率阈值 (0-1)
     * @param openTimeoutMs 熔断超时（毫秒）
     * @param halfOpenMaxCalls 半开状态最大请求数
     */
    public CircuitBreaker(int failureThreshold, double failureRateThreshold,
                          long openTimeoutMs, int halfOpenMaxCalls) {
        this.failureThreshold = failureThreshold;
        this.failureRateThreshold = failureRateThreshold;
        this.openTimeout = openTimeoutMs;
        this.halfOpenMaxCalls = halfOpenMaxCalls;
    }

    /**
     * 创建默认熔断器。
     * @return 默认熔断器
     */
    public static CircuitBreaker defaultBreaker() {
        return new CircuitBreaker(5, 0.5, 30000, 3);
    }

    /**
     * 检查是否允许请求通过。
     * @return 是否允许通过
     */
    public boolean allowRequest() {
        State currentState = getState();

        switch (currentState) {
            case CLOSED:
                return true;
            case OPEN:
                // 检查是否可以进入半开状态
                if (System.currentTimeMillis() - openStartTime.get() >= openTimeout) {
                    transitionToHalfOpen();
                    return true;
                }
                return false;
            case HALF_OPEN:
                // 半开状态限制请求数
                return halfOpenCalls.incrementAndGet() <= halfOpenMaxCalls;
            default:
                return false;
        }
    }

    /**
     * 记录成功请求。
     */
    public void recordSuccess() {
        successCount.incrementAndGet();
        totalCount.incrementAndGet();

        State currentState = getState();
        if (currentState == State.HALF_OPEN) {
            // 半开状态下成功，关闭熔断器
            transitionToClosed();
        }
    }

    /**
     * 记录失败请求。
     */
    public void recordFailure() {
        failureCount.incrementAndGet();
        totalCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        State currentState = getState();

        if (currentState == State.HALF_OPEN) {
            // 半开状态下失败，重新打开熔断器
            transitionToOpen();
        } else if (currentState == State.CLOSED) {
            // 检查是否达到熔断条件
            if (shouldTrip()) {
                transitionToOpen();
            }
        }
    }

    /**
     * 手动重置熔断器。
     */
    public void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        totalCount.set(0);
        lastFailureTime.set(0);
        openStartTime.set(0);
        halfOpenCalls.set(0);
        log.info("Circuit breaker manually reset");
    }

    /**
     * 获取当前状态。
     * @return 熔断器状态
     */
    public State getState() {
        // 检查是否需要从 OPEN 转换到 HALF_OPEN
        if (state == State.OPEN &&
            System.currentTimeMillis() - openStartTime.get() >= openTimeout) {
            transitionToHalfOpen();
        }
        return state;
    }

    /**
     * 获取失败率。
     * @return 失败率 (0-1)
     */
    public double getFailureRate() {
        long total = totalCount.get();
        if (total == 0) {
            return 0;
        }
        return (double) failureCount.get() / total;
    }

    /**
     * 获取当前统计信息。
     * @return 统计信息
     */
    public CircuitBreakerStats getStats() {
        return new CircuitBreakerStats(
            state,
            failureCount.get(),
            successCount.get(),
            totalCount.get(),
            getFailureRate()
        );
    }

    /**
     * 判断是否应该触发熔断。
     */
    private boolean shouldTrip() {
        long total = totalCount.get();
        long failures = failureCount.get();

        // 检查是否达到最小请求数
        if (total < 10) {
            return false;
        }

        // 检查失败次数阈值
        if (failures >= failureThreshold) {
            return true;
        }

        // 检查失败率阈值
        double failureRate = (double) failures / total;
        return failureRate >= failureRateThreshold;
    }

    /**
     * 转换到 OPEN 状态。
     */
    private void transitionToOpen() {
        state = State.OPEN;
        openStartTime.set(System.currentTimeMillis());
        halfOpenCalls.set(0);
        log.warn("Circuit breaker opened: failures={}, failureRate={}%",
                failureCount.get(), String.format("%.1f", getFailureRate() * 100));
    }

    /**
     * 转换到 HALF_OPEN 状态。
     */
    private void transitionToHalfOpen() {
        state = State.HALF_OPEN;
        halfOpenCalls.set(0);
        log.info("Circuit breaker entered half-open state");
    }

    /**
     * 转换到 CLOSED 状态。
     */
    private void transitionToClosed() {
        state = State.CLOSED;
        failureCount.set(0);
        totalCount.set(0);
        halfOpenCalls.set(0);
        log.info("Circuit breaker closed");
    }

    /**
     * 熔断器统计信息。
     */
    public static class CircuitBreakerStats {
        private final State state;
        private final long failureCount;
        private final long successCount;
        private final long totalCount;
        private final double failureRate;

        public CircuitBreakerStats(State state, long failureCount, long successCount,
                                   long totalCount, double failureRate) {
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.totalCount = totalCount;
            this.failureRate = failureRate;
        }

        public State getState() { return state; }
        public long getFailureCount() { return failureCount; }
        public long getSuccessCount() { return successCount; }
        public long getTotalCount() { return totalCount; }
        public double getFailureRate() { return failureRate; }
    }
}
