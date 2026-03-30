package com.aypak.engine.task.executor;

import com.aypak.engine.task.config.TaskEngineProperties;
import com.aypak.engine.task.monitor.QueueMonitor;

/**
 * 任务引擎构建器，提供 Fluent API 创建 TaskEngine 实例。
 * Task Engine Builder providing Fluent API to create TaskEngine instances.
 *
 * <p>使用示例 / Usage example:</p>
 * <pre>{@code
 * TaskEngine engine = TaskEngine.builder()
 *     .maxThreads(200)
 *     .scaleFactor(2)
 *     .scaleUpThreshold(80)
 *     .idleTimeout(60000)
 *     .shutdownTimeout(30)
 *     .queueMonitorInterval(100)
 *     .build();
 * }</pre>
 *
 * <p>非 Spring 环境使用 / Usage in non-Spring environment:</p>
 * <pre>{@code
 * TaskEngine engine = TaskEngine.builder()
 *     .maxThreads(100)
 *     .build();
 *
 * engine.register(config, processor);
 * engine.execute("MyTask", payload);
 * }</pre>
 */
public class TaskEngineBuilder {

    /** 全局最大线程数 / Global maximum threads */
    private int globalMaxThreads = 200;

    /** 每次扩展的线程数 / Threads to add/remove per scaling event */
    private int scaleFactor = 2;

    /** 队列深度百分比触发扩展 / Queue depth percentage to trigger scale-up */
    private int scaleUpThreshold = 80;

    /** 空闲超时（毫秒） / Idle timeout in milliseconds */
    private long idleTimeout = 60000;

    /** 优雅关闭超时（秒） / Graceful shutdown timeout in seconds */
    private int shutdownTimeout = 30;

    /** 队列监控间隔（毫秒） / Queue monitor interval in milliseconds */
    private long queueMonitorInterval = 100;

    /**
     * 设置全局最大线程数。
     * Set global maximum threads.
     *
     * @param maxThreads 最大线程数 / maximum threads
     * @return this builder
     */
    public TaskEngineBuilder maxThreads(int maxThreads) {
        this.globalMaxThreads = maxThreads;
        return this;
    }

    /**
     * 设置每次扩展的线程数。
     * Set threads to add/remove per scaling event.
     *
     * @param factor 扩展因子 / scale factor
     * @return this builder
     */
    public TaskEngineBuilder scaleFactor(int factor) {
        this.scaleFactor = factor;
        return this;
    }

    /**
     * 设置队列深度触发扩展的百分比阈值。
     * Set queue depth percentage threshold to trigger scale-up.
     *
     * @param threshold 百分比 (0-100) / percentage (0-100)
     * @return this builder
     */
    public TaskEngineBuilder scaleUpThreshold(int threshold) {
        this.scaleUpThreshold = threshold;
        return this;
    }

    /**
     * 设置线程空闲超时（毫秒）。
     * Set thread idle timeout in milliseconds.
     *
     * @param timeoutMs 超时时间（毫秒） / timeout in milliseconds
     * @return this builder
     */
    public TaskEngineBuilder idleTimeout(long timeoutMs) {
        this.idleTimeout = timeoutMs;
        return this;
    }

    /**
     * 设置优雅关闭超时（秒）。
     * Set graceful shutdown timeout in seconds.
     *
     * @param timeoutSec 超时时间（秒） / timeout in seconds
     * @return this builder
     */
    public TaskEngineBuilder shutdownTimeout(int timeoutSec) {
        this.shutdownTimeout = timeoutSec;
        return this;
    }

    /**
     * 设置队列监控间隔（毫秒）。
     * Set queue monitor interval in milliseconds.
     *
     * @param intervalMs 间隔时间（毫秒） / interval in milliseconds
     * @return this builder
     */
    public TaskEngineBuilder queueMonitorInterval(long intervalMs) {
        this.queueMonitorInterval = intervalMs;
        return this;
    }

    /**
     * 构建并启动任务引擎。
     * Build and start Task Engine.
     *
     * @return TaskEngine 实例 / TaskEngine instance
     */
    public TaskEngine build() {
        TaskEngineProperties properties = createProperties();
        TaskEngineImpl engine = new TaskEngineImpl(properties);

        // 创建并启动动态扩展器 / Create and start dynamic scaler
        DynamicScaler scaler = new DynamicScaler(engine, properties);
        scaler.start(5000); // 每 5 秒检查一次 / Check every 5 seconds
        engine.setScaler(scaler);

        // 创建并启动队列监控 / Create and start queue monitor
        QueueMonitor queueMonitor = new QueueMonitor(engine, scaleUpThreshold);
        queueMonitor.start(queueMonitorInterval);

        return engine;
    }

    /**
     * 构建任务引擎但不启动自动扩展和监控。
     * Build Task Engine without starting auto-scaling and monitoring.
     * 适用于需要手动控制组件的场景。
     * Suitable for scenarios requiring manual control of components.
     *
     * @return TaskEngineImpl 实例 / TaskEngineImpl instance
     */
    public TaskEngineImpl buildWithoutMonitoring() {
        TaskEngineProperties properties = createProperties();
        return new TaskEngineImpl(properties);
    }

    /**
     * 创建任务引擎配置属性。
     * Create task engine configuration properties.
     *
     * @return TaskEngineProperties 实例 / TaskEngineProperties instance
     */
    private TaskEngineProperties createProperties() {
        TaskEngineProperties properties = new TaskEngineProperties();
        properties.setGlobalMaxThreads(this.globalMaxThreads);
        properties.setScaleFactor(this.scaleFactor);
        properties.setScaleUpThreshold(this.scaleUpThreshold);
        properties.setIdleTimeout(this.idleTimeout);
        properties.setShutdownTimeout(this.shutdownTimeout);
        properties.setQueueMonitorInterval(this.queueMonitorInterval);
        return properties;
    }
}
