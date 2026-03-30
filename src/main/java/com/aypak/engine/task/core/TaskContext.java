package com.aypak.engine.task.core;

import lombok.Getter;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务执行上下文。
 * 捕获调用者上下文以便传播到异步线程。
 * Task execution context.
 * Captures caller context for propagation to async thread.
 */
@Getter
public class TaskContext {

    /**
     * 来自 MDC 的追踪 ID，用于请求追踪。
     * Trace ID from MDC for request tracing.
     */
    private final String traceId;

    /**
     * 完整的 MDC 上下文副本，用于传播。
     * Full MDC context map copy for propagation.
     */
    private final Map<String, String> baggage;

    /**
     * 任务提交时间戳（毫秒）。
     * Task submission timestamp (milliseconds).
     */
    private final long submitTime;

    /**
     * 创建上下文，捕获当前 MDC 状态。
     * Create context capturing current MDC state.
     */
    public TaskContext() {
        this.traceId = MDC.get("traceId");
        Map<String, String> mdcCopy = MDC.getCopyOfContextMap();
        this.baggage = mdcCopy != null ? new HashMap<>(mdcCopy) : new HashMap<>();
        this.submitTime = System.currentTimeMillis();
    }

    /**
     * 使用显式值创建上下文。
     * Create context with explicit values.
     *
     * @param traceId   显式追踪 ID / explicit trace ID
     * @param baggage   显式上下文映射 / explicit baggage map
     * @param submitTime 显式提交时间 / explicit submit time
     */
    public TaskContext(String traceId, Map<String, String> baggage, long submitTime) {
        this.traceId = traceId;
        this.baggage = baggage != null ? new HashMap<>(baggage) : new HashMap<>();
        this.submitTime = submitTime;
    }

    /**
     * 将此上下文传播到当前线程的 MDC。
     * 由 TaskDecorator 在任务执行前调用。
     * Propagate this context to the current thread's MDC.
     * Called by TaskDecorator before task execution.
     */
    public void propagate() {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        MDC.setContextMap(Collections.unmodifiableMap(baggage));
    }

    /**
     * 任务执行后清除 MDC 上下文。
     * 防止同一线程上任务之间的上下文泄漏。
     * Clear MDC context after task execution.
     * Prevents context leakage between tasks on same thread.
     */
    public void clear() {
        MDC.clear();
    }

    /**
     * 计算自提交以来经过的时间。
     * Calculate elapsed time since submission.
     *
     * @return 经过的毫秒数 / elapsed milliseconds
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - submitTime;
    }
}