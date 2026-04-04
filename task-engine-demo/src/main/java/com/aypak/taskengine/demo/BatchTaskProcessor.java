package com.aypak.taskengine.demo;

import com.aypak.taskengine.core.ITaskProcessor;
import com.aypak.taskengine.core.TaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 批量处理任务处理器（演示用）。
 * Batch task processor for demo.
 */
@Slf4j
public class BatchTaskProcessor implements ITaskProcessor<BatchTaskProcessor.BatchTask> {

    @Override
    public String getTaskName() {
        return "Demo-Batch";
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.BATCH;
    }

    @Override
    public void process(BatchTask payload) {
        long startTime = System.currentTimeMillis();

        // 模拟批量数据处理
        int processed = 0;
        for (int i = 0; i < payload.getRecordCount(); i++) {
            // 处理每条记录
            processed++;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Batch task completed, taskId={}, records={}, duration={}ms",
                payload.getTaskId(), processed, elapsed);
    }

    /**
     * 批量任务负载。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchTask {
        private int recordCount;
        private int taskId;
    }
}
