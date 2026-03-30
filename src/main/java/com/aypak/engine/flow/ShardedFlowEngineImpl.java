package com.aypak.engine.flow;

import com.aypak.engine.flow.core.FlowConfig;
import com.aypak.engine.flow.core.FlowEvent;
import com.aypak.engine.flow.core.FlowNode;
import com.aypak.engine.flow.dispatcher.ShardDispatcher;
import com.aypak.engine.flow.monitor.FlowMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 分片流引擎实现。
 * Sharded Flow Engine implementation.
 *
 * @param <K> 分片键类型 / shard key type
 * @param <T> 负载类型 / payload type
 */
public class ShardedFlowEngineImpl<K, T> implements ShardedFlowEngine<K, T> {

    private static final Logger log = LoggerFactory.getLogger(ShardedFlowEngineImpl.class);

    /** 流配置 / Flow configuration */
    private final FlowConfig<K, T> config;

    /** 流指标 / Flow metrics */
    private final FlowMetrics metrics;

    /** 分片调度器 / Shard dispatcher */
    private final ShardDispatcher<K, T> dispatcher;

    /** 运行状态 / Running status */
    private volatile boolean running = false;

    /**
     * 创建分片流引擎。
     * Create sharded flow engine.
     *
     * @param config 配置 / configuration
     */
    public ShardedFlowEngineImpl(FlowConfig<K, T> config) {
        this.config = config;
        this.metrics = config.isMetricsEnabled() ? new FlowMetrics(config.getName()) : null;
        this.dispatcher = new ShardDispatcher<>(
                config.getShardCount(),
                config.getQueueCapacity(),
                config.getNodes(),
                config.getRejectPolicy(),
                metrics
        );
    }

    @Override
    public void start() {
        if (running) {
            log.warn("Engine {} is already running", config.getName());
            return;
        }

        log.info("Starting ShardedFlowEngine: {} with {} shards",
                config.getName(), config.getShardCount());

        // 初始化所有节点
        // Initialize all nodes
        for (FlowNode<K, T> node : config.getNodes()) {
            try {
                node.initialize();
            } catch (Exception e) {
                log.error("Failed to initialize node: {}", node.getNodeName(), e);
            }
        }

        // 启动调度器
        // Start dispatcher
        dispatcher.start();
        running = true;

        log.info("ShardedFlowEngine {} started successfully", config.getName());
    }

    @Override
    public boolean submit(FlowEvent<K, T> event) {
        if (!running) {
            log.warn("Engine {} is not running, rejecting event: {}", config.getName(), event.getId());
            return false;
        }

        if (metrics != null) {
            metrics.recordReceive();
        }

        return dispatcher.submit(event);
    }

    @Override
    public FlowMetrics getMetrics() {
        return metrics;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void stop() {
        shutdown(30, TimeUnit.SECONDS);
    }

    @Override
    public boolean shutdown(long timeout, TimeUnit unit) {
        if (!running) {
            return true;
        }

        log.info("Shutting down ShardedFlowEngine {}...", config.getName());
        running = false;

        boolean completed = dispatcher.shutdown(timeout, unit);

        // 销毁所有节点
        // Destroy all nodes
        for (FlowNode<K, T> node : config.getNodes()) {
            try {
                node.destroy();
            } catch (Exception e) {
                log.error("Failed to destroy node: {}", node.getNodeName(), e);
            }
        }

        if (completed) {
            log.info("ShardedFlowEngine {} shut down completed", config.getName());
        } else {
            log.warn("ShardedFlowEngine {} shut down timed out", config.getName());
        }

        return completed;
    }

    @Override
    public FlowConfig<K, T> getConfig() {
        return config;
    }

    /**
     * 获取分片调度器（用于内部使用）。
     * Get shard dispatcher (for internal use).
     *
     * @return 分片调度器 / shard dispatcher
     */
    public ShardDispatcher<K, T> getDispatcher() {
        return dispatcher;
    }
}
