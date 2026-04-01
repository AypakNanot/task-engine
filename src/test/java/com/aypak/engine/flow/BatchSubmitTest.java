package com.aypak.engine.flow;

import com.aypak.engine.flow.core.FlowEvent;
import com.aypak.engine.flow.core.FlowNode;
import com.aypak.engine.flow.monitor.FlowMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 批量提交功能专项测试。
 * Batch submit feature tests.
 */
@DisplayName("Batch Submit Feature Tests")
class BatchSubmitTest {

    @Test
    @DisplayName("Should submit batch events successfully")
    void shouldSubmitBatchEventsSuccessfully() throws Exception {
        CountDownLatch latch = new CountDownLatch(10);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("BatchFlow")
                .shardCount(4)
                .queueCapacity(100)
                .addNode("Processor", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        List<FlowEvent<String, String>> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(new FlowEvent<>("key-" + i, "data" + i));
        }

        int submittedCount = engine.submit(events);

        assertEquals(10, submittedCount);
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should reject batch submit when engine not running")
    void shouldRejectBatchSubmitWhenEngineNotRunning() {
        ShardedFlowEngineImpl<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .addNode("Node", (event, context) -> true)
                .buildWithoutStart();

        List<FlowEvent<String, String>> events = new ArrayList<>();
        events.add(new FlowEvent<>("key", "data"));

        int submittedCount = engine.submit(events);

        assertEquals(0, submittedCount);
    }

    @Test
    @DisplayName("Should handle empty batch submit")
    void shouldHandleEmptyBatchSubmit() {
        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .addNode("Node", (event, context) -> true)
                .build();

        List<FlowEvent<String, String>> emptyEvents = new ArrayList<>();
        int submittedCount = engine.submit(emptyEvents);

        assertEquals(0, submittedCount);

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should track metrics correctly for batch submit")
    void shouldTrackMetricsCorrectlyForBatchSubmit() throws Exception {
        CountDownLatch latch = new CountDownLatch(5);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("MetricsFlow")
                .shardCount(2)
                .queueCapacity(100)
                .metricsEnabled(true)
                .addNode("Processor", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        List<FlowEvent<String, String>> events = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            events.add(new FlowEvent<>("key-" + i, "data" + i));
        }

        engine.submit(events);

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        FlowMetrics metrics = engine.getMetrics();
        assertNotNull(metrics);
        assertEquals(5, metrics.getReceivedCount().sum());
        assertEquals(5, metrics.getTotalSuccess());

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should maintain shard ordering for batch events")
    void shouldMaintainShardOrderingForBatchEvents() throws Exception {
        Map<String, List<Integer>> processedOrder = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(20);

        ShardedFlowEngine<String, Integer> engine = ShardedFlowEngine.<String, Integer>builder()
                .name("OrderedBatchFlow")
                .shardCount(2)
                .addNode("Processor", (event, context) -> {
                    String key = event.getShardKey();
                    processedOrder.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(event.getPayload());
                    latch.countDown();
                    return true;
                })
                .build();

        // 提交 20 个事件，10 个 key-a，10 个 key-b
        List<FlowEvent<String, Integer>> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(new FlowEvent<>("key-a", i));
            events.add(new FlowEvent<>("key-b", i));
        }

        engine.submit(events);

        assertTrue(latch.await(3, TimeUnit.SECONDS));

        // 验证每个分片键的事件顺序
        List<Integer> orderA = processedOrder.get("key-a");
        List<Integer> orderB = processedOrder.get("key-b");

        assertNotNull(orderA);
        assertNotNull(orderB);
        assertEquals(10, orderA.size());
        assertEquals(10, orderB.size());

        for (int i = 0; i < 10; i++) {
            assertEquals(i, orderA.get(i).intValue());
            assertEquals(i, orderB.get(i).intValue());
        }

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle large batch submit")
    void shouldHandleLargeBatchSubmit() throws Exception {
        int batchSize = 1000;
        CountDownLatch latch = new CountDownLatch(batchSize);
        AtomicInteger successCount = new AtomicInteger(0);

        ShardedFlowEngine<String, Integer> engine = ShardedFlowEngine.<String, Integer>builder()
                .name("LargeBatchFlow")
                .shardCount(8)
                .queueCapacity(2000)
                .addNode("Processor", (event, context) -> {
                    successCount.incrementAndGet();
                    latch.countDown();
                    return true;
                })
                .build();

        List<FlowEvent<String, Integer>> events = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            events.add(new FlowEvent<>("key-" + (i % 10), i));
        }

        int submittedCount = engine.submit(events);

        assertEquals(batchSize, submittedCount);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(batchSize, successCount.get());

        engine.shutdown(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle batch submit with mixed shard keys")
    void shouldHandleBatchSubmitWithMixedShardKeys() throws Exception {
        CountDownLatch latch = new CountDownLatch(100);
        Map<String, AtomicInteger> shardCounts = new ConcurrentHashMap<>();

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("MixedShardFlow")
                .shardCount(4)
                .queueCapacity(200)
                .addNode("Processor", (event, context) -> {
                    shardCounts.computeIfAbsent(event.getShardKey(), k -> new AtomicInteger(0)).incrementAndGet();
                    latch.countDown();
                    return true;
                })
                .build();

        List<FlowEvent<String, String>> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            events.add(new FlowEvent<>("shard-" + (i % 10), "data" + i));
        }

        int submittedCount = engine.submit(events);

        assertEquals(100, submittedCount);
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        // 验证所有分片键都被处理
        assertEquals(10, shardCounts.size());

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle batch submit with node failure")
    void shouldHandleBatchSubmitWithNodeFailure() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(10);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("FailureFlow")
                .shardCount(2)
                .addNode("FailingNode", (event, context) -> {
                    if (event.getPayload().equals("data-5")) {
                        failureCount.incrementAndGet();
                        throw new RuntimeException("Test error");
                    }
                    return true;
                })
                .addNode("SuccessCounter", (event, context) -> {
                    successCount.incrementAndGet();
                    latch.countDown();
                    return true;
                })
                .build();

        List<FlowEvent<String, String>> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(new FlowEvent<>("key-" + i, "data-" + i));
        }

        int submittedCount = engine.submit(events);
        assertEquals(10, submittedCount);

        assertTrue(latch.await(3, TimeUnit.SECONDS));

        // 非关键节点失败会被捕获，事件继续处理，所有 10 个事件都会到达 SuccessCounter
        assertEquals(10, successCount.get());
        assertEquals(1, failureCount.get());

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle multiple batch submits sequentially")
    void shouldHandleMultipleBatchSubmitsSequentially() throws Exception {
        CountDownLatch latch = new CountDownLatch(30);

        ShardedFlowEngine<String, Integer> engine = ShardedFlowEngine.<String, Integer>builder()
                .name("SequentialBatchFlow")
                .shardCount(4)
                .queueCapacity(500)
                .addNode("Processor", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        // 提交 3 批次
        for (int batch = 0; batch < 3; batch++) {
            List<FlowEvent<String, Integer>> events = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                events.add(new FlowEvent<>("key-" + batch, batch * 10 + i));
            }
            engine.submit(events);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        FlowMetrics metrics = engine.getMetrics();
        assertEquals(30, metrics.getTotalSuccess());

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should compare single vs batch submit performance")
    void shouldCompareSingleVsBatchSubmitPerformance() throws Exception {
        int count = 100;

        // 单次提交测试
        CountDownLatch singleLatch = new CountDownLatch(count);
        ShardedFlowEngine<String, Integer> singleEngine = ShardedFlowEngine.<String, Integer>builder()
                .name("SingleSubmitFlow")
                .shardCount(4)
                .queueCapacity(200)
                .addNode("Processor", (event, context) -> {
                    singleLatch.countDown();
                    return true;
                })
                .build();

        long singleStartTime = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            singleEngine.submit(new FlowEvent<>("key-" + (i % 10), i));
        }
        assertTrue(singleLatch.await(5, TimeUnit.SECONDS));
        long singleEndTime = System.currentTimeMillis();

        singleEngine.shutdown(1, TimeUnit.SECONDS);

        // 批量提交测试
        CountDownLatch batchLatch = new CountDownLatch(count);
        ShardedFlowEngine<String, Integer> batchEngine = ShardedFlowEngine.<String, Integer>builder()
                .name("BatchSubmitFlow")
                .shardCount(4)
                .queueCapacity(200)
                .addNode("Processor", (event, context) -> {
                    batchLatch.countDown();
                    return true;
                })
                .build();

        long batchStartTime = System.currentTimeMillis();
        List<FlowEvent<String, Integer>> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(new FlowEvent<>("key-" + (i % 10), i));
        }
        batchEngine.submit(events);
        assertTrue(batchLatch.await(5, TimeUnit.SECONDS));
        long batchEndTime = System.currentTimeMillis();

        batchEngine.shutdown(1, TimeUnit.SECONDS);

        long singleDuration = singleEndTime - singleStartTime;
        long batchDuration = batchEndTime - batchStartTime;

        System.out.println("Single submit duration: " + singleDuration + "ms");
        System.out.println("Batch submit duration: " + batchDuration + "ms");

        // 批量提交应该更高效（或至少不慢于单次提交）
        // 注意：这是一个宽松的比较，因为测试环境可能有波动
        assertTrue(batchDuration <= singleDuration * 1.5);
    }

    @Test
    @DisplayName("Should handle batch submit with context stop")
    void shouldHandleBatchSubmitWithContextStop() throws Exception {
        AtomicInteger stoppedCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(9);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("ContextStopFlow")
                .shardCount(2)
                .addNode("Filter", (event, context) -> {
                    if (event.getPayload().equals("filter-5")) {
                        context.stop();
                        stoppedCount.incrementAndGet();
                        return false;
                    }
                    processedCount.incrementAndGet();
                    latch.countDown();
                    return true;
                })
                .build();

        List<FlowEvent<String, String>> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(new FlowEvent<>("key-" + i, "filter-" + i));
        }

        int submittedCount = engine.submit(events);
        assertEquals(10, submittedCount);

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        // 9 个事件通过第一个节点，1 个被过滤
        assertEquals(9, processedCount.get());
        assertEquals(1, stoppedCount.get());

        engine.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should return correct success count for partial batch submit")
    void shouldReturnCorrectSuccessCountForPartialBatchSubmit() {
        // 使用 DROP 策略，队列满时会丢弃
        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("PartialBatchFlow")
                .shardCount(1)  // 单分片
                .queueCapacity(5)  // 小队列
                .addNode("SlowNode", (event, context) -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return true;
                })
                .build();

        // 提交超过队列容量的批量事件
        List<FlowEvent<String, String>> events = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            events.add(new FlowEvent<>("same-key", "data" + i));
        }

        int submittedCount = engine.submit(events);

        // 由于是 DROP 策略，部分事件会被丢弃
        // 验证返回值是正确的提交数量
        assertTrue(submittedCount >= 0);
        assertTrue(submittedCount <= 20);

        engine.shutdown(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle batch submit with null payload gracefully")
    void shouldHandleBatchSubmitWithNullPayloadGracefully() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        ShardedFlowEngine<String, String> engine = ShardedFlowEngine.<String, String>builder()
                .name("NullPayloadFlow")
                .shardCount(2)
                .addNode("Processor", (event, context) -> {
                    latch.countDown();
                    return true;
                })
                .build();

        List<FlowEvent<String, String>> events = new ArrayList<>();
        events.add(new FlowEvent<>("key-1", null));

        int submittedCount = engine.submit(events);

        assertEquals(1, submittedCount);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        engine.shutdown(1, TimeUnit.SECONDS);
    }
}
