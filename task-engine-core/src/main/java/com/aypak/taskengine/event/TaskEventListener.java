package com.aypak.taskengine.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 任务事件监听器示例。
 * Sample task event listener.
 *
 * <p>监听器会自动监听以下事件：</p>
 * <ul>
 *     <li>{@link TaskRegisteredEvent} - 任务注册事件</li>
 *     <li>{@link TaskSuccessEvent} - 任务执行成功事件</li>
 *     <li>{@link TaskFailureEvent} - 任务执行失败事件</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @Component
 * public class CustomTaskEventListener {
 *
 *     @EventListener
 *     @Async
 *     public void handleTaskSuccess(TaskSuccessEvent<?> event) {
 *         log.info("Task {} succeeded in {}ms",
 *                  event.getTaskName(), event.getExecutionTimeMs());
 *     }
 * }
 * }</pre>
 */
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
        log.info("Task registered: {} [type={}, priority={}]",
                event.getTaskName(),
                event.getTaskType(),
                event.getPriority());
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
