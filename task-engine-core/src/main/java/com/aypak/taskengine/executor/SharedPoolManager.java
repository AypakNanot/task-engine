package com.aypak.taskengine.executor;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.core.TaskConfig;
import com.aypak.taskengine.core.TaskType;
import com.aypak.taskengine.monitor.TaskMetrics;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享线程池管理器。
 * Shared thread pool manager.
 *
 * <p>按任务类型管理共享线程池，避免为每个任务创建独立线程池。</p>
 * <p>Manages shared thread pools by task type to avoid creating dedicated pools per task.</p>
 */
@Slf4j
public class SharedPoolManager {

    /**
     * 共享执行器映射。
     * Shared executor map.
     */
    @Getter
    private final Map<TaskType, TaskExecutor> sharedExecutors = new EnumMap<>(TaskType.class);

    /**
     * 共享调度器映射（用于 SCHEDULED 类型）。
     * Shared scheduler map for SCHEDULED type.
     */
    @Getter
    private final Map<TaskType, ThreadPoolTaskScheduler> sharedSchedulers = new EnumMap<>(TaskType.class);

    /**
     * 共享原生执行器映射。
     * Shared native executor map.
     */
    @Getter
    private final Map<TaskType, ThreadPoolTaskExecutor> sharedNativeExecutors = new EnumMap<>(TaskType.class);

    /**
     * 任务名称到执行器的映射（用于查找）。
     * Task name to executor map for lookup.
     */
    private final Map<String, TaskExecutor> taskExecutorMap = new ConcurrentHashMap<>();

    /**
     * 线程池工厂。
     * Thread pool factory.
     */
    private final TaskThreadPoolFactory poolFactory = new TaskThreadPoolFactory();

    /**
     * 任务引擎配置。
     * Task engine properties.
     */
    private final TaskEngineProperties properties;

    public SharedPoolManager(TaskEngineProperties properties) {
        this.properties = properties;
        initializeSharedPools();
    }

    /**
     * 初始化所有共享线程池。
     * Initialize all shared thread pools.
     */
    private void initializeSharedPools() {
        for (TaskType type : TaskType.values()) {
            if (type == TaskType.SCHEDULED) {
                // SCHEDULED 类型使用调度器
                ThreadPoolTaskScheduler scheduler = createSharedScheduler(type);
                sharedSchedulers.put(type, scheduler);
                log.info("Created shared scheduler for {}: poolSize={}",
                        type, getPoolSizeForType(type));
            } else {
                // 其他类型使用执行器
                TaskExecutor executor = createSharedExecutor(type);
                sharedExecutors.put(type, executor);
                log.info("Created shared executor for {}: core={}, max={}, queue={}",
                        type,
                        properties.getPools().getPoolSizeForType(type).getCoreSize(),
                        properties.getPools().getPoolSizeForType(type).getMaxSize(),
                        properties.getPools().getPoolSizeForType(type).getQueueCapacity());
            }
        }
        log.info("Shared pool manager initialized with {} executors and {} schedulers",
                sharedExecutors.size(), sharedSchedulers.size());
    }

    /**
     * 为任务类型创建共享执行器。
     * Create shared executor for task type.
     */
    private TaskExecutor createSharedExecutor(TaskType type) {
        // 创建临时配置用于创建执行器
        TaskConfig config = TaskConfig.builder()
                .taskName(type.getPrefix() + "-SHARED")
                .taskType(type)
                .build();

        // 使用共享指标容器
        TaskMetrics metrics = new TaskMetrics(type.getPrefix() + "-SHARED", type);
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = poolFactory.createExecutor(config);

        // 应用配置中的池大小
        TaskEngineProperties.PoolSize poolSize = properties.getPools().getPoolSizeForType(type);
        executor.setCorePoolSize(poolSize.getCoreSize());
        executor.setMaxPoolSize(poolSize.getMaxSize());
        executor.setQueueCapacity(poolSize.getQueueCapacity());

        // 重新初始化以应用新设置
        executor.initialize();

        // 存储原生执行器引用
        sharedNativeExecutors.put(type, executor);

        return new TaskExecutor(executor, type.getPrefix() + "-SHARED", type, metrics);
    }

    /**
     * 为 SCHEDULED 类型创建共享调度器。
     * Create shared scheduler for SCHEDULED type.
     */
    private ThreadPoolTaskScheduler createSharedScheduler(TaskType type) {
        TaskConfig config = TaskConfig.builder()
                .taskName(type.getPrefix() + "-SHARED")
                .taskType(type)
                .build();

        return poolFactory.createScheduler(config);
    }

    /**
     * 获取任务类型的池大小。
     * Get pool size for task type.
     */
    private int getPoolSizeForType(TaskType type) {
        TaskEngineProperties.PoolSize poolSize = properties.getPools().getPoolSizeForType(type);
        return poolSize != null ? poolSize.getCoreSize() : 4;
    }

    /**
     * 获取任务的执行器。
     * Get executor for task.
     *
     * @param taskName 任务名称 / task name
     * @param type 任务类型 / task type
     * @return 任务执行器 / task executor
     */
    public TaskExecutor getExecutor(String taskName, TaskType type) {
        if (type == TaskType.SCHEDULED) {
            return null; // SCHEDULED 类型返回 null，使用 getScheduler
        }
        TaskExecutor sharedExecutor = sharedExecutors.get(type);
        if (sharedExecutor == null) {
            throw new IllegalStateException("No shared executor found for task type: " + type);
        }
        // 记录任务到执行器的映射
        taskExecutorMap.put(taskName, sharedExecutor);
        return sharedExecutor;
    }

    /**
     * 获取任务类型的底层 ThreadPoolTaskExecutor。
     * Get the underlying ThreadPoolTaskExecutor for a task type.
     *
     * @param type 任务类型 / task type
     * @return ThreadPoolTaskExecutor
     */
    public ThreadPoolTaskExecutor getNativeExecutor(TaskType type) {
        ThreadPoolTaskExecutor executor = sharedNativeExecutors.get(type);
        if (executor == null) {
            throw new IllegalStateException("No shared executor found for task type: " + type);
        }
        return executor;
    }

    /**
     * 获取任务的调度器。
     * Get scheduler for task.
     *
     * @param taskName 任务名称 / task name
     * @param type 任务类型 / task type
     * @return 任务调度器 / task scheduler
     */
    public ThreadPoolTaskScheduler getScheduler(String taskName, TaskType type) {
        if (type != TaskType.SCHEDULED) {
            throw new IllegalArgumentException("Scheduler only available for SCHEDULED tasks");
        }
        ThreadPoolTaskScheduler scheduler = sharedSchedulers.get(type);
        if (scheduler == null) {
            throw new IllegalStateException("No shared scheduler found for task type: " + type);
        }
        return scheduler;
    }

    /**
     * 获取任务到执行器的映射。
     * Get task to executor mapping.
     *
     * @return 映射 / mapping
     */
    public Map<String, TaskExecutor> getTaskExecutorMap() {
        return new ConcurrentHashMap<>(taskExecutorMap);
    }

    /**
     * 关闭所有共享线程池。
     * Shutdown all shared thread pools.
     */
    public void shutdown() {
        log.info("Shutting down shared pool manager...");

        for (Map.Entry<TaskType, TaskExecutor> entry : sharedExecutors.entrySet()) {
            TaskExecutor executor = entry.getValue();
            if (executor != null) {
                executor.shutdown(properties.getShutdownTimeout());
                log.info("Shutdown shared executor for {}", entry.getKey());
            }
        }

        for (Map.Entry<TaskType, ThreadPoolTaskScheduler> entry : sharedSchedulers.entrySet()) {
            ThreadPoolTaskScheduler scheduler = entry.getValue();
            if (scheduler != null) {
                scheduler.shutdown();
                log.info("Shutdown shared scheduler for {}", entry.getKey());
            }
        }

        log.info("Shared pool manager shutdown complete");
    }
}
