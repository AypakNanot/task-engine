package com.aypak.engine.task.executor;

import com.aypak.engine.task.core.TaskContext;
import com.aypak.engine.task.core.TaskType;
import com.aypak.engine.task.monitor.TaskMetrics;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池的优化包装器，最小化热路径开销。
 * Optimized wrapper around thread pool with minimal overhead in hot path.
 */
@Slf4j
@Getter
public class TaskExecutor {

    private final String taskName;
    private final TaskType taskType;
    private final Executor executor;
    private final ThreadPoolExecutor threadPoolExecutor;
    private final TaskMetrics metrics;
    private final ThreadPoolTaskScheduler scheduler;

    // 缓存值以避免重复计算 / Cached values to avoid repeated calculations
    private volatile int cachedQueueCapacity = 0;

    public TaskExecutor(ThreadPoolTaskExecutor executor, String taskName, TaskType taskType, TaskMetrics metrics) {
        this.taskName = taskName;
        this.taskType = taskType;
        this.executor = executor.getThreadPoolExecutor();
        this.threadPoolExecutor = executor.getThreadPoolExecutor();
        this.metrics = metrics;
        this.scheduler = null;
    }

    public TaskExecutor(ThreadPoolTaskScheduler scheduler, String taskName, TaskType taskType, TaskMetrics metrics) {
        this.taskName = taskName;
        this.taskType = taskType;
        this.executor = scheduler.getScheduledExecutor();
        this.threadPoolExecutor = (ThreadPoolExecutor) scheduler.getScheduledExecutor();
        this.metrics = metrics;
        this.scheduler = scheduler;
    }

    /**
     * 执行任务，最小化开销 - 针对高吞吐量优化。
     * Execute task with minimal overhead - optimized for high throughput.
     */
    public void execute(Runnable runnable, TaskContext context) {
        Runnable wrapped = wrapWithContextPropagation(runnable, context);

        executor.execute(() -> {
            long startTime = System.nanoTime(); // 使用 nanoTime 以获得更好的精度 / Use nanoTime for better precision
            try {
                wrapped.run();
                long executionNs = System.nanoTime() - startTime;
                metrics.recordSuccess(executionNs / 1_000_000); // 转换为毫秒 / Convert to ms
            } catch (Throwable e) {
                metrics.recordFailure();
                // 仅在 WARN 级别记录失败 / Only log failures at WARN level
                log.warn("[{}] Task failed: {}", Thread.currentThread().getName(), e.getMessage());
                throw e;
            }
        });
    }

    /**
     * 轻量级上下文传播，无对象创建开销。
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
     * 批量更新线程池指标 - 定期调用，而非每个任务调用。
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
        return threadPoolExecutor;
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
                log.warn("[{}] 关闭超时，强制关闭。剩余任务：{}",
                        taskName, executor.getQueue().size());
                executor.shutdownNow();
            } else {
                log.info("[{}] 优雅关闭完成", taskName);
            }
        } catch (InterruptedException e) {
            log.warn("[{}] 关闭被中断", taskName);
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /**
     * 检查线程池是否已终止。
     * Check if thread pool is terminated.
     */
    public boolean isTerminated() {
        ThreadPoolExecutor pool = getThreadPool();
        return pool != null ? pool.isTerminated() : true;
    }

    /**
     * 获取活动线程数。
     * Get active thread count.
     */
    public int getActiveThreads() {
        ThreadPoolExecutor pool = getThreadPool();
        return pool != null ? pool.getActiveCount() : 0;
    }

    /**
     * 设置核心线程池大小。
     * Set core pool size.
     */
    public void setCorePoolSize(int size) {
        ThreadPoolExecutor pool = getThreadPool();
        if (pool != null) {
            if (size > pool.getMaximumPoolSize()) {
                pool.setMaximumPoolSize(size);
            }
            pool.setCorePoolSize(size);
            log.info("[{}] 核心线程池大小已更改为 {}", taskName, size);
        }
    }

    /**
     * 设置最大线程池大小。
     * Set max pool size.
     */
    public void setMaxPoolSize(int size) {
        ThreadPoolExecutor pool = getThreadPool();
        if (pool != null) {
            if (size < pool.getCorePoolSize()) {
                pool.setCorePoolSize(size);
            }
            pool.setMaximumPoolSize(size);
            metrics.updateCurrentMaxPoolSize(size);
            cachedQueueCapacity = 0; // 重置缓存 / Reset cache
            log.info("[{}] 最大线程池大小已更改为 {}", taskName, size);
        }
    }

    /**
     * 获取最大线程池大小。
     * Get max pool size.
     */
    public int getMaxPoolSize() {
        ThreadPoolExecutor pool = getThreadPool();
        return pool != null ? pool.getMaximumPoolSize() : 0;
    }

    /**
     * 获取调度器（用于 CRON 任务）。
     * Get scheduler for CRON tasks.
     */
    public ThreadPoolTaskScheduler getScheduler() {
        return scheduler;
    }
}