package com.aypak.engine.flow;

import com.aypak.engine.flow.core.FlowConfig;
import com.aypak.engine.flow.core.FlowEvent;
import com.aypak.engine.flow.core.FlowNode;
import com.aypak.engine.flow.core.RejectPolicy;
import com.aypak.engine.flow.core.ShardingStrategy;
import com.aypak.engine.flow.monitor.FlowMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShardedFlowEngineBuilder 单元测试。
 * ShardedFlowEngineBuilder unit tests.
 */
@DisplayName("ShardedFlowEngineBuilder Unit Tests")
class ShardedFlowEngineBuilderTest {

    @Test
    @DisplayName("Should create engine with default configuration")
    void shouldCreateEngineWithDefaultConfiguration() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .addNode("Node", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        assertNotNull(engine);
        assertTrue(engine.isRunning());
        assertNotNull(engine.getMetrics());

        engine.submit(new FlowEvent<>("key", "data"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine with custom name")
    void shouldCreateEngineWithCustomName() throws Exception {
        String customName = "MyCustomFlow";
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name(customName)
                .addNode("Node", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        FlowConfig<String, String> config = ((ShardedFlowEngineImpl<String, String>) engine).getConfig();
        assertEquals(customName, config.getName());

        engine.submit(new FlowEvent<>("key", "data"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine with custom shard count")
    void shouldCreateEngineWithCustomShardCount() throws Exception {
        int customShardCount = 16;
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .shardCount(customShardCount)
                .addNode("Node", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        FlowConfig<String, String> config = ((ShardedFlowEngineImpl<String, String>) engine).getConfig();
        assertEquals(customShardCount, config.getShardCount());

        engine.submit(new FlowEvent<>("key", "data"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine with custom queue capacity")
    void shouldCreateEngineWithCustomQueueCapacity() throws Exception {
        int customQueueCapacity = 5000;
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .queueCapacity(customQueueCapacity)
                .addNode("Node", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        FlowConfig<String, String> config = ((ShardedFlowEngineImpl<String, String>) engine).getConfig();
        assertEquals(customQueueCapacity, config.getQueueCapacity());

        engine.submit(new FlowEvent<>("key", "data"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine with custom reject policy")
    void shouldCreateEngineWithCustomRejectPolicy() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .rejectPolicy(RejectPolicy.BLOCK)
                .addNode("Node", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        FlowConfig<String, String> config = ((ShardedFlowEngineImpl<String, String>) engine).getConfig();
        assertEquals(RejectPolicy.BLOCK, config.getRejectPolicy());

        engine.submit(new FlowEvent<>("key", "data"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine with disabled metrics")
    void shouldCreateEngineWithDisabledMetrics() {
        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .metricsEnabled(false)
                .addNode("Node", (event, context) -> true)
                .build();

        FlowMetrics metrics = engine.getMetrics();
        assertNull(metrics);

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine with single node")
    void shouldCreateEngineWithSingleNode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .addNode("TestNode", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        engine.submit(new FlowEvent<>("key", "data"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine with multiple nodes")
    void shouldCreateEngineWithMultipleNodes() throws Exception {
        List<String> executedNodes = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .addNode("Node1", (event, context) -> {
                    executedNodes.add("Node1");
                    latch.countDown();
                    return true;
                })
                .addNode("Node2", (event, context) -> {
                    executedNodes.add("Node2");
                    latch.countDown();
                    return true;
                })
                .build();

        engine.submit(new FlowEvent<>("key", "data"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(2, executedNodes.size());

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine with addNodes method")
    void shouldCreateEngineWithAddNodesMethod() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);

        FlowNode<String, String> node1 = FlowNode.of("Node1", (event, context) -> {
            latch.countDown();
            return true;
        });

        FlowNode<String, String> node2 = FlowNode.of("Node2", (event, context) -> {
            latch.countDown();
            return true;
        });

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .addNodes(node1, node2)
                .build();

        engine.submit(new FlowEvent<>("key", "data"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine with custom sharding strategy")
    void shouldCreateEngineWithCustomShardingStrategy() throws Exception {
        ShardingStrategy<String> customStrategy = (shardKey, count) -> {
            // 自定义策略：所有键都路由到 0 号分片
            return 0;
        };

        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .shardingStrategy(customStrategy)
                .shardCount(4)
                .addNode("Node", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        FlowConfig<String, String> config = ((ShardedFlowEngineImpl<String, String>) engine).getConfig();
        assertNotNull(config);

        engine.submit(new FlowEvent<>("key", "data"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine with batch configuration")
    void shouldCreateEngineWithBatchConfiguration() throws Exception {
        int customBatchSize = 200;
        long customBatchTimeout = 2000L;
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .batchSize(customBatchSize)
                .batchTimeoutMs(customBatchTimeout)
                .addNode("Node", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        FlowConfig<String, String> config = ((ShardedFlowEngineImpl<String, String>) engine).getConfig();
        assertEquals(customBatchSize, config.getBatchSize());
        assertEquals(customBatchTimeout, config.getBatchTimeoutMs());

        engine.submit(new FlowEvent<>("key", "data"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should create engine without starting")
    void shouldCreateEngineWithoutStarting() {
        ShardedFlowEngineImpl<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .addNode("Node", (event, context) -> true)
                .buildWithoutStart();

        assertNotNull(engine);
        assertFalse(engine.isRunning());

        // 手动启动
        engine.start();
        assertTrue(engine.isRunning());

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should build engine with all configuration options")
    void shouldBuildEngineWithAllConfigurationOptions() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("FullConfigFlow")
                .shardCount(8)
                .queueCapacity(2000)
                .rejectPolicy(RejectPolicy.CALLER_RUNS)
                .metricsEnabled(true)
                .batchSize(150)
                .batchTimeoutMs(1500L)
                .addNode("Processor", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        FlowConfig<String, String> config = ((ShardedFlowEngineImpl<String, String>) engine).getConfig();

        assertEquals("FullConfigFlow", config.getName());
        assertEquals(8, config.getShardCount());
        assertEquals(2000, config.getQueueCapacity());
        assertEquals(RejectPolicy.CALLER_RUNS, config.getRejectPolicy());
        assertEquals(150, config.getBatchSize());
        assertEquals(1500L, config.getBatchTimeoutMs());

        engine.submit(new FlowEvent<>("key", "data"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should chain builder methods fluently")
    void shouldChainBuilderMethodsFluently() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        // 测试链式调用是否流畅
        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("ChainedFlow")
                .shardCount(4)
                .queueCapacity(500)
                .rejectPolicy(RejectPolicy.DROP)
                .metricsEnabled(true)
                .addNode("Node1", (event, context) -> true)
                .addNode("Node2", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        assertNotNull(engine);
        assertTrue(engine.isRunning());

        engine.submit(new FlowEvent<>("key", "data"));
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle node with default handler")
    void shouldHandleNodeWithDefaultHandler() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        // 使用默认节点名称
        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .addNode((event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        engine.submit(new FlowEvent<>("key", "data"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should process events in correct order with single shard")
    void shouldProcessEventsInCorrectOrderWithSingleShard() throws Exception {
        List<Integer> processedOrder = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(5);

        ShardedFlowEngine<String, Integer> engine = ShardedFlowEngine.<String, Integer>builder()
                .name("OrderedFlow")
                .shardCount(1)
                .addNode("Processor", (event, context) -> {
                    processedOrder.add(event.getPayload());
                    latch.countDown();
                    return true;
                })
                .build();

        for (int i = 0; i < 5; i++) {
            engine.submit(new FlowEvent<>("same-key", i));
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS));

        // 验证顺序
        for (int i = 0; i < 5; i++) {
            assertEquals(i, processedOrder.get(i));
        }

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle critical node correctly")
    void shouldHandleCriticalNodeCorrectly() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .addNode(new FlowNode<String, String>() {
                    @Override
                    public String getNodeName() {
                        return "CriticalNode";
                    }

                    @Override
                    public boolean process(FlowEvent<String, String> event, com.aypak.engine.flow.core.FlowContext context) {
                        latch.countDown();
                        throw new RuntimeException("Critical error");
                    }

                    @Override
                    public boolean isCritical() {
                        return true;
                    }
                })
                .addNode("SuccessCounter", (event, context) -> {
                    successCount.incrementAndGet();
                    return true;
                })
                .build();

        engine.submit(new FlowEvent<>("key", "data"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // 关键节点失败，第二个节点不应该执行
        assertEquals(0, successCount.get());

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should throw exception when validate fails with zero shard count")
    void shouldThrowExceptionWhenValidateFailsWithZeroShardCount() {
        // 0 个分片应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            ShardedFlowEngine.<String, String>builder()
                    .shardCount(0)
                    .addNode("Node", (event, context) -> true)
                    .build();
        });
    }

    @Test
    @DisplayName("Should throw exception when validate fails with negative shard count")
    void shouldThrowExceptionWhenValidateFailsWithNegativeShardCount() {
        // 负数分片应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            ShardedFlowEngine.<String, String>builder()
                    .shardCount(-1)
                    .addNode("Node", (event, context) -> true)
                    .build();
        });
    }

    @Test
    @DisplayName("Should throw exception when validate fails with zero queue capacity")
    void shouldThrowExceptionWhenValidateFailsWithZeroQueueCapacity() {
        // 0 队列容量应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            ShardedFlowEngine.<String, String>builder()
                    .queueCapacity(0)
                    .addNode("Node", (event, context) -> true)
                    .build();
        });
    }
}
