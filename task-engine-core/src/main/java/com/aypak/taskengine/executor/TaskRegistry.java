package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.ITaskProcessor;
import com.aypak.taskengine.core.TaskConfig;
import com.aypak.taskengine.monitor.TaskMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务处理器的线程安全注册表。
 * 使用 ConcurrentHashMap 实现并发注册和查找。
 * Thread-safe registry for task processors.
 * Uses ConcurrentHashMap for concurrent registration and lookup.
 */
@Slf4j
public class TaskRegistry {

    private final Map<String, TaskRegistration<?>> registrations = new ConcurrentHashMap<>();
    private final Map<String, TaskMetrics> metrics = new ConcurrentHashMap<>();

    /**
     * 使用指标注册任务处理器及其配置。
     * Register a task processor with its configuration and pre-created metrics.
     *
     * @param config    任务配置 / task configuration
     * @param processor 任务处理器实现 / task processor implementation
     * @param metrics   预先创建的指标（如果为 null 则创建新指标）/ pre-created metrics (if null, new metrics will be created)
     * @throws IllegalArgumentException 如果已存在注册 / if registration already exists
     */
    public <T> void registerWithMetrics(TaskConfig config, ITaskProcessor<T> processor, TaskMetrics metrics) {
        String taskName = config.getTaskName();

        if (registrations.containsKey(taskName)) {
            throw new IllegalArgumentException("Task already registered: " + taskName);
        }

        TaskRegistration<T> registration = new TaskRegistration<>(config, processor);
        registrations.put(taskName, registration);

        if (metrics == null) {
            metrics = new TaskMetrics(taskName, config.getTaskType());
        }
        this.metrics.put(taskName, metrics);

        log.info("Registered task: {} [type={}]",
                taskName, config.getTaskType());
    }

    /**
     * 使用配置注册任务处理器。
     * 内部调用 registerWithMetrics，不传入指标（将创建新指标）。
     * Register a task processor with its configuration.
     * Internally calls registerWithMetrics without metrics (will create new metrics).
     *
     * @param config    任务配置 / task configuration
     * @param processor 任务处理器实现 / task processor implementation
     * @throws IllegalArgumentException 如果已存在注册 / if registration already exists
     */
    public <T> void register(TaskConfig config, ITaskProcessor<T> processor) {
        registerWithMetrics(config, processor, null);
    }

    /**
     * 按名称获取任务注册信息。
     * 返回：注册信息，如果未找到则返回 null。
     * Get task registration by name.
     *
     * @param taskName 任务名称 / task name
     * @return registration or null if not found
     */
    public TaskRegistration<?> getRegistration(String taskName) {
        return registrations.get(taskName);
    }

    /**
     * 按名称获取任务指标。
     * 返回：指标，如果未找到则返回 null。
     * Get task metrics by name.
     *
     * @param taskName 任务名称 / task name
     * @return metrics or null if not found
     */
    public TaskMetrics getMetrics(String taskName) {
        return metrics.get(taskName);
    }

    /**
     * 获取所有注册信息。
     * Get all registrations.
     */
    public Collection<TaskRegistration<?>> getAllRegistrations() {
        return Collections.unmodifiableCollection(registrations.values());
    }

    /**
     * 获取所有指标。
     * Get all metrics.
     */
    public Map<String, TaskMetrics> getAllMetrics() {
        return Collections.unmodifiableMap(metrics);
    }

    /**
     * 检查任务是否已注册。
     * Check if task is registered.
     */
    public boolean isRegistered(String taskName) {
        return registrations.containsKey(taskName);
    }

    /**
     * 注销任务（用于清理）。
     * Deregister a task (for cleanup).
     */
    public void deregister(String taskName) {
        TaskRegistration<?> registration = registrations.remove(taskName);
        metrics.remove(taskName);

        if (registration != null) {
            log.info("Deregistered task: {}", taskName);
        }
    }

    /**
     * 获取已注册任务总数。
     * Get total registered task count.
     */
    public int getTaskCount() {
        return registrations.size();
    }

    /**
     * 任务注册容器。
     * Task registration container.
     */
    @lombok.Getter
    public static class TaskRegistration<T> {
        private final TaskConfig config;
        private final ITaskProcessor<T> processor;

        public TaskRegistration(TaskConfig config, ITaskProcessor<T> processor) {
            this.config = config;
            this.processor = processor;
        }
    }
}