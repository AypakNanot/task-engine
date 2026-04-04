package com.aypak.taskengine.demo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务执行响应。
 * Task execution response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionResponse {

    /**
     * 任务类型。
     * Task type.
     */
    private String taskType;

    /**
     * 任务名称。
     * Task name.
     */
    private String taskName;

    /**
     * 执行次数。
     * Execution count.
     */
    private int count;

    /**
     * 状态。
     * Status.
     */
    private String status;

    /**
     * 消息。
     * Message.
     */
    private String message;
}
