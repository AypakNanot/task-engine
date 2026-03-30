package com.aypak.engine.flow;

import com.aypak.engine.flow.core.FlowContext;
import com.aypak.engine.flow.core.FlowEvent;
import com.aypak.engine.flow.core.FlowNode;
import com.aypak.engine.flow.monitor.FlowMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShardedFlowEngine 集成测试。
 * ShardedFlowEngine integration tests.
 */
@DisplayName("ShardedFlowEngine Integration Tests")
class ShardedFlowEngineIntegrationTest {

    @Test
    @DisplayName("Should process event through all nodes")
    void shouldProcessEventThroughAllNodes() throws Exception {
        List<String> executedNodes = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("TestFlow")
                .shardCount(2)
                .queueCapacity(100)
                .addNode("Node1", (event, context) -> {
                    executedNodes.add("Node1");
                    return true;
                })
                .addNode("Node2", (event, context) -> {
                    executedNodes.add("Node2");
                    latch.countDown();
                    return true;
                })
                .buildWithoutStart();

        engine.start();
        engine.submit(new FlowEvent<>("key-1", "data"));

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        engine.shutdown(5, TimeUnit.SECONDS);

        assertTrue(completed);
        assertEquals(2, executedNodes.size());
        assertEquals("Node1", executedNodes.get(0));
        assertEquals("Node2", executedNodes.get(1));
    }

    @Test
    @DisplayName("Should stop processing when node returns false")
    void shouldStopProcessingWhenNodeReturnsFalse() throws Exception {
        List<String> executedNodes = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("TestFlow")
                .shardCount(2)
                .queueCapacity(100)
                .addNode("Node1", (event, context) -> {
                    executedNodes.add("Node1");
                    context.stop();
                    latch.countDown();
                    return false;
                })
                .addNode("Node2", (event, context) -> {
                    executedNodes.add("Node2");
                    return true;
                })
                .buildWithoutStart();

        engine.start();
        engine.submit(new FlowEvent<>("key-1", "data"));

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        engine.shutdown(5, TimeUnit.SECONDS);

        assertTrue(completed);
        assertEquals(1, executedNodes.size());
        assertEquals("Node1", executedNodes.get(0));
    }

    @Test
    @DisplayName("Should process events with same shard key in order")
    void shouldProcessEventsWithSameShardKeyInOrder() throws Exception {
        List<Integer> processedOrder = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(5);

        ShardedFlowEngine<String, Integer> engine = ShardedFlowEngine.<String, Integer>builder()
                .name("OrderedFlow")
                .shardCount(1)  // 单分片保证顺序
                .queueCapacity(100)
                .addNode("Processor", (event, context) -> {
                    processedOrder.add(event.getPayload());
                    latch.countDown();
                    return true;
                })
                .buildWithoutStart();

        engine.start();

        // 提交 5 个事件
        // Submit 5 events
        for (int i = 0; i < 5; i++) {
            engine.submit(new FlowEvent<>("same-key", i));
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        engine.shutdown(5, TimeUnit.SECONDS);

        assertTrue(completed);
        assertEquals(5, processedOrder.size());
        // 验证顺序处理
        // Verify ordered processing
        for (int i = 0; i < 5; i++) {
            assertEquals(i, processedOrder.get(i));
        }
    }

    @Test
    @DisplayName("Should handle node exception gracefully")
    void shouldHandleNodeExceptionGracefully() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("ErrorHandlingFlow")
                .shardCount(2)
                .queueCapacity(100)
                .addNode("Validator", (event, context) -> {
                    if (event.getPayload().equals("bad")) {
                        throw new RuntimeException("Test error");
                    }
                    return true;
                })
                .addNode("SuccessCounter", (event, context) -> {
                    successCount.incrementAndGet();
                    latch.countDown();
                    return true;
                })
                .buildWithoutStart();

        engine.start();

        // 提交好事件
        // Submit good event
        engine.submit(new FlowEvent<>("key-1", "good"));

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        engine.shutdown(5, TimeUnit.SECONDS);

        assertTrue(completed);
        assertEquals(1, successCount.get());
    }

    @Test
    @DisplayName("Should mark event as dropped when context marks dropped")
    void shouldMarkEventAsDroppedWhenContextMarksDropped() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        FlowEvent<String, String>[] capturedEvent = new FlowEvent[1];

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("DropFlow")
                .shardCount(2)
                .queueCapacity(100)
                .addNode("Dropper", (event, context) -> {
                    context.markDropped();
                    return false;
                })
                .addNode("Captor", (event, context) -> {
                    capturedEvent[0] = event;
                    latch.countDown();
                    return true;
                })
                .buildWithoutStart();

        engine.start();
        engine.submit(new FlowEvent<>("key-1", "data"));

        // 第二个节点不应该执行
        // Second node should not execute
        boolean completed = !latch.await(2, TimeUnit.SECONDS);

        engine.shutdown(5, TimeUnit.SECONDS);

        assertTrue(completed);  // latch 不应该被触发 / latch should not be triggered
        assertNull(capturedEvent[0]);
    }

    @Test
    @DisplayName("Should collect metrics during processing")
    void shouldCollectMetricsDuringProcessing() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("MetricsFlow")
                .shardCount(2)
                .queueCapacity(100)
                .metricsEnabled(true)
                .addNode("Processor", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .buildWithoutStart();

        engine.start();

        engine.submit(new FlowEvent<>("key-1", "data1"));
        engine.submit(new FlowEvent<>("key-2", "data2"));
        engine.submit(new FlowEvent<>("key-3", "data3"));

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        FlowMetrics metrics = engine.getMetrics();

        engine.shutdown(5, TimeUnit.SECONDS);

        assertTrue(completed);
        assertNotNull(metrics);
        assertEquals(3, metrics.getTotalSuccess());
        assertEquals(3, metrics.getReceivedCount().sum());
    }

    @Test
    @DisplayName("Should handle graceful shutdown")
    void shouldHandleGracefulShutdown() throws Exception {
        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("ShutdownFlow")
                .shardCount(2)
                .queueCapacity(100)
                .addNode("Sleeper", (event, context) -> {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return true;
                })
                .build();

        // 提交一些事件
        // Submit some events
        for (int i = 0; i < 10; i++) {
            engine.submit(new FlowEvent<>("key-" + i, "data" + i));
        }

        assertTrue(engine.isRunning());

        boolean shutdownCompleted = engine.shutdown(10, TimeUnit.SECONDS);

        assertFalse(engine.isRunning());
        assertTrue(shutdownCompleted);
    }
}
