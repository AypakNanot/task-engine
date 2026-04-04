package com.aypak.taskengine.api;

import com.aypak.taskengine.core.DynamicConfig;
import com.aypak.taskengine.core.TaskType;
import com.aypak.taskengine.executor.SharedPoolManager;
import com.aypak.taskengine.executor.TaskEngineImpl;
import com.aypak.taskengine.executor.TaskExecutor;
import com.aypak.taskengine.executor.TaskRegistry;
import com.aypak.taskengine.monitor.MetricsCollector;
import com.aypak.taskengine.monitor.PoolStatsResponse;
import com.aypak.taskengine.monitor.TaskStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    @Autowired
    private SharedPoolManager sharedPoolManager;

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

    /**
     * 获取所有共享线程池的状态（SHARED 模式）。
     * Get status of all shared thread pools (SHARED mode).
     *
     * @return 线程池统计响应列表 / list of pool stats responses
     */
    @GetMapping("/pools")
    public ResponseEntity<List<PoolStatsResponse>> getAllPools() {
        List<PoolStatsResponse> pools = new ArrayList<>();
        for (TaskType type : TaskType.values()) {
            if (type == TaskType.SCHEDULED) {
                continue;
            }
            TaskExecutor executor = sharedPoolManager.getExecutor("temp", type);
            if (executor != null) {
                executor.updatePoolMetrics();
                pools.add(buildPoolStatsResponse(type, executor));
            }
        }
        return ResponseEntity.ok(pools);
    }

    /**
     * 获取指定类型线程池的状态。
     * Get status of a specific thread pool.
     *
     * @param taskType 任务类型 / task type
     * @return 线程池统计响应 / pool stats response
     */
    @GetMapping("/pool/{taskType}")
    public ResponseEntity<PoolStatsResponse> getPool(@PathVariable String taskType) {
        TaskType type;
        try {
            type = TaskType.valueOf(taskType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        TaskExecutor executor = sharedPoolManager.getExecutor("temp", type);
        if (executor == null) {
            return ResponseEntity.notFound().build();
        }

        executor.updatePoolMetrics();
        return ResponseEntity.ok(buildPoolStatsResponse(type, executor));
    }

    /**
     * 构建线程池统计响应。
     * Build pool stats response.
     *
     * @param type 任务类型 / task type
     * @param executor 执行器 / executor
     * @return 线程池统计响应 / pool stats response
     */
    private PoolStatsResponse buildPoolStatsResponse(TaskType type, TaskExecutor executor) {
        int coreSize = executor.getCorePoolSize();
        int maxSize = executor.getMaxPoolSize();
        int activeThreads = executor.getActiveThreads();
        int queueSize = executor.getQueueSize();
        int queueCapacity = executor.getQueueCapacity();

        double queueUtilization = calculateUtilization(queueSize, queueCapacity);
        double threadUtilization = calculateUtilization(activeThreads, maxSize);

        return PoolStatsResponse.builder()
            .taskType(type.name())
            .corePoolSize(coreSize)
            .maxPoolSize(maxSize)
            .activeThreads(activeThreads)
            .queueSize(queueSize)
            .queueCapacity(queueCapacity)
            .queueUtilization(queueUtilization)
            .threadUtilization(threadUtilization)
            .completedTaskCount(0)
            .shuttingDown(executor.isTerminated())
            .terminated(executor.isTerminated())
            .build();
    }

    /**
     * 计算利用率百分比。
     * Calculate utilization percentage.
     *
     * @param used 已使用量 / used amount
     * @param capacity 总容量 / total capacity
     * @return 利用率百分比 / utilization percentage
     */
    private double calculateUtilization(int used, int capacity) {
        if (capacity <= 0) {
            return 0.0;
        }
        return (double) used / capacity * 100;
    }
}