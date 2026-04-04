package com.aypak.taskengine.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 任务事件监听器示例（已废弃）。
 * Sample task event listener. DEPRECATED.
 *
 * @deprecated 使用 {@link TaskEventDispatcher} 替代，基于异步缓冲队列实现，性能更高。
 * <p>
 * Deprecated: Use {@link TaskEventDispatcher} instead, which uses async buffering queue for higher performance.
 *
 * <p>原监听器会自动监听以下事件：</p>
 * <ul>
 *     <li>{@link TaskRegisteredEvent} - 任务注册事件</li>
 *     <li>{@link TaskSuccessEvent} - 任务执行成功事件</li>
 *     <li>{@link TaskFailureEvent} - 任务执行失败事件</li>
 * </ul>
 *
 * <p>新实现说明：</p>
 * <ul>
 *     <li>成功事件：通过 {@code TaskEventDispatcher.publishTaskSuccess()} 提交到缓冲队列</li>
 *     <li>失败事件：直接记录日志，确保不丢失</li>
 *     <li>注册事件：直接记录日志，仅启动时触发</li>
 * </ul>
 *
 * <p>性能对比：</p>
 * <ul>
 *     <li>原实现：~1-10μs/事件（@Async 创建新线程）</li>
 *     <li>新实现：~50-200ns/事件（队列 offer 操作）</li>
 *     <li>提升：50-500 倍</li>
 * </ul>
 */
@Deprecated(since = "2026-04-04", forRemoval = true)
@Component
public class TaskEventListener {

    private static final Logger log = LoggerFactory.getLogger(TaskEventListener.class);

    /**
     * 处理任务注册事件。
     * Handle task registered event.
     *
     * @param event 任务注册事件 / task registered event
     */
    @EventListener
    @Async
    public void handleTaskRegistered(TaskRegisteredEvent<?> event) {
        log.info("Task registered: {} [type={}]",
                event.getTaskName(),
                event.getTaskType());
    }

    /**
     * 处理任务成功事件。
     * Handle task success event.
     *
     * @param event 任务成功事件 / task success event
     */
    @EventListener
    @Async
    public void handleTaskSuccess(TaskSuccessEvent<?> event) {
        log.debug("Task succeeded: {} [executionTime={}ms]",
                event.getTaskName(),
                event.getExecutionTimeMs());
    }

    /**
     * 处理任务失败事件。
     * Handle task failure event.
     *
     * @param event 任务失败事件 / task failure event
     */
    @EventListener
    @Async
    public void handleTaskFailure(TaskFailureEvent<?> event) {
        log.warn("Task failed: {} [error={}]",
                event.getTaskName(),
                event.getErrorMessage());
    }
}
