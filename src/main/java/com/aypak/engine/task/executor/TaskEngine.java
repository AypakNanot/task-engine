package com.aypak.engine.task.executor;

import com.aypak.engine.task.core.DynamicConfig;
import com.aypak.engine.task.core.ITaskProcessor;
import com.aypak.engine.task.core.TaskConfig;
import com.aypak.engine.task.monitor.TaskMetrics;

import java.util.Collection;
import java.util.Map;

/**
 * 任务引擎接口，用于任务管理。
 * Task Engine interface for task management.
 */
public interface TaskEngine {

    /**
     * 使用配置注册任务处理器。
     * Register a task processor with configuration.
     *
     * @param config    任务配置 / task configuration
     * @param processor 任务处理器实现 / task processor implementation
     */
    <T> void register(TaskConfig config, ITaskProcessor<T> processor);

    /**
     * 使用负载执行已注册的任务。
     * Execute a registered task with payload.
     *
     * @param taskName 已注册的任务名称 / registered task name
     * @param payload  任务负载/上下文 / task payload/context
     */
    <T> void execute(String taskName, T payload);

    /**
     * 获取所有任务统计信息。
     * 返回：任务名称到指标的映射。
     * Get all task statistics.
     *
     * @return map of task name to metrics
     */
    Map<String, TaskMetrics> getStats();

    /**
     * 获取特定任务的统计信息。
     * 返回：任务指标，如果未找到则返回 null。
     * Get statistics for specific task.
     *
     * @param taskName 任务名称 / task name
     * @return task metrics or null if not found
     */
    TaskMetrics getStats(String taskName);

    /**
     * 动态更新任务配置。
     * Update task configuration dynamically.
     *
     * @param taskName 任务名称 / task name
     * @param config   要应用的动态配置 / dynamic configuration to apply
     */
    void updateConfig(String taskName, DynamicConfig config);

    /**
     * 重置特定任务的指标。
     * Reset metrics for a specific task.
     *
     * @param taskName 任务名称 / task name
     */
    void resetMetrics(String taskName);

    /**
     * 重置所有任务指标。
     * Reset all task metrics.
     */
    void resetAllMetrics();

    /**
     * 获取所有任务注册信息。
     * Get all task registrations.
     */
    Collection<TaskRegistry.TaskRegistration<?>> getAllRegistrations();
}