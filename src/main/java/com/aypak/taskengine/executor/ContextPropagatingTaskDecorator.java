package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.TaskContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Task decorator for context propagation across async boundaries.
 * Propagates MDC context from submission thread to execution thread.
 */
@Slf4j
public class ContextPropagatingTaskDecorator implements java.util.concurrent.ThreadFactory {

    private final java.util.concurrent.ThreadFactory defaultFactory;
    private final TaskContext context;

    public ContextPropagatingTaskDecorator(java.util.concurrent.ThreadFactory defaultFactory, TaskContext context) {
        this.defaultFactory = defaultFactory;
        this.context = context;
    }

    @Override
    public Thread newThread(Runnable r) {
        Runnable wrappedRunnable = () -> {
            // Propagate context before execution
            try {
                if (context != null) {
                    context.propagate();
                }
                r.run();
            } finally {
                // Clear context after execution to prevent leakage
                if (context != null) {
                    context.clear();
                }
            }
        };
        return defaultFactory.newThread(wrappedRunnable);
    }

    /**
     * Wrap a runnable with context propagation.
     *
     * @param runnable original runnable
     * @param context  task context
     * @return wrapped runnable with MDC propagation
     */
    public static Runnable wrap(Runnable runnable, TaskContext context) {
        return () -> {
            try {
                if (context != null) {
                    context.propagate();
                }
                runnable.run();
            } finally {
                if (context != null) {
                    context.clear();
                }
            }
        };
    }

    /**
     * Capture current MDC context for propagation.
     *
     * @return new TaskContext with current MDC state
     */
    public static TaskContext captureContext() {
        String traceId = MDC.get("traceId");
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return new TaskContext(traceId, contextMap, System.currentTimeMillis());
    }
}