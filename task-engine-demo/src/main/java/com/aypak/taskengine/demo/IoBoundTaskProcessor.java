package com.aypak.taskengine.demo;

import com.aypak.taskengine.core.ITaskProcessor;
import com.aypak.taskengine.core.TaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * IO 密集型任务处理器（演示用）。
 * IO-bound task processor for demo.
 */
@Slf4j
public class IoBoundTaskProcessor implements ITaskProcessor<IoBoundTaskProcessor.IoTask> {

    @Override
    public String getTaskName() {
        return "Demo-IoBound";
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.IO_BOUND;
    }

    @Override
    public void process(IoTask payload) {
        try {
            // 模拟 IO 操作（网络/数据库调用）
            Thread.sleep(payload.getDurationMs());
            log.info("IO-bound task completed, taskId={}, duration={}ms",
                    payload.getTaskId(), payload.getDurationMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("IO-bound task interrupted");
        }
    }

    /**
     * IO 任务负载。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IoTask {
        private long durationMs;
        private int taskId;
    }
}
