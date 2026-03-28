package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.TaskContext;
import com.aypak.taskengine.core.TaskType;
import com.aypak.taskengine.monitor.TaskMetrics;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper around thread pool with metrics collection and context propagation.
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
        // ScheduledThreadPoolExecutor extends ThreadPoolExecutor, safe cast
        this.scheduledExecutor = (ScheduledThreadPoolExecutor) scheduler.getScheduledExecutor();
        this.metrics = metrics;
        this.scheduler = scheduler;
        this.taskExecutor = null;
    }

    public void execute(Runnable runnable, TaskContext context) {
        Runnable wrapped = wrapWithContextPropagation(runnable, context);

        executor.execute(() -> {
            long startTime = System.currentTimeMillis();
            try {
                wrapped.run();
                metrics.recordSuccess(System.currentTimeMillis() - startTime);
            } catch (Throwable e) {
                metrics.recordFailure();
                log.error("[{}] Task failed: {} - {}",
                        Thread.currentThread().getName(), taskName, e.getMessage(), e);
                throw e;
            }
        });

        updatePoolMetrics();
    }

    private Runnable wrapWithContextPropagation(Runnable runnable, TaskContext context) {
        return () -> {
            try {
                context.propagate();
                runnable.run();
            } finally {
                context.clear();
            }
        };
    }

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
        ThreadPoolExecutor pool = getThreadPool();
        if (pool != null) {
            int size = pool.getQueue().size();
            int remaining = pool.getQueue().remainingCapacity();
            return size + remaining;
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
            // Ensure core <= max
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
            // Ensure max >= core
            if (size < pool.getCorePoolSize()) {
                pool.setCorePoolSize(size);
            }
            pool.setMaximumPoolSize(size);
            metrics.updateCurrentMaxPoolSize(size);
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