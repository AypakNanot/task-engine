package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.RejectionPolicy;
import com.aypak.taskengine.core.TaskConfig;
import com.aypak.taskengine.core.TaskType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 用于为每个任务类型创建隔离线程池的工厂。
 * Factory for creating isolated thread pools per task type.
 */
public class TaskThreadPoolFactory {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * 为给定任务配置创建 ThreadPoolTaskExecutor。
     * Create ThreadPoolTaskExecutor for given task config.
     *
     * @param config 任务配置 / task configuration
     * @return 线程池执行器 / thread pool executor
     */
    public ThreadPoolTaskExecutor createExecutor(TaskConfig config) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int coreSize = config.getCorePoolSize() != null
                ? config.getCorePoolSize()
                : getDefaultCoreSize(config.getTaskType());
        int maxSize = config.getMaxPoolSize() != null
                ? config.getMaxPoolSize()
                : getDefaultMaxSize(config.getTaskType());
        int queueCapacity = config.getQueueCapacity() != null
                ? config.getQueueCapacity()
                : getDefaultQueueCapacity(config.getTaskType());

        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(config.getTaskType().getPrefix() + "-" + config.getTaskName() + "-");

        RejectionPolicy policy = config.getRejectionPolicy();
        executor.setRejectedExecutionHandler(createRejectionHandler(policy, config.getTaskName()));

        boolean daemon = config.getTaskType() == TaskType.CPU_BOUND;
        executor.setThreadFactory(new NamedThreadFactory(
                config.getTaskType().getPrefix(),
                config.getTaskName(),
                daemon
        ));

        executor.setAllowCoreThreadTimeOut(config.getTaskType() == TaskType.IO_BOUND || config.getTaskType() == TaskType.HYBRID);
        executor.initialize();
        return executor;
    }

    /**
     * 为 CRON 类型任务创建 ThreadPoolTaskScheduler。
     * Create ThreadPoolTaskScheduler for CRON type tasks.
     *
     * @param config 任务配置 / task configuration
     * @return 线程池调度器 / thread pool scheduler
     */
    public ThreadPoolTaskScheduler createScheduler(TaskConfig config) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        int poolSize = config.getCorePoolSize() != null
                ? config.getCorePoolSize()
                : getDefaultCoreSize(TaskType.SCHEDULED);

        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(TaskType.SCHEDULED.getPrefix() + "-" + config.getTaskName() + "-");

        RejectionPolicy policy = config.getRejectionPolicy();
        scheduler.setRejectedExecutionHandler(createRejectionHandler(policy, config.getTaskName()));
        scheduler.setThreadFactory(new NamedThreadFactory(
                TaskType.SCHEDULED.getPrefix(),
                config.getTaskName(),
                false
        ));

        scheduler.initialize();
        return scheduler;
    }

    /**
     * 创建拒绝执行处理器。
     * Create rejected execution handler.
     *
     * @param policy  拒绝策略 / rejection policy
     * @param taskName 任务名称 / task name
     * @return 拒绝执行处理器 / rejected execution handler
     */
    private RejectedExecutionHandler createRejectionHandler(RejectionPolicy policy, String taskName) {
        if (policy == null) {
            return new ThreadPoolExecutor.AbortPolicy();
        }
        switch (policy) {
            case ABORT_WITH_ALERT:
                return new ThreadPoolExecutor.AbortPolicy();
            case CALLER_RUNS:
                return new ThreadPoolExecutor.CallerRunsPolicy();
            case DISCARD_OLDEST:
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case BLOCK_WAIT:
                return new BlockWaitPolicy();
            default:
                return new ThreadPoolExecutor.AbortPolicy();
        }
    }

    /**
     * 获取任务类型的默认核心线程数。
     * Get default core thread count for task type.
     */
    private int getDefaultCoreSize(TaskType type) {
        return switch (type) {
            case CPU_BOUND -> CPU_COUNT;
            case IO_BOUND -> 16;
            case HYBRID -> 8;
            case SCHEDULED -> 4;
            case BATCH -> 2;
        };
    }

    /**
     * 获取任务类型的默认最大线程数。
     * Get default max thread count for task type.
     */
    private int getDefaultMaxSize(TaskType type) {
        return switch (type) {
            case CPU_BOUND -> CPU_COUNT * 2;
            case IO_BOUND -> 64;
            case HYBRID -> 16;
            case SCHEDULED -> 4;
            case BATCH -> 4;
        };
    }

    /**
     * 获取任务类型的默认队列容量。
     * Get default queue capacity for task type.
     */
    private int getDefaultQueueCapacity(TaskType type) {
        return switch (type) {
            case CPU_BOUND -> 100;
            case IO_BOUND -> 1000;
            case HYBRID -> 500;
            case SCHEDULED -> 0;
            case BATCH -> 10000;
        };
    }

    /**
     * 自定义拒绝策略，阻塞直到有空闲位置。
     * Custom rejection policy that blocks until space available.
     */
    private static class BlockWaitPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            try {
                executor.getQueue().put(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task submission interrupted", e);
            }
        }
    }
}