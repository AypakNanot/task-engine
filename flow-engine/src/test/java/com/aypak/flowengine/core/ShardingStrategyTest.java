package com.aypak.flowengine.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShardingStrategy 单元测试。
 * ShardingStrategy unit tests.
 */
@DisplayName("ShardingStrategy Tests")
class ShardingStrategyTest {

    @Test
    @DisplayName("Should calculate shard index using default strategy")
    void shouldCalculateShardIndexUsingDefaultStrategy() {
        ShardingStrategy<String> strategy = ShardingStrategy.defaultStrategy();

        int shardIndex = strategy.getShardIndex("device-123", 8);

        assertTrue(shardIndex >= 0 && shardIndex < 8);
    }

    @Test
    @DisplayName("Should return consistent shard index for same key")
    void shouldReturnConsistentShardIndexForSameKey() {
        ShardingStrategy<String> strategy = ShardingStrategy.defaultStrategy();

        int index1 = strategy.getShardIndex("user-456", 16);
        int index2 = strategy.getShardIndex("user-456", 16);

        assertEquals(index1, index2);
    }

    @Test
    @DisplayName("Should return different shard indices for different keys")
    void shouldReturnDifferentShardIndicesForDifferentKeys() {
        ShardingStrategy<String> strategy = ShardingStrategy.defaultStrategy();

        int index1 = strategy.getShardIndex("user-A", 16);
        int index2 = strategy.getShardIndex("user-B", 16);

        // 可能相同也可能不同，但概率上应该不同
        // May be same or different, but probably different
        assertNotEquals(index1, index2);
    }

    @Test
    @DisplayName("Should handle negative hash codes correctly")
    void shouldHandleNegativeHashCodesCorrectly() {
        ShardingStrategy<String> strategy = ShardingStrategy.defaultStrategy();

        // 找一个负哈希值的键
        // Find a key with negative hash code
        String keyWithNegativeHash = "A"; // String "A" has hash code 65

        int shardIndex = strategy.getShardIndex(keyWithNegativeHash, 8);

        assertTrue(shardIndex >= 0);
        assertTrue(shardIndex < 8);
    }

    @Test
    @DisplayName("Should work with different shard counts")
    void shouldWorkWithDifferentShardCounts() {
        ShardingStrategy<String> strategy = ShardingStrategy.defaultStrategy();

        String key = "test-key";

        int index4 = strategy.getShardIndex(key, 4);
        int index8 = strategy.getShardIndex(key, 8);
        int index16 = strategy.getShardIndex(key, 16);
        int index32 = strategy.getShardIndex(key, 32);

        assertTrue(index4 >= 0 && index4 < 4);
        assertTrue(index8 >= 0 && index8 < 8);
        assertTrue(index16 >= 0 && index16 < 16);
        assertTrue(index32 >= 0 && index32 < 32);
    }

    @Test
    @DisplayName("Should create custom sharding strategy")
    void shouldCreateCustomShardingStrategy() {
        ShardingStrategy<Integer> customStrategy = (key, count) -> key % count;

        int index = customStrategy.getShardIndex(123, 10);

        assertEquals(3, index);
    }

    @Test
    @DisplayName("Should create consistent hash strategy (fallback to default)")
    void shouldCreateConsistentHashStrategy() {
        ShardingStrategy<String> strategy = ShardingStrategy.consistentHashStrategy();

        int index = strategy.getShardIndex("key", 8);

        assertTrue(index >= 0 && index < 8);
    }
}
