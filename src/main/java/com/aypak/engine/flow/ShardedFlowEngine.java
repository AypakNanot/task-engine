package com.aypak.engine.flow;

import com.aypak.engine.flow.core.FlowEvent;
import com.aypak.engine.flow.core.FlowConfig;
import com.aypak.engine.flow.monitor.FlowMetrics;

/**
 * 分片流引擎接口。
 * Sharded Flow Engine interface.
 *
 * <p>主要入口点，用于提交事件和管理引擎。</p>
 * <p>Main entry point for submitting events and managing engine.</p>
 *
 * <p>使用示例 / Usage example:</p>
 * <pre>{@code
 * ShardedFlowEngine<String, OrderData> engine = ShardedFlowEngine.builder()
 *     .name("OrderProcessor")
 *     .shardCount(16)
 *     .queueCapacity(5000)
 *     .addNode(new ValidateNode())
 *     .addNode(new SaveNode())
 *     .build();
 *
 * engine.start();
 * engine.submit(new FlowEvent<>("user-123", orderData));
 * }</pre>
 *
 * @param <K> 分片键类型 / shard key type
 * @param <T> 负载类型 / payload type
 */
public interface ShardedFlowEngine<K, T> {

    /**
     * 启动引擎。
     * Start engine.
     */
    void start();

    /**
     * 提交事件。
     * Submit event.
     *
     * @param event 事件 / event
     * @return true 表示成功接收，false 表示被拒绝 / true if successfully received, false if rejected
     */
    boolean submit(FlowEvent<K, T> event);

    /**
     * 获取流指标。
     * Get flow metrics.
     *
     * @return 流指标 / flow metrics
     */
    FlowMetrics getMetrics();

    /**
     * 获取引擎状态。
     * Get engine status.
     *
     * @return true 表示运行中 / true if running
     */
    boolean isRunning();

    /**
     * 停止引擎。
     * Stop engine.
     */
    void stop();

    /**
     * 优雅关闭引擎。
     * Shutdown engine gracefully.
     *
     * @param timeout 超时时间 / timeout
     * @param unit    时间单位 / time unit
     * @return true 表示正常关闭，false 表示超时 / true if shutdown completed, false if timed out
     */
    boolean shutdown(long timeout, java.util.concurrent.TimeUnit unit);

    /**
     * 获取配置。
     * Get configuration.
     *
     * @return 流配置 / flow configuration
     */
    FlowConfig<K, T> getConfig();

    /**
     * 创建构建器。
     * Create builder.
     *
     * @param <K> 分片键类型 / shard key type
     * @param <T> 负载类型 / payload type
     * @return 构建器 / builder
     */
    static <K, T> ShardedFlowEngineBuilder<K, T> builder() {
        return new ShardedFlowEngineBuilder<>();
    }
}
