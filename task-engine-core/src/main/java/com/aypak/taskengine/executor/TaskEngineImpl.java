package com.aypak.taskengine.executor;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.core.*;
import com.aypak.taskengine.event.TaskFailureEvent;
import com.aypak.taskengine.event.TaskRegisteredEvent;
import com.aypak.taskengine.event.TaskSuccessEvent;
import com.aypak.taskengine.monitor.TaskMetrics;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务引擎 - 任务管理的主要协调器。
 * 针对高吞吐量优化，热路径开销最小化。
 * Task Engine - main orchestrator for task management.
 * Optimized for high-throughput with minimal overhead in hot path.
 */
@Slf4j
@Getter
public class TaskEngineImpl implements TaskEngine, ApplicationEventPublisherAware {

    private final TaskRegistry registry;
    private final TaskThreadPoolFactory poolFactory;
    private final Map<String, TaskExecutor> executors = new ConcurrentHashMap<>();
    private final TaskEngineProperties properties;
    private DynamicScaler scaler;
    private ApplicationEventPublisher eventPublisher;

    public TaskEngineImpl(TaskEngineProperties properties) {
        this.properties = properties;
        this.registry = new TaskRegistry();
        this.poolFactory = new TaskThreadPoolFactory();
    }

    public void setScaler(DynamicScaler scaler) {
        this.scaler = scaler;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发布任务注册事件。
     * Publish task registered event.
     */
    private void publishTaskRegistered(String taskName, TaskConfig config) {
        if (eventPublisher != null) {
            TaskRegisteredEvent<Void> event = new TaskRegisteredEvent<>(
                    this,
                    taskName,
                    config.getTaskType().name(),
                    null
            );
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 注册任务处理器。
     * Register a task processor.
     *
     * @param config    任务配置 / task configuration
     * @param processor 任务处理器实现 / task processor implementation
     */
    @Override
    public <T> void register(TaskConfig config, ITaskProcessor<T> processor) {
        config.validate();
        String taskName = config.getTaskName();

        if (registry.isRegistered(taskName)) {
            throw new IllegalArgumentException("Task already registered: " + taskName);
        }

        TaskMetrics metrics = new TaskMetrics(taskName, config.getTaskType());
        registry.registerWithMetrics(config, processor, metrics);

        TaskExecutor executor = createExecutor(config, metrics);
        executors.put(taskName, executor);

        int maxPoolSize = config.getMaxPoolSize() != null
                ? config.getMaxPoolSize()
                : getDefaultMaxSize(config.getTaskType());
        metrics.setPoolSizes(maxPoolSize, maxPoolSize);

        log.info("Task registered: {} [type={}]",
                taskName, config.getTaskType());

        // 发布任务注册事件 / Publish task registered event
        publishTaskRegistered(taskName, config);
    }

    /**
     * 获取任务类型的默认最大线程数。
     * Get default max thread count for task type.
     *
     * @param type 任务类型 / task type
     * @return 默认最大线程数 / default max thread count
     */
    private int getDefaultMaxSize(TaskType type) {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        return switch (type) {
            case INIT -> cpuCount;
            case CRON -> 4;
            case HIGH_FREQ -> cpuCount * 4;
            case BACKGROUND -> 4;
        };
    }

    /**
     * 创建任务执行器。
     * Create task executor.
     *
     * @param config  任务配置 / task configuration
     * @param metrics 任务指标 / task metrics
     * @return 任务执行器 / task executor
     */
    private TaskExecutor createExecutor(TaskConfig config, TaskMetrics metrics) {
        if (config.getTaskType() == TaskType.CRON) {
            ThreadPoolTaskScheduler scheduler = poolFactory.createScheduler(config);
            return new TaskExecutor(scheduler, config.getTaskName(), config.getTaskType(), metrics);
        } else {
            ThreadPoolTaskExecutor executor = poolFactory.createExecutor(config);
            return new TaskExecutor(executor, config.getTaskName(), config.getTaskType(), metrics);
        }
    }

    /**
     * 执行任务 - 针对高吞吐量优化。
     * 热路径开销最小化：无日志记录，无不必要的计算。
     * Execute task - optimized for high throughput.
     * Minimal overhead in hot path: no logging, no unnecessary calculations.
     */
    @Override
    public <T> void execute(String taskName, T payload) {
        TaskRegistry.TaskRegistration<?> registration = registry.getRegistration(taskName);
        if (registration == null) {
            throw new IllegalArgumentException("Task not registered: " + taskName);
        }

        TaskExecutor executor = executors.get(taskName);
        if (executor == null) {
            throw new IllegalStateException("Executor not found for task: " + taskName);
        }

        TaskMetrics metrics = registry.getMetrics(taskName);
        if (metrics == null) {
            throw new IllegalStateException("Metrics not found for task: " + taskName);
        }

        @SuppressWarnings("unchecked")
        ITaskProcessor<T> processor = (ITaskProcessor<T>) registration.getProcessor();

        // 捕获上下文一次 / Capture context once
        String traceId = MDC.get("traceId");
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        long startTime = System.currentTimeMillis();
        TaskContext context = new TaskContext(traceId, mdcContext, startTime);

        executor.execute(() -> {
            try {
                // 传播 MDC 上下文 / Propagate MDC context
                if (mdcContext != null && !mdcContext.isEmpty()) {
                    MDC.setContextMap(mdcContext);
                }

                // 执行任务 / Execute task
                processor.process(payload);

                // 成功回调（仅当重写时）/ Success callback (only if overridden)
                processor.onSuccess(payload);

                // 发布成功事件 / Publish success event
                long executionTime = System.currentTimeMillis() - startTime;
                publishTaskSuccess(taskName, payload, executionTime);
            } catch (Throwable e) {
                metrics.recordFailure();
                // 仅在 debug 级别记录日志以减少开销 / Only log at debug level to reduce overhead
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Task failed: {}", Thread.currentThread().getName(), e.getMessage());
                }

                // 发布失败事件 / Publish failure event
                publishTaskFailure(taskName, payload, e);

                processor.onFailure(payload, e);
                throw e;
            } finally {
                MDC.clear();
            }
        }, context);
    }

    /**
     * 发布任务成功事件。
     * Publish task success event.
     */
    private <T> void publishTaskSuccess(String taskName, T payload, long executionTime) {
        if (eventPublisher != null) {
            TaskSuccessEvent<T> event = new TaskSuccessEvent<>(
                    this,
                    taskName,
                    payload,
                    executionTime
            );
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * 发布任务失败事件。
     * Publish task failure event.
     */
    private <T> void publishTaskFailure(String taskName, T payload, Throwable error) {
        if (eventPublisher != null) {
            TaskFailureEvent<T> event = new TaskFailureEvent<>(
                    this,
                    taskName,
                    payload,
                    error
            );
            eventPublisher.publishEvent(event);
        }
    }

    @Override
    public Map<String, TaskMetrics> getStats() {
        Map<String, TaskMetrics> stats = new HashMap<>();

        for (Map.Entry<String, TaskExecutor> entry : executors.entrySet()) {
            String taskName = entry.getKey();
            TaskExecutor executor = entry.getValue();
            TaskMetrics metrics = registry.getMetrics(taskName);

            if (metrics != null) {
                executor.updatePoolMetrics();
                stats.put(taskName, metrics);
            }
        }

        return stats;
    }

    @Override
    public TaskMetrics getStats(String taskName) {
        TaskExecutor executor = executors.get(taskName);
        TaskMetrics metrics = registry.getMetrics(taskName);

        if (executor != null && metrics != null) {
            executor.updatePoolMetrics();
        }

        return metrics;
    }

    /**
     * 更新任务配置。
     * Update task configuration.
     *
     * @param taskName 任务名称 / task name
     * @param config   动态配置 / dynamic configuration
     */
    @Override
    public void updateConfig(String taskName, DynamicConfig config) {
        config.validate();

        TaskExecutor executor = executors.get(taskName);
        if (executor == null) {
            throw new IllegalArgumentException("Task not found: " + taskName);
        }

        if (config.getMaxPoolSize() != null && config.getMaxPoolSize() > 0) {
            int oldSize = executor.getMaxPoolSize();
            executor.setMaxPoolSize(config.getMaxPoolSize());
            TaskMetrics metrics = registry.getMetrics(taskName);
            if (metrics != null) {
                metrics.updateCurrentMaxPoolSize(config.getMaxPoolSize());
            }
            log.info("[{}] Pool scaling: {} -> {} (manual)", taskName, oldSize, config.getMaxPoolSize());
        }
        if (config.getCorePoolSize() != null && config.getCorePoolSize() > 0) {
            executor.setCorePoolSize(config.getCorePoolSize());
        }
    }

    /**
     * 重置指定任务的指标。
     * Reset metrics for a specific task.
     *
     * @param taskName 任务名称 / task name
     */
    @Override
    public void resetMetrics(String taskName) {
        TaskMetrics metrics = registry.getMetrics(taskName);
        if (metrics != null) {
            metrics.reset();
            log.info("Metrics reset for: {}", taskName);
        }
    }

    /**
     * 重置所有任务指标。
     * Reset all task metrics.
     */
    @Override
    public void resetAllMetrics() {
        registry.getAllMetrics().values().forEach(TaskMetrics::reset);
        log.info("All metrics reset");
    }

    @Override
    public Collection<TaskRegistry.TaskRegistration<?>> getAllRegistrations() {
        return registry.getAllRegistrations();
    }

    /**
     * 优雅关闭任务引擎。
     * Graceful shutdown of task engine.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Task Engine shutting down gracefully...");

        if (scaler != null) {
            scaler.stop();
        }

        for (TaskExecutor executor : executors.values()) {
            executor.shutdown(properties.getShutdownTimeout());
        }

        executors.clear();
        registry.getAllRegistrations().forEach(r ->
                registry.deregister(r.getConfig().getTaskName()));

        log.info("Task Engine shutdown completed");
    }
}