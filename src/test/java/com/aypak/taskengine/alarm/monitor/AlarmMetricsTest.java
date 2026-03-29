package com.aypak.taskengine.alarm.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AlarmMetrics 单元测试
 */
class AlarmMetricsTest {

    private AlarmMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new AlarmMetrics();
    }

    @Test
    void testRecordIncoming() {
        // 给定
        long initialCount = metrics.getIncomingCount();

        // 当
        metrics.recordIncoming();
        metrics.recordIncoming();
        metrics.recordIncoming();

        // 则
        assertEquals(initialCount + 3, metrics.getIncomingCount());
    }

    @Test
    void testRecordSuccessAndFailure() {
        // 当
        metrics.recordSuccess();
        metrics.recordSuccess();
        metrics.recordFailure();

        // 则
        assertEquals(2, metrics.getSuccessCount());
        assertEquals(1, metrics.getFailureCount());
    }

    @Test
    void testRecordDropped() {
        // 当
        metrics.recordDropped();
        metrics.recordDropped();

        // 则
        assertEquals(2, metrics.getDroppedCount());
    }

    @Test
    void testUpdateProcessingRT() {
        // 当
        metrics.updateProcessingRT(100);
        metrics.updateProcessingRT(200);
        metrics.updateProcessingRT(300);

        // 则
        long avgRT = metrics.getAvgProcessingRT();
        assertTrue(avgRT > 0, "Average RT should be positive");
    }

    @Test
    void testUpdateDBLatency() {
        // 当
        metrics.updateDBLatency(50);
        metrics.updateDBLatency(100);

        // 则
        long avgLatency = metrics.getAvgDBLatency();
        assertTrue(avgLatency > 0, "Average DB latency should be positive");
    }

    @Test
    void testSetReceiverQueueDepth() {
        // 当
        metrics.setReceiverQueueDepth(100);

        // 则
        assertEquals(100, metrics.getReceiverQueueDepth());
    }

    @Test
    void testSetWorkerQueueDepths() {
        // 当
        int[] depths = {10, 20, 30, 40};
        metrics.setWorkerQueueDepths(depths);

        // 则
        int[] result = metrics.getWorkerQueueDepths();
        assertArrayEquals(depths, result);
        assertEquals(100, metrics.getTotalWorkerQueueDepth());
    }

    @Test
    void testGetSuccessRate() {
        // 给定
        metrics.recordSuccess();
        metrics.recordSuccess();
        metrics.recordSuccess();
        metrics.recordFailure();

        // 则
        double rate = metrics.getSuccessRate();
        assertEquals(75.0, rate, 0.01);
    }

    @Test
    void testGetFailureRate() {
        // 给定
        metrics.recordSuccess();
        metrics.recordFailure();
        metrics.recordFailure();

        // 则
        double rate = metrics.getFailureRate();
        assertEquals(66.67, rate, 0.01);
    }

    @Test
    void testGetSnapshot() {
        // 给定
        metrics.recordIncoming();
        metrics.recordSuccess();
        metrics.setReceiverQueueDepth(50);

        // 当
        AlarmMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();

        // 则
        assertNotNull(snapshot);
        assertEquals(1, snapshot.incomingCount);
        assertEquals(1, snapshot.successCount);
        assertEquals(50, snapshot.receiverQueueDepth);
    }

    @Test
    void testReset() {
        // 给定
        metrics.recordIncoming();
        metrics.recordSuccess();
        metrics.recordFailure();
        metrics.recordDropped();
        metrics.updateProcessingRT(100);
        metrics.setReceiverQueueDepth(50);

        // 当
        metrics.reset();

        // 则
        assertEquals(0, metrics.getIncomingCount());
        assertEquals(0, metrics.getSuccessCount());
        assertEquals(0, metrics.getFailureCount());
        assertEquals(0, metrics.getDroppedCount());
        assertEquals(0, metrics.getReceiverQueueDepth());
    }

    @Test
    void testQPSCalculation() throws InterruptedException {
        // 给定
        metrics.recordIncoming();
        metrics.recordIncoming();

        // 等待超过 1 秒，触发 QPS 计算
        Thread.sleep(1100);

        // 当
        long qps = metrics.getQPS();

        // 则
        assertTrue(qps >= 0, "QPS should be non-negative");
    }
}
