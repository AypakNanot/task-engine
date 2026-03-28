package com.aypak.taskengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Task Engine.
 */
@Data
@ConfigurationProperties(prefix = "task-engine")
public class TaskEngineProperties {

    /**
     * Global maximum thread limit across all pools.
     */
    private int globalMaxThreads = 200;

    /**
     * Number of threads to add/remove per scaling event.
     */
    private int scaleFactor = 2;

    /**
     * Queue depth percentage threshold to trigger scale-up (0-100).
     */
    private int scaleUpThreshold = 80;

    /**
     * Idle timeout in milliseconds before scale-down.
     */
    private long idleTimeout = 60000;

    /**
     * Shutdown timeout in seconds for graceful shutdown.
     */
    private long shutdownTimeout = 30;

    /**
     * QPS calculation window size in milliseconds.
     */
    private long qpsWindowSize = 60000;

    /**
     * Queue monitoring interval in milliseconds.
     */
    private long queueMonitorInterval = 100;

    /**
     * Pool-specific configuration.
     */
    private PoolConfig pools = new PoolConfig();

    @Data
    public static class PoolConfig {
        private PoolSize type1Init = new PoolSize(1, 8);
        private PoolSize type2Cron = new PoolSize(4, 4);
        private PoolSize type3HighFreq = new PoolSize(16, 32, 10000);
        private PoolSize type4Background = new PoolSize(2, 4, 100);
    }

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