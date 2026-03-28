package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.TaskContext;
import com.aypak.taskengine.core.TaskType;
import com.aypak.taskengine.monitor.TaskMetrics;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Optimized wrapper around thread pool with minimal overhead in hot path.
 */
@Slf4j
@Getter
public class TaskExecutor {

    private final String taskName;
    private final TaskType taskType;
    private final Executor executor;
    private final ThreadPoolExecutor threadPoolExecutor;
    private final ScheduledThreadPoolExecutor scheduledExecutor;
    private final TaskMetrics metrics;
    private final ThreadPoolTaskScheduler scheduler;
    private final ThreadPoolTaskExecutor taskExecutor;

    // Cached values to avoid repeated calculations
    private volatile int cachedQueueCapacity = 0;

    public TaskExecutor(ThreadPoolTaskExecutor executor, String taskName, TaskType taskType, TaskMetrics metrics) {
        this.taskName = taskName;
        this.taskType = taskType;
        this.executor = executor.getThreadPoolExecutor();
        this.threadPoolExecutor = executor.getThreadPoolExecutor();
        this.scheduledExecutor = null;
        this.metrics = metrics;
        this.scheduler = null;
        this.taskExecutor = executor;
    }

    public TaskExecutor(ThreadPoolTaskScheduler scheduler, String taskName, TaskType taskType, TaskMetrics metrics) {
        this.taskName = taskName;
        this.taskType = taskType;
        this.executor = scheduler.getScheduledExecutor();
        this.threadPoolExecutor = null;
        this.scheduledExecutor = (ScheduledThreadPoolExecutor) scheduler.getScheduledExecutor();
        this.metrics = metrics;
        this.scheduler = scheduler;
        this.taskExecutor = null;
    }

    /**
     * Execute task with minimal overhead - optimized for high throughput.
     */
    public void execute(Runnable runnable, TaskContext context) {
        Runnable wrapped = wrapWithContextPropagation(runnable, context);

        executor.execute(() -> {
            long startTime = System.nanoTime(); // Use nanoTime for better precision
            try {
                wrapped.run();
                long executionNs = System.nanoTime() - startTime;
                metrics.recordSuccess(executionNs / 1_000_000); // Convert to ms
            } catch (Throwable e) {
                metrics.recordFailure();
                // Only log failures at WARN level
                log.warn("[{}] Task failed: {}", Thread.currentThread().getName(), e.getMessage());
                throw e;
            }
        });
    }

    /**
     * Lighter context propagation without object creation overhead.
     */
    private Runnable wrapWithContextPropagation(Runnable runnable, TaskContext context) {
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
     * Batch update pool metrics - call periodically, not per-task.
     */
    public void updatePoolMetrics() {
        ThreadPoolExecutor pool = getThreadPool();
        if (pool != null) {
            metrics.setQueueDepth(pool.getQueue().size());
            metrics.setActiveThreads(pool.getActiveCount());
        }
    }

    private ThreadPoolExecutor getThreadPool() {
        if (threadPoolExecutor != null) return threadPoolExecutor;
        if (scheduledExecutor != null) return scheduledExecutor;
        if (taskExecutor != null) return taskExecutor.getThreadPoolExecutor();
        return null;
    }

    public int getQueueSize() {
        ThreadPoolExecutor pool = getThreadPool();
        return pool != null ? pool.getQueue().size() : 0;
    }

    public int getQueueCapacity() {
        // Return cached value if available
        if (cachedQueueCapacity > 0) {
            return cachedQueueCapacity;
        }

        ThreadPoolExecutor pool = getThreadPool();
        if (pool != null) {
            int size = pool.getQueue().size();
            int remaining = pool.getQueue().remainingCapacity();
            cachedQueueCapacity = size + remaining;
            return cachedQueueCapacity;
        }
        return 0;
    }

    public void shutdown(long timeout) {
        ThreadPoolExecutor pool = getThreadPool();
        if (pool != null) {
            pool.shutdown();
            tryAwaitTermination(pool, timeout);
        }
    }

    private void tryAwaitTermination(ThreadPoolExecutor executor, long timeout) {
        try {
            if (!executor.awaitTermination(timeout, TimeUnit.SECONDS)) {
                log.warn("[{}] Shutdown timeout exceeded, forcing shutdown. Remaining tasks: {}",
                        taskName, executor.getQueue().size());
                executor.shutdownNow();
            } else {
                log.info("[{}] Graceful shutdown completed", taskName);
            }
        } catch (InterruptedException e) {
            log.warn("[{}] Shutdown interrupted", taskName);
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public boolean isTerminated() {
        ThreadPoolExecutor pool = getThreadPool();
        return pool != null ? pool.isTerminated() : true;
    }

    public int getActiveThreads() {
        ThreadPoolExecutor pool = getThreadPool();
        return pool != null ? pool.getActiveCount() : 0;
    }

    public void setCorePoolSize(int size) {
        ThreadPoolExecutor pool = getThreadPool();
        if (pool != null) {
            if (size > pool.getMaximumPoolSize()) {
                pool.setMaximumPoolSize(size);
            }
            pool.setCorePoolSize(size);
            log.info("[{}] Core pool size changed to {}", taskName, size);
        }
    }

    public void setMaxPoolSize(int size) {
        ThreadPoolExecutor pool = getThreadPool();
        if (pool != null) {
            if (size < pool.getCorePoolSize()) {
                pool.setCorePoolSize(size);
            }
            pool.setMaximumPoolSize(size);
            metrics.updateCurrentMaxPoolSize(size);
            cachedQueueCapacity = 0; // Reset cache
            log.info("[{}] Max pool size changed to {}", taskName, size);
        }
    }

    public int getMaxPoolSize() {
        ThreadPoolExecutor pool = getThreadPool();
        return pool != null ? pool.getMaximumPoolSize() : 0;
    }

    public ThreadPoolTaskScheduler getScheduler() {
        return scheduler;
    }
}