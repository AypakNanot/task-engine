package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.RejectionPolicy;
import com.aypak.taskengine.core.TaskConfig;
import com.aypak.taskengine.core.TaskType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Factory for creating isolated thread pools per task type.
 */
public class TaskThreadPoolFactory {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * Create ThreadPoolTaskExecutor for given task config.
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

        boolean daemon = config.getTaskType() == TaskType.INIT;
        executor.setThreadFactory(new NamedThreadFactory(
                config.getTaskType().getPrefix(),
                config.getTaskName(),
                daemon
        ));

        executor.setAllowCoreThreadTimeOut(config.getTaskType() == TaskType.HIGH_FREQ);
        executor.initialize();
        return executor;
    }

    /**
     * Create ThreadPoolTaskScheduler for CRON type tasks.
     */
    public ThreadPoolTaskScheduler createScheduler(TaskConfig config) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        int poolSize = config.getCorePoolSize() != null
                ? config.getCorePoolSize()
                : getDefaultCoreSize(TaskType.CRON);

        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(TaskType.CRON.getPrefix() + "-" + config.getTaskName() + "-");

        RejectionPolicy policy = config.getRejectionPolicy();
        scheduler.setRejectedExecutionHandler(createRejectionHandler(policy, config.getTaskName()));
        scheduler.setThreadFactory(new NamedThreadFactory(
                TaskType.CRON.getPrefix(),
                config.getTaskName(),
                false
        ));

        scheduler.initialize();
        return scheduler;
    }

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

    private int getDefaultCoreSize(TaskType type) {
        return switch (type) {
            case INIT -> 1;
            case CRON -> 4;
            case HIGH_FREQ -> CPU_COUNT * 2;
            case BACKGROUND -> 2;
        };
    }

    private int getDefaultMaxSize(TaskType type) {
        return switch (type) {
            case INIT -> CPU_COUNT;
            case CRON -> 4;
            case HIGH_FREQ -> CPU_COUNT * 4;
            case BACKGROUND -> 4;
        };
    }

    private int getDefaultQueueCapacity(TaskType type) {
        return switch (type) {
            case INIT -> 0;
            case CRON -> 0;
            case HIGH_FREQ -> 10000;
            case BACKGROUND -> 100;
        };
    }

    /**
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