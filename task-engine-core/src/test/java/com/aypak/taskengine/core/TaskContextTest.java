package com.aypak.taskengine.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskContext 单元测试。
 * TaskContext unit tests.
 */
@DisplayName("TaskContext Unit Tests")
class TaskContextTest {

    @Test
    @DisplayName("Should create context with default constructor capturing MDC")
    void shouldCreateContextWithDefaultConstructorCapturingMDC() {
        // 设置 MDC 上下文
        MDC.put("traceId", "test-trace-123");
        MDC.put("userId", "user-456");

        TaskContext context = new TaskContext();

        assertNotNull(context);
        assertEquals("test-trace-123", context.getTraceId());
        assertNotNull(context.getBaggage());
        assertEquals("test-trace-123", context.getBaggage().get("traceId"));
        assertEquals("user-456", context.getBaggage().get("userId"));
        assertTrue(context.getSubmitTime() > 0);

        // 清理
        MDC.clear();
    }

    @Test
    @DisplayName("Should create context with null MDC gracefully")
    void shouldCreateContextWithNullMDCGracefully() {
        // 确保 MDC 为空
        MDC.clear();

        TaskContext context = new TaskContext();

        assertNotNull(context);
        assertNull(context.getTraceId());
        assertNotNull(context.getBaggage());
        assertTrue(context.getBaggage().isEmpty());
        assertTrue(context.getSubmitTime() > 0);
    }

    @Test
    @DisplayName("Should create context with explicit values")
    void shouldCreateContextWithExplicitValues() {
        Map<String, String> baggage = new HashMap<>();
        baggage.put("traceId", "explicit-trace");
        baggage.put("userId", "explicit-user");

        long submitTime = System.currentTimeMillis();
        TaskContext context = new TaskContext("explicit-trace", baggage, submitTime);

        assertEquals("explicit-trace", context.getTraceId());
        assertNotNull(context.getBaggage());
        assertEquals("explicit-trace", context.getBaggage().get("traceId"));
        assertEquals("explicit-user", context.getBaggage().get("userId"));
        assertEquals(submitTime, context.getSubmitTime());
    }

    @Test
    @DisplayName("Should create defensive copy of baggage map")
    void shouldCreateDefensiveCopyOfBaggageMap() {
        Map<String, String> originalBaggage = new HashMap<>();
        originalBaggage.put("key1", "value1");

        TaskContext context = new TaskContext("trace", originalBaggage, System.currentTimeMillis());

        // 修改原始 map 不应影响 context
        originalBaggage.put("key2", "value2");

        assertEquals("value1", context.getBaggage().get("key1"));
        assertNull(context.getBaggage().get("key2"));
        assertEquals(1, context.getBaggage().size());
    }

    @Test
    @DisplayName("Should handle null baggage in constructor")
    void shouldHandleNullBaggageInConstructor() {
        TaskContext context = new TaskContext("trace", null, System.currentTimeMillis());

        assertNotNull(context.getBaggage());
        assertTrue(context.getBaggage().isEmpty());
    }

    @Test
    @DisplayName("Should propagate context to MDC")
    void shouldPropagateContextToMDC() {
        MDC.clear();

        Map<String, String> baggage = new HashMap<>();
        baggage.put("traceId", "propagate-trace");
        baggage.put("userId", "propagate-user");
        baggage.put("requestId", "req-789");

        TaskContext context = new TaskContext("propagate-trace", baggage, System.currentTimeMillis());
        context.propagate();

        // 验证 MDC 已被设置
        assertEquals("propagate-trace", MDC.get("traceId"));
        assertEquals("propagate-user", MDC.get("userId"));
        assertEquals("req-789", MDC.get("requestId"));

        // 清理
        MDC.clear();
    }

    @Test
    @DisplayName("Should propagate null traceId gracefully")
    void shouldPropagateNullTraceIdGracefully() {
        MDC.clear();
        MDC.put("existingKey", "existingValue");

        Map<String, String> baggage = new HashMap<>();
        baggage.put("userId", "user-123");

        TaskContext context = new TaskContext(null, baggage, System.currentTimeMillis());
        context.propagate();

        // traceId 不应被设置
        assertNull(MDC.get("traceId"));
        // 但 baggage 应该被设置
        assertEquals("user-123", MDC.get("userId"));

        // 清理
        MDC.clear();
    }

    @Test
    @DisplayName("Should clear MDC context")
    void shouldClearMDCContext() {
        MDC.put("traceId", "to-be-cleared");
        MDC.put("userId", "to-be-cleared");

        TaskContext context = new TaskContext();
        context.clear();

        // 验证 MDC 已被清空
        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("userId"));
    }

    @Test
    @DisplayName("Should calculate elapsed time correctly")
    void shouldCalculateElapsedTimeCorrectly() throws InterruptedException {
        TaskContext context = new TaskContext();

        Thread.sleep(50); // 等待 50ms

        long elapsed = context.getElapsedMs();

        assertTrue(elapsed >= 50, "Elapsed time should be at least 50ms, but was " + elapsed);
        assertTrue(elapsed < 1000, "Elapsed time should be less than 1000ms");
    }

    @Test
    @DisplayName("Should return zero elapsed time for immediate call")
    void shouldReturnZeroElapsedTimeForImmediateCall() {
        TaskContext context = new TaskContext();
        long elapsed = context.getElapsedMs();

        assertTrue(elapsed >= 0, "Elapsed time should be non-negative");
        assertTrue(elapsed < 100, "Elapsed time should be very small");
    }

    @Test
    @DisplayName("Should propagate and clear context in sequence")
    void shouldPropagateAndClearContextInSequence() {
        MDC.clear();

        Map<String, String> baggage = new HashMap<>();
        baggage.put("traceId", "test-trace");

        TaskContext context = new TaskContext("test-trace", baggage, System.currentTimeMillis());

        // 传播上下文
        context.propagate();
        assertEquals("test-trace", MDC.get("traceId"));

        // 清除上下文
        context.clear();
        assertNull(MDC.get("traceId"));
    }

    @Test
    @DisplayName("Should handle multiple propagate calls")
    void shouldHandleMultiplePropagateCalls() {
        MDC.clear();

        Map<String, String> baggage = new HashMap<>();
        baggage.put("traceId", "multi-trace");

        TaskContext context = new TaskContext("multi-trace", baggage, System.currentTimeMillis());

        // 多次传播
        context.propagate();
        String traceId1 = MDC.get("traceId");

        context.propagate();
        String traceId2 = MDC.get("traceId");

        assertEquals("multi-trace", traceId1);
        assertEquals("multi-trace", traceId2);
        assertEquals(1, context.getBaggage().size());

        // 清理
        MDC.clear();
    }
}
