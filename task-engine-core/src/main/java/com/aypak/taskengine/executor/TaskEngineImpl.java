package com.aypak.taskengine.executor;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.core.*;
import com.aypak.taskengine.circuitbreaker.CircuitBreaker;
import com.aypak.taskengine.event.TaskEventDispatcher;
import com.aypak.taskengine.monitor.TaskMetrics;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
public class TaskEngineImpl implements TaskEngine {

    private final TaskRegistry registry;
    private final TaskThreadPoolFactory poolFactory;
    private final Map<String, TaskExecutor> executors = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final TaskEngineProperties properties;
    private final TaskEventDispatcher eventDispatcher;
    private final SharedPoolManager sharedPoolManager;
    private DynamicScaler scaler;

    public TaskEngineImpl(TaskEngineProperties properties) {
        this.properties = properties;
        this.registry = new TaskRegistry();
        this.poolFactory = new TaskThreadPoolFactory();
        this.eventDispatcher = new TaskEventDispatcher();
        this.sharedPoolManager = new SharedPoolManager(properties);
    }

    public void setScaler(DynamicScaler scaler) {
        this.scaler = scaler;
    }

    /**
     * 发布任务注册事件。
     * Publish task registered event.
     */
    private void publishTaskRegistered(String taskName, TaskConfig config) {
        eventDispatcher.publishTaskRegistered(taskName, config.getTaskType().name());
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

        // 根据池模式选择执行器：共享模式使用类型共享池，独立模式创建专用池
        TaskExecutor executor = getOrCreateExecutor(config, metrics);
        executors.put(taskName, executor);

        // 为每个任务创建熔断器 / Create circuit breaker for each task
        CircuitBreaker circuitBreaker = CircuitBreaker.defaultBreaker();
        circuitBreakers.put(taskName, circuitBreaker);

        int maxPoolSize = config.getMaxPoolSize() != null
                ? config.getMaxPoolSize()
                : getDefaultMaxSize(config.getTaskType());
        metrics.setPoolSizes(maxPoolSize, maxPoolSize);

        log.info("Task registered: {} [type={}, poolMode={}]",
                taskName, config.getTaskType(), properties.getPoolMode());

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
            case CPU_BOUND -> cpuCount * 2;
            case IO_BOUND -> 64;
            case HYBRID -> 16;
            case SCHEDULED -> 4;
            case BATCH -> 4;
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
        if (config.getTaskType() == TaskType.SCHEDULED) {
            ThreadPoolTaskScheduler scheduler = poolFactory.createScheduler(config);
            return new TaskExecutor(scheduler, config.getTaskName(), config.getTaskType(), metrics);
        } else {
            ThreadPoolTaskExecutor executor = poolFactory.createExecutor(config);
            return new TaskExecutor(executor, config.getTaskName(), config.getTaskType(), metrics);
        }
    }

    /**
     * 获取或创建任务执行器（根据池模式决定使用共享池或独立池）。
     * Get or create task executor based on pool mode.
     *
     * @param config  任务配置 / task configuration
     * @param metrics 任务指标 / task metrics
     * @return 任务执行器 / task executor
     */
    private TaskExecutor getOrCreateExecutor(TaskConfig config, TaskMetrics metrics) {
        TaskType type = config.getTaskType();
        String taskName = config.getTaskName();

        if (properties.getPoolMode() == TaskEngineProperties.PoolMode.SHARED) {
            // 共享模式：使用类型共享池
            if (type == TaskType.SCHEDULED) {
                ThreadPoolTaskScheduler scheduler = sharedPoolManager.getScheduler(taskName, type);
                return new TaskExecutor(scheduler, taskName, type, metrics);
            } else {
                // 获取共享的原生执行器，为当前任务创建包装器（使用任务自己的 metrics）
                ThreadPoolTaskExecutor nativeExecutor = sharedPoolManager.getNativeExecutor(type);
                return new TaskExecutor(nativeExecutor, taskName, type, metrics);
            }
        } else {
            // 独立模式：为每个任务创建独立池（向后兼容）
            return createExecutor(config, metrics);
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

        // 检查熔断器状态 / Check circuit breaker state
        CircuitBreaker circuitBreaker = circuitBreakers.get(taskName);
        if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
            throw new IllegalStateException("Circuit breaker is OPEN for task: " + taskName);
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
                publishTaskSuccess(taskName, executionTime);

                // 记录到熔断器 / Record to circuit breaker
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess();
                }
            } catch (Throwable e) {
                metrics.recordFailure();

                // 记录到熔断器 / Record to circuit breaker
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure();
                }

                // 增强错误日志：包含任务上下文信息 / Enhanced error logging with task context
                String contextTraceId = context != null ? context.getTraceId() : null;
                String payloadSummary = payload != null ? payload.getClass().getSimpleName() : "null";
                long queueTime = context != null ? context.getElapsedMs() : 0;

                if (log.isDebugEnabled()) {
                    log.debug("[{}] Task failed: name={}, traceId={}, payload={}, queueTime={}ms, error={}",
                            Thread.currentThread().getName(), taskName, contextTraceId, payloadSummary,
                            queueTime, e.getMessage());
                }

                // 发布失败事件 / Publish failure event
                publishTaskFailure(taskName, e);

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
    private void publishTaskSuccess(String taskName, long executionTime) {
        eventDispatcher.publishTaskSuccess(taskName, executionTime);
    }

    /**
     * 发布任务失败事件。
     * Publish task failure event.
     */
    private void publishTaskFailure(String taskName, Throwable error) {
        eventDispatcher.publishTaskFailure(taskName, error);
    }

    /**
     * 获取任务的熔断器状态。
     * Get circuit breaker state for a task.
     *
     * @param taskName 任务名称 / task name
     * @return 熔断器状态，如果未找到则返回 null / circuit breaker state, null if not found
     */
    public CircuitBreaker getCircuitBreaker(String taskName) {
        return circuitBreakers.get(taskName);
    }

    /**
     * 手动重置任务的熔断器。
     * Manually reset circuit breaker for a task.
     *
     * @param taskName 任务名称 / task name
     */
    public void resetCircuitBreaker(String taskName) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(taskName);
        if (circuitBreaker != null) {
            circuitBreaker.reset();
            log.info("Circuit breaker manually reset for task: {}", taskName);
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

        // 停止事件调度器
        if (eventDispatcher != null) {
            eventDispatcher.stop();
        }

        // 关闭共享池管理器（如果是共享模式）
        if (properties.getPoolMode() == TaskEngineProperties.PoolMode.SHARED) {
            sharedPoolManager.shutdown();
        } else {
            // 独立模式：关闭每个任务的独立池
            for (TaskExecutor executor : executors.values()) {
                executor.shutdown(properties.getShutdownTimeout());
            }
        }

        executors.clear();
        registry.getAllRegistrations().forEach(r ->
                registry.deregister(r.getConfig().getTaskName()));

        log.info("Task Engine shutdown completed");
    }
}