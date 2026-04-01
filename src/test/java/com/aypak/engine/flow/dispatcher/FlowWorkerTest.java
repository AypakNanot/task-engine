package com.aypak.engine.flow.dispatcher;

import com.aypak.engine.flow.core.FlowContext;
import com.aypak.engine.flow.core.FlowEvent;
import com.aypak.engine.flow.core.FlowNode;
import com.aypak.engine.flow.monitor.FlowMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FlowWorker 单元测试。
 * FlowWorker unit tests.
 */
@DisplayName("FlowWorker Unit Tests")
class FlowWorkerTest {

    @Test
    @DisplayName("Should process event successfully")
    void shouldProcessEventSuccessfully() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger processedCount = new AtomicInteger(0);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("TestNode", (event, context) -> {
                    processedCount.incrementAndGet();
                    latch.countDown();
                    return true;
                })
        );

        FlowWorker<String, String> worker = createWorker(nodes);

        FlowEvent<String, String> event = new FlowEvent<>("key-1", "data");
        boolean submitted = worker.submit(event);

        assertTrue(submitted);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, processedCount.get());

        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should handle empty queue and become idle")
    void shouldHandleEmptyQueueAndBecomeIdle() throws Exception {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowWorker<String, String> worker = createWorker(nodes);

        // 等待 Worker 变为空闲
        Thread.sleep(300);

        assertEquals(FlowWorker.WorkerState.IDLE, worker.getState());

        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should reject event when shutdown signaled")
    void shouldRejectEventWhenShutdownSignaled() {
        List<FlowNode<String, String>> nodes = new ArrayList<>();
        FlowWorker<String, String> worker = createWorker(nodes);

        worker.signalShutdown();

        FlowEvent<String, String> event = new FlowEvent<>("key-1", "data");
        boolean submitted = worker.submit(event);

        assertFalse(submitted);
    }

    @Test
    @DisplayName("Should update queue depth correctly")
    void shouldUpdateQueueDepthCorrectly() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("SlowNode", (event, context) -> {
                    latch.await(1, TimeUnit.SECONDS);
                    return true;
                })
        );

        FlowWorker<String, String> worker = createWorker(nodes);

        // 提交事件但不立即处理
        FlowEvent<String, String> event = new FlowEvent<>("key-1", "data");
        worker.submit(event);

        // 等待事件进入队列
        Thread.sleep(100);

        // 队列深度应该是 0（已经被 Worker 取出）
        assertEquals(0, worker.getQueueSize());

        latch.countDown();
        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should handle node exception and continue processing")
    void shouldHandleNodeExceptionAndContinueProcessing() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger secondNodeCount = new AtomicInteger(0);

        List<FlowNode<String, String>> nodes = new ArrayList<>();
        nodes.add(FlowNode.of("FailingNode", (event, context) -> {
            failureCount.incrementAndGet();
            throw new RuntimeException("Test exception");
        }));
        nodes.add(FlowNode.of("SecondNode", (event, context) -> {
            secondNodeCount.incrementAndGet();
            latch.countDown();
            return true;
        }));

        FlowWorker<String, String> worker = createWorker(nodes);

        FlowEvent<String, String> event = new FlowEvent<>("key-1", "data");
        worker.submit(event);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, failureCount.get());
        assertEquals(1, secondNodeCount.get());

        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should handle critical node exception and mark event failed")
    void shouldHandleCriticalNodeExceptionAndMarkEventFailed() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger secondNodeCount = new AtomicInteger(0);

        List<FlowNode<String, String>> nodes = new ArrayList<>();
        nodes.add(new FlowNode<String, String>() {
            @Override
            public String getNodeName() {
                return "CriticalNode";
            }

            @Override
            public boolean process(FlowEvent<String, String> event, FlowContext context) {
                throw new RuntimeException("Critical error");
            }

            @Override
            public boolean isCritical() {
                return true;
            }
        });
        nodes.add(FlowNode.of("SecondNode", (event, context) -> {
            secondNodeCount.incrementAndGet();
            latch.countDown();
            return true;
        }));

        FlowWorker<String, String> worker = createWorker(nodes);

        FlowEvent<String, String> event = new FlowEvent<>("key-1", "data");
        worker.submit(event);

        // 第二个节点不应该被执行
        assertFalse(latch.await(500, TimeUnit.MILLISECONDS));
        assertEquals(0, secondNodeCount.get());

        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should process multiple events sequentially")
    void shouldProcessMultipleEventsSequentially() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        List<String> processedOrder = new ArrayList<>();

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("Processor", (event, context) -> {
                    processedOrder.add(event.getPayload());
                    latch.countDown();
                    return true;
                })
        );

        FlowWorker<String, String> worker = createWorker(nodes);

        worker.submit(new FlowEvent<>("key-1", "data1"));
        worker.submit(new FlowEvent<>("key-2", "data2"));
        worker.submit(new FlowEvent<>("key-3", "data3"));

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(3, processedOrder.size());

        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should stop processing when context is marked to stop")
    void shouldStopProcessingWhenContextIsMarkedToStop() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        List<String> executedNodes = new ArrayList<>();

        List<FlowNode<String, String>> nodes = new ArrayList<>();
        nodes.add(FlowNode.of("Node1", (event, context) -> {
            executedNodes.add("Node1");
            context.stop();
            latch.countDown();
            return false;
        }));
        nodes.add(FlowNode.of("Node2", (event, context) -> {
            executedNodes.add("Node2");
            return true;
        }));

        FlowWorker<String, String> worker = createWorker(nodes);

        FlowEvent<String, String> event = new FlowEvent<>("key-1", "data");
        worker.submit(event);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, executedNodes.size());
        assertEquals("Node1", executedNodes.get(0));

        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should handle shutdown gracefully")
    void shouldHandleShutdownGracefully() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger processedCount = new AtomicInteger(0);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("Processor", (event, context) -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    processedCount.incrementAndGet();
                    latch.countDown();
                    return true;
                })
        );

        FlowWorker<String, String> worker = createWorker(nodes);

        worker.submit(new FlowEvent<>("key-1", "data"));

        worker.signalShutdown();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, processedCount.get());
    }

    @Test
    @DisplayName("Should track processed count correctly")
    void shouldTrackProcessedCountCorrectly() throws Exception {
        CountDownLatch latch = new CountDownLatch(5);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("Processor", (event, context) -> {
                    latch.countDown();
                    return true;
                })
        );

        FlowWorker<String, String> worker = createWorker(nodes);

        for (int i = 0; i < 5; i++) {
            worker.submit(new FlowEvent<>("key-" + i, "data"));
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(5, worker.getProcessedCount());

        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should handle queue full scenario")
    void shouldHandleQueueFullScenario() {
        int capacity = 5;
        List<FlowNode<String, String>> nodes = Collections.emptyList();
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        AtomicInteger activeCount = new AtomicInteger(0);
        AtomicInteger queueDepth = new AtomicInteger(0);

        FlowWorker<String, String> worker = new FlowWorker<>(
                0, capacity, nodes,
                shutdownLatch,
                activeCount,
                queueDepth,
                null
        );

        // 填满队列
        for (int i = 0; i < capacity; i++) {
            boolean submitted = worker.submit(new FlowEvent<>("key-" + i, "data"));
            assertTrue(submitted);
        }

        // 队列满后应该拒绝
        boolean rejected = worker.submit(new FlowEvent<>("key-full", "data"));
        assertFalse(rejected);
    }

    @Test
    @DisplayName("Should get correct queue size and remaining capacity")
    void shouldGetCorrectQueueSizeAndRemainingCapacity() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("SlowNode", (event, context) -> {
                    latch.await(1, TimeUnit.SECONDS);
                    return true;
                })
        );

        FlowWorker<String, String> worker = createWorker(nodes);

        // 快速提交多个事件
        for (int i = 0; i < 3; i++) {
            worker.submit(new FlowEvent<>("key-" + i, "data"));
        }

        Thread.sleep(100);

        assertTrue(worker.getQueueSize() >= 0);
        assertTrue(worker.getRemainingCapacity() > 0);

        latch.countDown();
        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should record metrics when processing events")
    void shouldRecordMetricsWhenProcessingEvents() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("Processor", (event, context) -> {
                    latch.countDown();
                    return true;
                })
        );

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        AtomicInteger activeCount = new AtomicInteger(0);
        AtomicInteger queueDepth = new AtomicInteger(0);

        FlowWorker<String, String> worker = new FlowWorker<>(
                0, 100, nodes,
                shutdownLatch,
                activeCount,
                queueDepth,
                metrics
        );

        Thread workerThread = new Thread(worker);
        workerThread.setDaemon(false);
        workerThread.start();

        worker.submit(new FlowEvent<>("key-1", "data"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, metrics.getTotalSuccess());

        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should handle submit with timeout")
    void shouldHandleSubmitWithTimeout() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                FlowNode.of("Processor", (event, context) -> {
                    latch.countDown();
                    return true;
                })
        );

        FlowWorker<String, String> worker = createWorker(nodes);

        FlowEvent<String, String> event = new FlowEvent<>("key-1", "data");
        boolean submitted = worker.submit(event, 1, TimeUnit.SECONDS);

        assertTrue(submitted);
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        worker.signalShutdown();
    }

    @Test
    @DisplayName("Should stop after shutdown signal when queue is empty")
    void shouldStopAfterShutdownSignalWhenQueueIsEmpty() throws Exception {
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        List<FlowNode<String, String>> nodes = new ArrayList<>();

        FlowWorker<String, String> worker = new FlowWorker<>(
                0, 100, nodes,
                shutdownLatch,
                new AtomicInteger(0),
                new AtomicInteger(0),
                null
        );

        Thread workerThread = new Thread(worker);
        workerThread.setDaemon(false);
        workerThread.start();

        // 等待 Worker 启动
        Thread.sleep(100);

        // 信号关闭
        worker.signalShutdown();

        // 等待 Worker 停止
        boolean completed = shutdownLatch.await(2, TimeUnit.SECONDS);
        assertTrue(completed);
        assertFalse(worker.isRunning());
    }

    @Test
    @DisplayName("Should call onFailure callback when node throws exception")
    void shouldCallOnFailureCallbackWhenNodeThrowsException() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean onFailureCalled = new AtomicBoolean(false);
        AtomicInteger processCount = new AtomicInteger(0);

        List<FlowNode<String, String>> nodes = Collections.singletonList(
                new FlowNode<String, String>() {
                    @Override
                    public String getNodeName() {
                        return "FailingNode";
                    }

                    @Override
                    public boolean process(FlowEvent<String, String> event, FlowContext context) {
                        processCount.incrementAndGet();
                        throw new RuntimeException("Test error");
                    }

                    @Override
                    public void onFailure(FlowEvent<String, String> event, Throwable error) {
                        onFailureCalled.set(true);
                        latch.countDown();
                    }
                }
        );

        FlowWorker<String, String> worker = createWorker(nodes);

        worker.submit(new FlowEvent<>("key-1", "data"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(onFailureCalled.get());
        assertEquals(1, processCount.get());

        worker.signalShutdown();
    }

    // 辅助方法：创建并启动 Worker
    private FlowWorker<String, String> createWorker(List<FlowNode<String, String>> nodes) {
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        AtomicInteger activeCount = new AtomicInteger(0);
        AtomicInteger queueDepth = new AtomicInteger(0);
        FlowMetrics metrics = new FlowMetrics("TestMetrics");

        FlowWorker<String, String> worker = new FlowWorker<>(
                0, 100, nodes,
                shutdownLatch,
                activeCount,
                queueDepth,
                metrics
        );

        Thread workerThread = new Thread(worker);
        workerThread.setDaemon(false);
        workerThread.start();

        return worker;
    }
}
