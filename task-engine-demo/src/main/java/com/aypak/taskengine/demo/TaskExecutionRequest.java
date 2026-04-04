package com.aypak.taskengine.demo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务执行请求。
 * Task execution request.
 */
@Data
@NoArgsConstructor
public class TaskExecutionRequest {

    /**
     * 任务执行次数。
     * Number of task executions.
     */
    private int count = 1;

    /**
     * 每次执行耗时（毫秒）。
     * Execution time per task in milliseconds.
     */
    private long durationMs = 100;
}
