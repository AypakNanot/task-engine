package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.TaskContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;

/**
 * 用于异步边界上下文传播的任务装饰器。
 * 将 MDC 上下文从提交线程传播到执行线程。
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
            // 执行前传播上下文 / Propagate context before execution
            try {
                if (context != null) {
                    context.propagate();
                }
                r.run();
            } finally {
                // 执行后清除上下文，防止泄露 / Clear context after execution to prevent leakage
                if (context != null) {
                    context.clear();
                }
            }
        };
        return defaultFactory.newThread(wrappedRunnable);
    }

    /**
     * 使用上下文传播包装 Runnable。
     * Wrap a runnable with context propagation.
     *
     * @param runnable 原始 Runnable / original runnable
     * @param context  任务上下文 / task context
     * @return 带有 MDC 传播的包装 Runnable / wrapped runnable with MDC propagation
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
     * 捕获当前 MDC 上下文用于传播。
     * Capture current MDC context for propagation.
     *
     * @return 带有当前 MDC 状态的新 TaskContext / new TaskContext with current MDC state
     */
    public static TaskContext captureContext() {
        String traceId = MDC.get("traceId");
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return new TaskContext(traceId, contextMap, System.currentTimeMillis());
    }
}