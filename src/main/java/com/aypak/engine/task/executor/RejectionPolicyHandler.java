package com.aypak.engine.task.executor;

import com.aypak.engine.task.core.RejectionPolicy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 拒绝策略处理器工厂。
 * Rejection policy handler factory.
 */
@Slf4j
public class RejectionPolicyHandler {

    /**
     * 根据拒绝策略创建处理器。
     * Create handler based on rejection policy.
     *
     * @param policy   拒绝策略 / rejection policy
     * @param taskName 任务名称 / task name
     * @return 拒绝执行处理器 / rejected execution handler
     */
    public static RejectedExecutionHandler create(RejectionPolicy policy, String taskName) {
        switch (policy) {
            case ABORT_WITH_ALERT:
                return new AbortWithAlertHandler(taskName);
            case CALLER_RUNS:
                return new ThreadPoolExecutor.CallerRunsPolicy();
            case DISCARD_OLDEST:
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case BLOCK_WAIT:
                return new BlockAndWaitHandler(taskName);
            default:
                return new ThreadPoolExecutor.AbortPolicy();
        }
    }

    /**
     * 带告警的终止处理器。
     * Abort handler with alert.
     */
    private static class AbortWithAlertHandler implements RejectedExecutionHandler {
        private final String taskName;

        AbortWithAlertHandler(String taskName) {
            this.taskName = taskName;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("[{}] Task rejected (queue full), dropping task. Active: {}, Queue: {}",
                    taskName, executor.getActiveCount(), executor.getQueue().size());
            throw new RejectedExecutionException("Task " + taskName + " rejected due to queue overflow");
        }
    }

    /**
     * 阻塞等待处理器。
     * Block and wait handler.
     */
    private static class BlockAndWaitHandler implements RejectedExecutionHandler {
        private final String taskName;

        BlockAndWaitHandler(String taskName) {
            this.taskName = taskName;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            try {
                log.debug("[{}] Queue full, blocking caller until space available", taskName);
                executor.getQueue().put(r);
            } catch (InterruptedException e) {
                log.warn("[{}] Blocked submission interrupted", taskName);
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Task submission interrupted", e);
            }
        }
    }
}