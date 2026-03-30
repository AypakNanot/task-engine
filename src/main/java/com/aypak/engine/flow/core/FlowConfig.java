package com.aypak.engine.flow.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 流配置 - 不可变配置对象（Builder 模式）。
 * Flow Configuration - immutable configuration object (Builder pattern).
 *
 * <p>使用示例 / Usage example:</p>
 * <pre>{@code
 * FlowConfig<String, OrderData> config = FlowConfig.<String, OrderData>builder()
 *     .name("OrderProcessor")
 *     .shardCount(16)
 *     .queueCapacity(5000)
 *     .rejectPolicy(RejectPolicy.BLOCK)
 *     .build();
 * }</pre>
 *
 * @param <K> 分片键类型 / shard key type
 * @param <T> 负载类型 / payload type
 */
public class FlowConfig<K, T> {

    /** 流名称 / Flow name */
    private final String name;

    /** 分片数量（Worker 数量） / Shard count (Worker count) */
    private final int shardCount;

    /** 每个分片的队列容量 / Queue capacity per shard */
    private final int queueCapacity;

    /** 拒绝策略 / Rejection policy */
    private final RejectPolicy rejectPolicy;

    /** 处理节点列表 / Processing nodes list */
    private final List<FlowNode<K, T>> nodes;

    /** 是否启用指标 / Whether metrics enabled */
    private final boolean metricsEnabled;

    /** 批处理大小（用于批量入库） / Batch size for batch persistence */
    private final int batchSize;

    /** 批处理超时（毫秒） / Batch timeout in milliseconds */
    private final long batchTimeoutMs;

    private FlowConfig(Builder<K, T> builder) {
        this.name = builder.name;
        this.shardCount = builder.shardCount;
        this.queueCapacity = builder.queueCapacity;
        this.rejectPolicy = builder.rejectPolicy;
        this.nodes = builder.nodes;
        this.metricsEnabled = builder.metricsEnabled;
        this.batchSize = builder.batchSize;
        this.batchTimeoutMs = builder.batchTimeoutMs;
    }

    // ==================== Getters ====================

    /**
     * 获取流名称。
     * Get flow name.
     */
    public String getName() {
        return name;
    }

    /**
     * 获取分片数量。
     * Get shard count.
     */
    public int getShardCount() {
        return shardCount;
    }

    /**
     * 获取队列容量。
     * Get queue capacity.
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * 获取拒绝策略。
     * Get rejection policy.
     */
    public RejectPolicy getRejectPolicy() {
        return rejectPolicy;
    }

    /**
     * 获取处理节点列表。
     * Get processing nodes list.
     */
    public List<FlowNode<K, T>> getNodes() {
        return new ArrayList<>(nodes);
    }

    /**
     * 是否启用指标。
     * Whether metrics enabled.
     */
    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    /**
     * 获取批处理大小。
     * Get batch size.
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * 获取批处理超时。
     * Get batch timeout.
     */
    public long getBatchTimeoutMs() {
        return batchTimeoutMs;
    }

    /**
     * 验证配置。
     * Validate configuration.
     *
     * @throws IllegalArgumentException 配置无效 / configuration invalid
     */
    public void validate() {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Flow name is required");
        }
        if (shardCount <= 0) {
            throw new IllegalArgumentException("Shard count must be positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("At least one node is required");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        if (batchTimeoutMs <= 0) {
            throw new IllegalArgumentException("Batch timeout must be positive");
        }
    }

    // ==================== Builder ====================

    /**
     * 创建配置构建器。
     * Create configuration builder.
     *
     * @param <K> 分片键类型 / shard key type
     * @param <T> 负载类型 / payload type
     * @return 构建器 / builder
     */
    public static <K, T> Builder<K, T> builder() {
        return new Builder<>();
    }

    /**
     * 配置构建器。
     * Configuration builder.
     *
     * @param <K> 分片键类型 / shard key type
     * @param <T> 负载类型 / payload type
     */
    public static class Builder<K, T> {

        private String name = "FlowEngine";
        private int shardCount = 8;
        private int queueCapacity = 1000;
        private RejectPolicy rejectPolicy = RejectPolicy.DROP;
        private final List<FlowNode<K, T>> nodes = new ArrayList<>();
        private boolean metricsEnabled = true;
        private int batchSize = 100;
        private long batchTimeoutMs = 1000;

        /**
         * 设置流名称。
         * Set flow name.
         */
        public Builder<K, T> name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 设置分片数量。
         * Set shard count.
         */
        public Builder<K, T> shardCount(int shardCount) {
            this.shardCount = shardCount;
            return this;
        }

        /**
         * 设置队列容量。
         * Set queue capacity.
         */
        public Builder<K, T> queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        /**
         * 设置拒绝策略。
         * Set rejection policy.
         */
        public Builder<K, T> rejectPolicy(RejectPolicy policy) {
            this.rejectPolicy = policy;
            return this;
        }

        /**
         * 添加处理节点。
         * Add processing node.
         */
        public Builder<K, T> addNode(FlowNode<K, T> node) {
            this.nodes.add(node);
            return this;
        }

        /**
         * 添加简单节点 - 使用处理函数。
         * Add simple node using processing function.
         *
         * @param name    节点名称 / node name
         * @param handler 处理函数 / processing function
         * @return 构建器 / builder
         */
        public Builder<K, T> addNode(String name, FlowNode.FlowHandler<K, T> handler) {
            this.nodes.add(FlowNode.of(name, handler));
            return this;
        }

        /**
         * 添加简单节点 - 使用默认名称。
         * Add simple node with default name.
         *
         * @param handler 处理函数 / processing function
         * @return 构建器 / builder
         */
        public Builder<K, T> addNode(FlowNode.FlowHandler<K, T> handler) {
            this.nodes.add(FlowNode.of(handler));
            return this;
        }

        /**
         * 设置是否启用指标。
         * Set whether metrics enabled.
         */
        public Builder<K, T> metricsEnabled(boolean enabled) {
            this.metricsEnabled = enabled;
            return this;
        }

        /**
         * 设置批处理大小。
         * Set batch size.
         */
        public Builder<K, T> batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * 设置批处理超时。
         * Set batch timeout.
         */
        public Builder<K, T> batchTimeoutMs(long timeoutMs) {
            this.batchTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * 构建配置。
         * Build configuration.
         */
        public FlowConfig<K, T> build() {
            return new FlowConfig<>(this);
        }
    }
}
