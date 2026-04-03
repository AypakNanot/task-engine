package com.aypak.flowengine.core;

/**
 * 分片策略接口。
 * Sharding strategy interface.
 *
 * <p>用于计算事件的路由分片索引。</p>
 * <p>Used to calculate routing shard index for events.</p>
 *
 * <p>使用示例 / Usage example:</p>
 * <pre>{@code
 * // 使用默认哈希策略
 * ShardingStrategy<String> strategy = ShardingStrategy.defaultStrategy();
 *
 * // 自定义策略：基于设备类型分片
 * ShardingStrategy<String> customStrategy = (key, count) -> {
 *     String deviceType = extractDeviceType(key);
 *     return Math.abs(deviceType.hashCode()) % count;
 * };
 * }</pre>
 *
 * @param <K> 分片键类型 / shard key type
 */
@FunctionalInterface
public interface ShardingStrategy<K> {

    /**
     * 计算分片索引。
     * Calculate shard index.
     *
     * @param key        分片键 / shard key
     * @param shardCount 分片数量 / shard count
     * @return 分片索引 (0 到 shardCount-1) / shard index (0 to shardCount-1)
     */
    int getShardIndex(K key, int shardCount);

    /**
     * 默认策略：哈希取模。
     * Default strategy: hash modulo.
     *
     * @param <K> 分片键类型 / shard key type
     * @return 默认策略 / default strategy
     */
    static <K> ShardingStrategy<K> defaultStrategy() {
        return (key, count) -> {
            int hash = key.hashCode();
            // 确保哈希值为正，避免负数取模问题
            // Ensure hash is positive to avoid negative modulo
            hash = Math.abs(hash);
            return hash % count;
        };
    }

    /**
     * 一致性哈希策略（预留）。
     * Consistent hash strategy (reserved for future implementation).
     *
     * @param <K> 分片键类型 / shard key type
     * @return 一致性哈希策略 / consistent hash strategy
     */
    static <K> ShardingStrategy<K> consistentHashStrategy() {
        // TODO: 实现一致性哈希 / Implement consistent hashing
        return defaultStrategy();
    }
}
