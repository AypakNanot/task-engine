package com.aypak.taskengine.core;

import lombok.Getter;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Task execution context.
 * Captures caller context for propagation to async thread.
 */
@Getter
public class TaskContext {

    /**
     * Trace ID from MDC for request tracing.
     */
    private final String traceId;

    /**
     * Full MDC context map copy for propagation.
     */
    private final Map<String, String> baggage;

    /**
     * Task submission timestamp (milliseconds).
     */
    private final long submitTime;

    /**
     * Create context capturing current MDC state.
     */
    public TaskContext() {
        this.traceId = MDC.get("traceId");
        Map<String, String> mdcCopy = MDC.getCopyOfContextMap();
        this.baggage = mdcCopy != null ? new HashMap<>(mdcCopy) : new HashMap<>();
        this.submitTime = System.currentTimeMillis();
    }

    /**
     * Create context with explicit values.
     *
     * @param traceId   explicit trace ID
     * @param baggage   explicit baggage map
     * @param submitTime explicit submit time
     */
    public TaskContext(String traceId, Map<String, String> baggage, long submitTime) {
        this.traceId = traceId;
        this.baggage = baggage != null ? new HashMap<>(baggage) : new HashMap<>();
        this.submitTime = submitTime;
    }

    /**
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
     * Clear MDC context after task execution.
     * Prevents context leakage between tasks on same thread.
     */
    public void clear() {
        MDC.clear();
    }

    /**
     * Calculate elapsed time since submission.
     *
     * @return elapsed milliseconds
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - submitTime;
    }
}