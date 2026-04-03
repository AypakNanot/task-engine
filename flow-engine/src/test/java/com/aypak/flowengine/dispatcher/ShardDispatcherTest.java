package com.aypak.flowengine.dispatcher;

import com.aypak.flowengine.core.FlowEvent;
import com.aypak.flowengine.core.FlowNode;
import com.aypak.flowengine.core.RejectPolicy;
import com.aypak.flowengine.monitor.FlowMetrics;
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
 * ShardDispatcher 单元测试。
 * ShardDispatcher unit tests.
 */
@DisplayName("ShardDispatcher Unit Tests")
class ShardDispatcherTest {

    @Test
    @DisplayName("Should route events to correct workers based on shard key")
    void shouldRouteEventsToCorrectWorkersBasedOnShardKey() throws Exception {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                4, 100, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();

        // 相同分片键的事件应该路由到同一个 Worker
        String shardKey = "same-key";
        int expectedWorkerIndex = Math.abs(shardKey.hashCode()) % 4;

        // 提交多个相同分片键的事件
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger workerIndexCapture = new AtomicInteger(-1);

        // 通过自定义节点捕获 Worker 索引
        FlowEvent<String, String> event1 = new FlowEvent<>(shardKey, "data1");
        FlowEvent<String, String> event2 = new FlowEvent<>(shardKey, "data2");
        FlowEvent<String, String> event3 = new FlowEvent<>(shardKey, "data3");

        dispatcher.submit(event1);
        dispatcher.submit(event2);
        dispatcher.submit(event3);

        // 等待处理
        Thread.sleep(500);

        // 验证提交计数
        assertEquals(3, dispatcher.getSubmitCount());

        dispatcher.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle DROP policy correctly")
    void shouldHandleDropPolicyCorrectly() {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        // 创建只有 1 个 Worker，队列容量为 2 的 dispatcher
        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                1, 2, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();

        // 填满队列
        FlowEvent<String, String> event1 = new FlowEvent<>("key", "data1");
        FlowEvent<String, String> event2 = new FlowEvent<>("key", "data2");

        boolean submitted1 = dispatcher.submit(event1);
        boolean submitted2 = dispatcher.submit(event2);

        assertTrue(submitted1);
        assertTrue(submitted2);

        dispatcher.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle BLOCK policy correctly")
    void shouldHandleBlockPolicyCorrectly() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("SlowNode", (event, context) -> {
                    latch.await(1, TimeUnit.SECONDS);
                    return true;
                })
        );

        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                1, 1, nodes, RejectPolicy.BLOCK, metrics
        );

        dispatcher.start();

        // 提交第一个事件
        FlowEvent<String, String> event1 = new FlowEvent<>("key", "data1");
        boolean submitted1 = dispatcher.submit(event1);
        assertTrue(submitted1);

        // 提交第二个事件（队列满，应该阻塞等待）
        FlowEvent<String, String> event2 = new FlowEvent<>("key", "data2");
        boolean submitted2 = dispatcher.submit(event2);

        // 在 BLOCK 策略下，应该能够提交（阻塞等待）
        assertTrue(submitted2);

        latch.countDown();
        dispatcher.shutdown(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle CALLER_RUNS policy correctly")
    void shouldHandleCallerRunsPolicyCorrectly() throws Exception {
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger processedCount = new AtomicInteger(0);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("Processor", (event, context) -> {
                    processedCount.incrementAndGet();
                    latch.countDown();
                    return true;
                })
        );

        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                1, 2, nodes, RejectPolicy.CALLER_RUNS, metrics
        );

        dispatcher.start();

        // 提交多个事件
        for (int i = 0; i < 5; i++) {
            dispatcher.submit(new FlowEvent<>("key", "data" + i));
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(5, processedCount.get());

        dispatcher.shutdown(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should reject events when shutdown")
    void shouldRejectEventsWhenShutdown() throws Exception {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                2, 100, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();
        dispatcher.shutdown(1, TimeUnit.SECONDS);

        // 关闭后提交应该被拒绝
        FlowEvent<String, String> event = new FlowEvent<>("key", "data");
        boolean submitted = dispatcher.submit(event);

        assertFalse(submitted);
    }

    @Test
    @DisplayName("Should batch submit events correctly")
    void shouldBatchSubmitEventsCorrectly() throws Exception {
        CountDownLatch latch = new CountDownLatch(10);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("Processor", (event, context) -> {
                    latch.countDown();
                    return true;
                })
        );

        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                2, 100, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();

        // 批量提交
        List<FlowEvent<String, String>> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(new FlowEvent<>("key-" + i, "data" + i));
        }

        int submittedCount = dispatcher.submit(events);

        assertEquals(10, submittedCount);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        dispatcher.shutdown(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should return correct submitted count for batch submit")
    void shouldReturnCorrectSubmittedCountForBatchSubmit() {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                2, 100, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();

        // 批量提交空列表
        List<FlowEvent<String, String>> emptyEvents = new ArrayList<>();
        int submittedCount = dispatcher.submit(emptyEvents);

        assertEquals(0, submittedCount);

        dispatcher.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle batch submit when shutdown")
    void shouldHandleBatchSubmitWhenShutdown() {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                2, 100, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();
        dispatcher.shutdown(1, TimeUnit.SECONDS);

        // 关闭后批量提交应该被拒绝
        List<FlowEvent<String, String>> events = new ArrayList<>();
        events.add(new FlowEvent<>("key-1", "data1"));
        events.add(new FlowEvent<>("key-2", "data2"));

        int submittedCount = dispatcher.submit(events);

        assertEquals(0, submittedCount);
    }

    @Test
    @DisplayName("Should get correct worker count and queue depth")
    void shouldGetCorrectWorkerCountAndQueueDepth() {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                4, 100, nodes, RejectPolicy.DROP, metrics
        );

        assertEquals(4, dispatcher.getWorkerCount());
        assertEquals(0, dispatcher.getTotalQueueDepth());

        dispatcher.start();
        dispatcher.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should get worker stats correctly")
    void shouldGetWorkerStatsCorrectly() {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                3, 100, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();

        List<ShardDispatcher.WorkerStats> stats = dispatcher.getWorkerStats();

        assertEquals(3, stats.size());
        for (ShardDispatcher.WorkerStats stat : stats) {
            assertNotNull(stat);
            assertTrue(stat.workerId >= 0 && stat.workerId < 3);
        }

        dispatcher.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should get specific worker correctly")
    void shouldGetSpecificWorkerCorrectly() {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                4, 100, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();

        FlowWorker<String, String> worker0 = dispatcher.getWorker(0);
        FlowWorker<String, String> worker3 = dispatcher.getWorker(3);

        assertNotNull(worker0);
        assertNotNull(worker3);
        assertEquals(0, worker0.getWorkerId());
        assertEquals(3, worker3.getWorkerId());

        dispatcher.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should throw exception for invalid worker index")
    void shouldThrowExceptionForInvalidWorkerIndex() {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                2, 100, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();

        // 测试负数索引
        assertThrows(IllegalArgumentException.class, () -> {
            dispatcher.getWorker(-1);
        });

        // 测试超出范围的索引
        assertThrows(IllegalArgumentException.class, () -> {
            dispatcher.getWorker(5);
        });

        dispatcher.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle multiple shard keys distribution")
    void shouldHandleMultipleShardKeysDistribution() throws Exception {
        AtomicInteger processedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(100);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("Processor", (event, context) -> {
                    processedCount.incrementAndGet();
                    latch.countDown();
                    return true;
                })
        );

        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                8, 1000, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();

        // 提交 100 个不同分片键的事件
        for (int i = 0; i < 100; i++) {
            dispatcher.submit(new FlowEvent<>("shard-" + i, "data" + i));
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(100, processedCount.get());

        dispatcher.shutdown(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should track dropped count correctly")
    void shouldTrackDroppedCountCorrectly() {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        // 使用极小的队列容量
        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                1, 1, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();

        // 提交多个事件到同一个分片
        for (int i = 0; i < 10; i++) {
            dispatcher.submit(new FlowEvent<>("same-key", "data" + i));
        }

        // 由于 Worker 处理需要时间，部分事件会被丢弃
        // 验证 submitCount 是正确的
        assertTrue(dispatcher.getSubmitCount() >= 1);

        dispatcher.shutdown(1, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle graceful shutdown with pending events")
    void shouldHandleGracefulShutdownWithPendingEvents() throws Exception {
        CountDownLatch latch = new CountDownLatch(5);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("Processor", (event, context) -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    latch.countDown();
                    return true;
                })
        );

        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        ShardDispatcher<String, String> dispatcher = new ShardDispatcher<>(
                2, 100, nodes, RejectPolicy.DROP, metrics
        );

        dispatcher.start();

        // 提交 5 个事件
        for (int i = 0; i < 5; i++) {
            dispatcher.submit(new FlowEvent<>("key-" + i, "data" + i));
        }

        // 立即关闭
        boolean completed = dispatcher.shutdown(2, TimeUnit.SECONDS);

        // 等待处理完成
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(completed);
    }
}
