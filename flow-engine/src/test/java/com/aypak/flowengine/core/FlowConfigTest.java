package com.aypak.flowengine.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FlowConfig 单元测试。
 * FlowConfig unit tests.
 */
@DisplayName("FlowConfig Tests")
class FlowConfigTest {

    @Test
    @DisplayName("Should create config with default values")
    void shouldCreateConfigWithDefaultValues() {
        FlowConfig<String, String> config = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .build();

        assertEquals("TestFlow", config.getName());
        assertEquals(8, config.getShardCount());
        assertEquals(1000, config.getQueueCapacity());
        assertEquals(RejectPolicy.DROP, config.getRejectPolicy());
        assertTrue(config.isMetricsEnabled());
        assertEquals(100, config.getBatchSize());
        assertEquals(1000, config.getBatchTimeoutMs());
    }

    @Test
    @DisplayName("Should create config with custom values")
    void shouldCreateConfigWithCustomValues() {
        FlowConfig<String, String> config = FlowConfig.<String, String>builder()
                .name("CustomFlow")
                .shardCount(16)
                .queueCapacity(5000)
                .rejectPolicy(RejectPolicy.BLOCK)
                .metricsEnabled(false)
                .batchSize(500)
                .batchTimeoutMs(2000)
                .addNode("Node", (event, context) -> true)
                .build();

        assertEquals("CustomFlow", config.getName());
        assertEquals(16, config.getShardCount());
        assertEquals(5000, config.getQueueCapacity());
        assertEquals(RejectPolicy.BLOCK, config.getRejectPolicy());
        assertFalse(config.isMetricsEnabled());
        assertEquals(500, config.getBatchSize());
        assertEquals(2000, config.getBatchTimeoutMs());
        assertEquals(1, config.getNodes().size());
    }

    @Test
    @DisplayName("Should add multiple nodes")
    void shouldAddMultipleNodes() {
        FlowConfig<String, String> config = FlowConfig.<String, String>builder()
                .name("MultiNodeFlow")
                .addNode("Node1", (event, context) -> true)
                .addNode("Node2", (event, context) -> false)
                .addNode("Node3", (event, context) -> true)
                .build();

        assertEquals(3, config.getNodes().size());
    }

    @Test
    @DisplayName("Should return defensive copy of nodes")
    void shouldReturnDefensiveCopyOfNodes() {
        FlowConfig<String, String> config = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .addNode("Node", (event, context) -> true)
                .build();

        List<FlowNode<String, String>> nodes = config.getNodes();
        nodes.clear();

        assertEquals(1, config.getNodes().size());
    }

    @Test
    @DisplayName("Should validate valid config")
    void shouldValidateValidConfig() {
        FlowConfig<String, String> config = FlowConfig.<String, String>builder()
                .name("ValidFlow")
                .shardCount(4)
                .queueCapacity(1000)
                .addNode("Node", (event, context) -> true)
                .build();

        assertDoesNotThrow(config::validate);
    }

    @Test
    @DisplayName("Should throw when name is empty")
    void shouldThrowWhenNameIsEmpty() {
        FlowConfig<String, String> config = FlowConfig.<String, String>builder()
                .name("")
                .build();

        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    @DisplayName("Should throw when shard count is zero or negative")
    void shouldThrowWhenShardCountIsInvalid() {
        FlowConfig<String, String> config1 = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .shardCount(0)
                .addNode("Node", (event, context) -> true)
                .build();

        FlowConfig<String, String> config2 = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .shardCount(-1)
                .addNode("Node", (event, context) -> true)
                .build();

        assertThrows(IllegalArgumentException.class, config1::validate);
        assertThrows(IllegalArgumentException.class, config2::validate);
    }

    @Test
    @DisplayName("Should throw when queue capacity is zero or negative")
    void shouldThrowWhenQueueCapacityIsInvalid() {
        FlowConfig<String, String> config1 = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .queueCapacity(0)
                .addNode("Node", (event, context) -> true)
                .build();

        FlowConfig<String, String> config2 = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .queueCapacity(-1)
                .addNode("Node", (event, context) -> true)
                .build();

        assertThrows(IllegalArgumentException.class, config1::validate);
        assertThrows(IllegalArgumentException.class, config2::validate);
    }

    @Test
    @DisplayName("Should throw when no nodes are added")
    void shouldThrowWhenNoNodesAreAdded() {
        FlowConfig<String, String> config = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .build();

        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    @DisplayName("Should throw when batch size is invalid")
    void shouldThrowWhenBatchSizeIsInvalid() {
        FlowConfig<String, String> config1 = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .batchSize(0)
                .addNode("Node", (event, context) -> true)
                .build();

        FlowConfig<String, String> config2 = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .batchSize(-1)
                .addNode("Node", (event, context) -> true)
                .build();

        assertThrows(IllegalArgumentException.class, config1::validate);
        assertThrows(IllegalArgumentException.class, config2::validate);
    }

    @Test
    @DisplayName("Should throw when batch timeout is invalid")
    void shouldThrowWhenBatchTimeoutIsInvalid() {
        FlowConfig<String, String> config1 = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .batchTimeoutMs(0)
                .addNode("Node", (event, context) -> true)
                .build();

        FlowConfig<String, String> config2 = FlowConfig.<String, String>builder()
                .name("TestFlow")
                .batchTimeoutMs(-1)
                .addNode("Node", (event, context) -> true)
                .build();

        assertThrows(IllegalArgumentException.class, config1::validate);
        assertThrows(IllegalArgumentException.class, config2::validate);
    }
}
