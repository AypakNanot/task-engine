package com.aypak.taskengine.demo;

import com.aypak.taskengine.core.ITaskProcessor;
import com.aypak.taskengine.core.TaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CPU 密集型任务处理器（演示用）。
 * CPU-bound task processor for demo.
 */
@Slf4j
public class CpuBoundTaskProcessor implements ITaskProcessor<CpuBoundTaskProcessor.CpuTask> {

    @Override
    public String getTaskName() {
        return "Demo-CpuBound";
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.CPU_BOUND;
    }

    @Override
    public void process(CpuTask payload) {
        long startTime = System.currentTimeMillis();

        // 模拟 CPU 密集型计算
        double result = 0;
        for (int i = 0; i < 1000000; i++) {
            result += Math.sqrt(i);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("CPU-bound task executed in {}ms, result: {}", elapsed, result);
    }

    /**
     * CPU 任务负载。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuTask {
        private long durationMs;
        private int taskId;
    }
}
