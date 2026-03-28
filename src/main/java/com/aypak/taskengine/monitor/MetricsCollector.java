package com.aypak.taskengine.monitor;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.executor.TaskEngineImpl;
import com.aypak.taskengine.executor.TaskExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
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

    public Map<String, TaskStatsResponse> collectSnapshot() {
        Map<String, TaskMetrics> metrics = taskEngine.getStats();
        Map<String, TaskExecutor> executors = taskEngine.getExecutors();
        Map<String, TaskStatsResponse> snapshot = new HashMap<>();

        for (Map.Entry<String, TaskMetrics> entry : metrics.entrySet()) {
            String taskName = entry.getKey();
            TaskMetrics m = entry.getValue();
            TaskExecutor executor = executors.get(taskName);

            int queueCapacity = executor != null ? executor.getQueueCapacity() : 0;

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

            snapshot.put(taskName, response);
        }

        return snapshot;
    }

    public TaskStatsResponse collectTaskSnapshot(String taskName) {
        TaskMetrics m = taskEngine.getStats(taskName);
        if (m == null) {
            return null;
        }

        TaskExecutor executor = taskEngine.getExecutors().get(taskName);
        int queueCapacity = executor != null ? executor.getQueueCapacity() : 0;

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