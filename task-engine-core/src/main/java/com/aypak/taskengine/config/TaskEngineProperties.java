package com.aypak.taskengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务引擎配置属性。
 * Configuration properties for Task Engine.
 */
@Data
@ConfigurationProperties(prefix = "task-engine")
public class TaskEngineProperties {

    /**
     * 所有线程池的全局最大线程数。
     * Global maximum thread limit across all pools.
     */
    private int globalMaxThreads = 200;

    /**
     * 每次扩展事件增加/减少的线程数。
     * Number of threads to add/remove per scaling event.
     */
    private int scaleFactor = 2;

    /**
     * 触发扩展的队列深度百分比阈值（0-100）。
     * Queue depth percentage threshold to trigger scale-up (0-100).
     */
    private int scaleUpThreshold = 80;

    /**
     * 缩小前的空闲超时（毫秒）。
     * Idle timeout in milliseconds before scale-down.
     */
    private long idleTimeout = 60000;

    /**
     * 优雅关闭的超时时间（秒）。
     * Shutdown timeout in seconds for graceful shutdown.
     */
    private long shutdownTimeout = 30;

    /**
     * QPS 计算窗口大小（毫秒）。
     * QPS calculation window size in milliseconds.
     */
    private long qpsWindowSize = 60000;

    /**
     * 队列监控间隔（毫秒）。
     * Queue monitoring interval in milliseconds.
     */
    private long queueMonitorInterval = 100;

    /**
     * 特定线程池的配置。
     * Pool-specific configuration.
     */
    private PoolConfig pools = new PoolConfig();

    /**
     * 线程池配置。
     * Pool configuration.
     */
    @Data
    public static class PoolConfig {
        private PoolSize cpuBound = new PoolSize(CPU_COUNT, CPU_COUNT * 2, 100);
        private PoolSize ioBound = new PoolSize(16, 64, 1000);
        private PoolSize hybrid = new PoolSize(8, 16, 500);
        private PoolSize scheduled = new PoolSize(4, 4, 0);
        private PoolSize batch = new PoolSize(2, 4, 10000);

        private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    }

    /**
     * 线程池大小配置。
     * Pool size configuration.
     */
    @Data
    public static class PoolSize {
        private int coreSize;
        private int maxSize;
        private int queueCapacity = 0;

        public PoolSize() {}

        public PoolSize(int coreSize, int maxSize) {
            this.coreSize = coreSize;
            this.maxSize = maxSize;
        }

        public PoolSize(int coreSize, int maxSize, int queueCapacity) {
            this.coreSize = coreSize;
            this.maxSize = maxSize;
            this.queueCapacity = queueCapacity;
        }
    }
}