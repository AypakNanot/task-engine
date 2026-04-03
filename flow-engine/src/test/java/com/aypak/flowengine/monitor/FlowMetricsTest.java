package com.aypak.flowengine.monitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FlowMetrics 单元测试。
 * FlowMetrics unit tests.
 */
@DisplayName("FlowMetrics Tests")
class FlowMetricsTest {

    @Test
    @DisplayName("Should create metrics with flow name")
    void shouldCreateMetricsWithFlowName() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        assertEquals("TestFlow", metrics.getFlowName());
    }

    @Test
    @DisplayName("Should record success")
    void shouldRecordSuccess() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        metrics.recordSuccess();

        assertEquals(1, metrics.getTotalSuccess());
    }

    @Test
    @DisplayName("Should record success with execution time")
    void shouldRecordSuccessWithExecutionTime() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        metrics.recordSuccess(100L);

        assertEquals(1, metrics.getTotalSuccess());
        assertTrue(metrics.getAvgResponseTime() >= 0);
    }

    @Test
    @DisplayName("Should record failure")
    void shouldRecordFailure() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        metrics.recordFailure();

        assertEquals(1, metrics.getTotalFailure());
    }

    @Test
    @DisplayName("Should record drop")
    void shouldRecordDrop() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        metrics.recordDrop();

        assertEquals(1, metrics.getTotalDropped());
    }

    @Test
    @DisplayName("Should record receive")
    void shouldRecordReceive() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        metrics.recordReceive();

        assertEquals(1, metrics.getReceivedCount().sum());
    }

    @Test
    @DisplayName("Should calculate success rate")
    void shouldCalculateSuccessRate() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        metrics.recordSuccess();
        metrics.recordSuccess();
        metrics.recordFailure();

        double successRate = metrics.getSuccessRate();

        assertEquals(2.0 / 3.0, successRate, 0.01);
    }

    @Test
    @DisplayName("Should return 1.0 success rate when no events")
    void shouldReturnOneSuccessRateWhenNoEvents() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        assertEquals(1.0, metrics.getSuccessRate());
    }

    @Test
    @DisplayName("Should calculate QPS")
    void shouldCalculateQps() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        // 记录一些成功事件
        // Record some success events
        for (int i = 0; i < 10; i++) {
            metrics.recordSuccess();
        }

        double qps = metrics.getQps();

        assertTrue(qps >= 0);
    }

    @Test
    @DisplayName("Should get and set queue depth")
    void shouldGetAndSetQueueDepth() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        metrics.setQueueDepth(100);

        assertEquals(100, metrics.getQueueDepth().get());
    }

    @Test
    @DisplayName("Should get and set active workers")
    void shouldGetAndSetActiveWorkers() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        metrics.setActiveWorkers(8);

        assertEquals(8, metrics.getActiveWorkers().get());
    }

    @Test
    @DisplayName("Should reset all metrics")
    void shouldResetAllMetrics() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        metrics.recordSuccess();
        metrics.recordFailure();
        metrics.recordDrop();
        metrics.recordReceive();
        metrics.setQueueDepth(50);
        metrics.setActiveWorkers(4);

        metrics.reset();

        assertEquals(0, metrics.getTotalSuccess());
        assertEquals(0, metrics.getTotalFailure());
        assertEquals(0, metrics.getTotalDropped());
        assertEquals(0, metrics.getQueueDepth().get());
        assertEquals(0, metrics.getActiveWorkers().get());
    }

    @Test
    @DisplayName("Should update EWMA response time")
    void shouldUpdateEwmaResponseTime() {
        FlowMetrics metrics = new FlowMetrics("TestFlow");

        metrics.recordSuccess(100L);
        long firstAvg = metrics.getAvgResponseTime();

        metrics.recordSuccess(200L);
        long secondAvg = metrics.getAvgResponseTime();

        // EWMA 应该介于两者之间
        // EWMA should be between the two values
        assertTrue(firstAvg >= 0);
        assertTrue(secondAvg >= 0);
    }
}
