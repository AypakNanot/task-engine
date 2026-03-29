package com.aypak.taskengine.api;

import com.aypak.taskengine.core.DynamicConfig;
import com.aypak.taskengine.executor.TaskEngineImpl;
import com.aypak.taskengine.executor.TaskRegistry;
import com.aypak.taskengine.monitor.MetricsCollector;
import com.aypak.taskengine.monitor.TaskStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * 用于任务监控和管理的 REST API。
 * REST API for task monitoring and management.
 */
@RestController
@RequestMapping("/monitor/task")
@RequiredArgsConstructor
public class TaskMonitorController {

    private final MetricsCollector metricsCollector;
    private final TaskEngineImpl taskEngine;

    /**
     * 获取所有任务的状态。
     * Get status of all tasks.
     *
     * @return 任务统计响应映射 / map of task stats responses
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, TaskStatsResponse>> getAllStatus() {
        Map<String, TaskStatsResponse> snapshot = metricsCollector.collectSnapshot();
        return ResponseEntity.ok(snapshot);
    }

    /**
     * 获取指定任务的状态。
     * Get status of a specific task.
     *
     * @param taskName 任务名称 / task name
     * @return 任务统计响应 / task stats response
     */
    @GetMapping("/status/{taskName}")
    public ResponseEntity<TaskStatsResponse> getTaskStatus(@PathVariable String taskName) {
        TaskStatsResponse response = metricsCollector.collectTaskSnapshot(taskName);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有任务注册信息。
     * Get all task registrations.
     *
     * @return 任务注册信息集合 / collection of task registrations
     */
    @GetMapping("/registrations")
    public ResponseEntity<Collection<TaskRegistry.TaskRegistration<?>>> getRegistrations() {
        return ResponseEntity.ok(taskEngine.getAllRegistrations());
    }

    /**
     * 更新任务配置。
     * Update task configuration.
     *
     * @param taskName 任务名称 / task name
     * @param config   动态配置 / dynamic configuration
     * @return 响应实体 / response entity
     */
    @PutMapping("/config/{taskName}")
    public ResponseEntity<Void> updateConfig(
            @PathVariable String taskName,
            @RequestBody DynamicConfig config) {
        try {
            taskEngine.updateConfig(taskName, config);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 重置指定任务的指标。
     * Reset metrics for a specific task.
     *
     * @param taskName 任务名称 / task name
     * @return 响应实体 / response entity
     */
    @DeleteMapping("/metrics/{taskName}")
    public ResponseEntity<Void> resetTaskMetrics(@PathVariable String taskName) {
        taskEngine.resetMetrics(taskName);
        return ResponseEntity.noContent().build();
    }

    /**
     * 重置所有任务指标。
     * Reset all task metrics.
     *
     * @return 响应实体 / response entity
     */
    @DeleteMapping("/metrics")
    public ResponseEntity<Void> resetAllMetrics() {
        taskEngine.resetAllMetrics();
        return ResponseEntity.noContent().build();
    }
}