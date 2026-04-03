package com.aypak.taskengine.monitor;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.executor.TaskEngineImpl;
import com.aypak.taskengine.executor.TaskExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 指标收集器和聚合器。
 * Metrics collector and aggregator.
 */
@Slf4j
public class MetricsCollector {

    private final TaskEngineImpl taskEngine;
    private final TaskEngineProperties properties;

    public MetricsCollector(TaskEngineImpl taskEngine, TaskEngineProperties properties) {
        this.taskEngine = taskEngine;
        this.properties = properties;
    }

    /**
     * 收集所有任务的统计快照。
     * Collect statistics snapshot for all tasks.
     *
     * @return 任务名称到统计响应的映射 / map of task name to stats response
     */
    public Map<String, TaskStatsResponse> collectSnapshot() {
        Map<String, TaskMetrics> metrics = taskEngine.getStats();
        Map<String, TaskExecutor> executors = taskEngine.getExecutors();
        Map<String, TaskStatsResponse> snapshot = new HashMap<>();

        for (Map.Entry<String, TaskMetrics> entry : metrics.entrySet()) {
            String taskName = entry.getKey();
            TaskExecutor executor = executors.get(taskName);
            int queueCapacity = executor != null ? executor.getQueueCapacity() : 0;

            snapshot.put(taskName, buildResponse(entry.getValue(), queueCapacity));
        }

        return snapshot;
    }

    /**
     * 收集单个任务的统计快照。
     * Collect statistics snapshot for a single task.
     *
     * @param taskName 任务名称 / task name
     * @return 任务统计响应，如果未找到则返回 null / task stats response, null if not found
     */
    public TaskStatsResponse collectTaskSnapshot(String taskName) {
        TaskMetrics m = taskEngine.getStats(taskName);
        if (m == null) {
            return null;
        }

        TaskExecutor executor = taskEngine.getExecutors().get(taskName);
        int queueCapacity = executor != null ? executor.getQueueCapacity() : 0;

        return buildResponse(m, queueCapacity);
    }

    /**
     * 构建统计响应对象。
     * Build stats response object.
     *
     * @param m 任务指标 / task metrics
     * @param queueCapacity 队列容量 / queue capacity
     * @return 统计响应 / stats response
     */
    private TaskStatsResponse buildResponse(TaskMetrics m, int queueCapacity) {
        TaskStatsResponse response = new TaskStatsResponse();
        response.setTaskName(m.getTaskName());
        response.setTaskType(m.getTaskType().name());
        response.setCurrentQps(m.calculateQps(properties.getQpsWindowSize()));
        response.setAvgResponseTime(m.getAvgResponseTime());
        response.setSuccessCount(m.getSuccessCount().sum());
        response.setFailureCount(m.getFailureCount().sum());
        response.setQueueDepth(m.getQueueDepth().get());
        response.setActiveThreads(m.getActiveThreads().get());
        response.setPeakThreads(m.getPeakThreads().get());
        response.setCurrentMaxPoolSize(m.getCurrentMaxPoolSize().get());
        response.calculateDerivedFields(queueCapacity);
        return response;
    }
}