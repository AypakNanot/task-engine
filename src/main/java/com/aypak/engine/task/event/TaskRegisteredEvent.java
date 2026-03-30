package com.aypak.engine.task.event;

/**
 * 任务注册事件。
 * Event fired when a task is registered.
 *
 * @param <T> 任务负载类型 / task payload type
 */
public class TaskRegisteredEvent<T> extends TaskEvent<T> {

    private final String taskType;
    private final String priority;

    /**
     * 创建任务注册事件。
     * Create task registered event.
     *
     * @param source    事件源 / event source
     * @param taskName  任务名称 / task name
     * @param taskType  任务类型 / task type
     * @param priority  优先级 / priority
     * @param payload   任务负载 / task payload
     */
    public TaskRegisteredEvent(Object source, String taskName, String taskType,
            String priority, T payload) {
        super(source, taskName, payload);
        this.taskType = taskType;
        this.priority = priority;
    }

    /**
     * 获取任务类型。
     * Get task type.
     *
     * @return 任务类型 / task type
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * 获取优先级。
     * Get priority.
     *
     * @return 优先级 / priority
     */
    public String getPriority() {
        return priority;
    }
}
