package com.aypak.engine.flow;

import com.aypak.engine.flow.core.FlowConfig;
import com.aypak.engine.flow.core.FlowNode;
import com.aypak.engine.flow.core.RejectPolicy;
import com.aypak.engine.flow.core.ShardingStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 分片流引擎构建器。
 * Sharded Flow Engine Builder.
 *
 * <p>提供 Fluent API 创建 ShardedFlowEngine 实例。</p>
 * <p>Provides Fluent API to create ShardedFlowEngine instances.</p>
 *
 * <p>使用示例 / Usage example:</p>
 * <pre>{@code
 * // 基本使用 / Basic usage
 * ShardedFlowEngine<String, OrderData> engine = ShardedFlowEngine.<String, OrderData>builder()
 *     .name("OrderProcessor")
 *     .shardCount(16)
 *     .queueCapacity(5000)
 *     .rejectPolicy(RejectPolicy.BLOCK)
 *     .addNode(new ValidateNode())
 *     .addNode(new SaveNode())
 *     .build();
 *
 * // 非 Spring 环境 / Non-Spring environment
 * engine.start();
 * engine.submit(new FlowEvent<>("user-123", orderData));
 * }</pre>
 *
 * @param <K> 分片键类型 / shard key type
 * @param <T> 负载类型 / payload type
 */
public class ShardedFlowEngineBuilder<K, T> {

    /** 流名称 / Flow name */
    private String name = "FlowEngine";

    /** 分片数量 / Shard count */
    private int shardCount = 8;

    /** 队列容量 / Queue capacity */
    private int queueCapacity = 1000;

    /** 拒绝策略 / Rejection policy */
    private RejectPolicy rejectPolicy = RejectPolicy.DROP;

    /** 分片策略 / Sharding strategy */
    private ShardingStrategy<K> shardingStrategy = ShardingStrategy.defaultStrategy();

    /** 处理节点列表 / Processing nodes list */
    private final List<FlowNode<K, T>> nodes = new ArrayList<>();

    /** 是否启用指标 / Whether metrics enabled */
    private boolean metricsEnabled = true;

    /** 批处理大小 / Batch size */
    private int batchSize = 100;

    /** 批处理超时（毫秒） / Batch timeout in milliseconds */
    private long batchTimeoutMs = 1000;

    /**
     * 设置流名称。
     * Set flow name.
     *
     * @param name 名称 / name
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> name(String name) {
        this.name = name;
        return this;
    }

    /**
     * 设置分片数量（Worker 数量）。
     * Set shard count (Worker count).
     *
     * @param shardCount 分片数量 / shard count
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> shardCount(int shardCount) {
        this.shardCount = shardCount;
        return this;
    }

    /**
     * 设置队列容量。
     * Set queue capacity.
     *
     * @param queueCapacity 队列容量 / queue capacity
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> queueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        return this;
    }

    /**
     * 设置拒绝策略。
     * Set rejection policy.
     *
     * @param policy 拒绝策略 / rejection policy
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> rejectPolicy(RejectPolicy policy) {
        this.rejectPolicy = policy;
        return this;
    }

    /**
     * 设置分片策略。
     * Set sharding strategy.
     *
     * @param strategy 分片策略 / sharding strategy
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> shardingStrategy(ShardingStrategy<K> strategy) {
        this.shardingStrategy = strategy;
        return this;
    }

    /**
     * 添加处理节点。
     * Add processing node.
     *
     * @param node 节点 / node
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> addNode(FlowNode<K, T> node) {
        this.nodes.add(node);
        return this;
    }

    /**
     * 添加简单节点 - 使用处理函数和节点名称。
     * Add simple node using processing function with node name.
     *
     * @param name    节点名称 / node name
     * @param handler 处理函数 / processing function
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> addNode(String name, FlowNode.FlowHandler<K, T> handler) {
        this.nodes.add(FlowNode.of(name, handler));
        return this;
    }

    /**
     * 添加简单节点 - 使用默认节点名称。
     * Add simple node with default node name.
     *
     * @param handler 处理函数 / processing function
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> addNode(FlowNode.FlowHandler<K, T> handler) {
        this.nodes.add(FlowNode.of(handler));
        return this;
    }

    /**
     * 添加多个处理节点。
     * Add multiple processing nodes.
     *
     * @param nodes 节点列表 / nodes list
     * @return this builder
     */
    @SafeVarargs
    public final ShardedFlowEngineBuilder<K, T> addNodes(FlowNode<K, T>... nodes) {
        for (FlowNode<K, T> node : nodes) {
            this.nodes.add(node);
        }
        return this;
    }

    /**
     * 设置是否启用指标。
     * Set whether metrics enabled.
     *
     * @param enabled 是否启用 / whether enabled
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> metricsEnabled(boolean enabled) {
        this.metricsEnabled = enabled;
        return this;
    }

    /**
     * 设置批处理大小。
     * Set batch size.
     *
     * @param batchSize 批处理大小 / batch size
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    /**
     * 设置批处理超时。
     * Set batch timeout.
     *
     * @param timeoutMs 超时时间（毫秒） / timeout in milliseconds
     * @return this builder
     */
    public ShardedFlowEngineBuilder<K, T> batchTimeoutMs(long timeoutMs) {
        this.batchTimeoutMs = timeoutMs;
        return this;
    }

    /**
     * 构建并启动引擎。
     * Build and start engine.
     *
     * @return ShardedFlowEngine 实例 / ShardedFlowEngine instance
     */
    public ShardedFlowEngine<K, T> build() {
        FlowConfig<K, T> config = createConfig();
        config.validate();

        ShardedFlowEngineImpl<K, T> engine = new ShardedFlowEngineImpl<>(config);
        engine.start();
        return engine;
    }

    /**
     * 构建引擎但不启动。
     * Build engine without starting.
     * 适用于需要手动控制启动时机的场景。
     * Suitable for scenarios requiring manual control of start timing.
     *
     * @return ShardedFlowEngineImpl 实例 / ShardedFlowEngineImpl instance
     */
    public ShardedFlowEngineImpl<K, T> buildWithoutStart() {
        FlowConfig<K, T> config = createConfig();
        config.validate();
        return new ShardedFlowEngineImpl<>(config);
    }

    /**
     * 创建配置。
     * Create configuration.
     *
     * @return FlowConfig 实例 / FlowConfig instance
     */
    private FlowConfig<K, T> createConfig() {
        FlowConfig.Builder<K, T> builder = FlowConfig.<K, T>builder()
                .name(this.name)
                .shardCount(this.shardCount)
                .queueCapacity(this.queueCapacity)
                .rejectPolicy(this.rejectPolicy)
                .metricsEnabled(this.metricsEnabled)
                .batchSize(this.batchSize)
                .batchTimeoutMs(this.batchTimeoutMs);

        for (FlowNode<K, T> node : this.nodes) {
            builder.addNode(node);
        }

        return builder.build();
    }
}
