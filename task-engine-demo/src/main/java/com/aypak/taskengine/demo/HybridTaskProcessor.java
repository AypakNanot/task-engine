package com.aypak.taskengine.demo;

import com.aypak.taskengine.core.ITaskProcessor;
import com.aypak.taskengine.core.TaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 混合型任务处理器（演示用）。
 * Hybrid task processor for demo.
 */
@Slf4j
public class HybridTaskProcessor implements ITaskProcessor<HybridTaskProcessor.HybridTask> {

    @Override
    public String getTaskName() {
        return "Demo-Hybrid";
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.HYBRID;
    }

    @Override
    public void process(HybridTask payload) {
        long startTime = System.currentTimeMillis();

        // 模拟混合操作：计算 + 等待
        double result = 0;
        for (int i = 0; i < 100000; i++) {
            result += Math.sqrt(i);
        }

        try {
            Thread.sleep(payload.getDurationMs() / 2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Hybrid task completed, taskId={}, duration={}ms",
                payload.getTaskId(), elapsed);
    }

    /**
     * 混合任务负载。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HybridTask {
        private long durationMs;
        private int taskId;
    }
}
