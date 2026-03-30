package com.aypak.engine.flow.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FlowContext 单元测试。
 * FlowContext unit tests.
 */
@DisplayName("FlowContext Tests")
class FlowContextTest {

    @Test
    @DisplayName("Should create empty context")
    void shouldCreateEmptyContext() {
        FlowContext context = new FlowContext();

        assertTrue(context.shouldContinue());
        assertFalse(context.isDropped());
        assertFalse(context.isPersisted());
        assertFalse(context.isNotified());
    }

    @Test
    @DisplayName("Should set and get values")
    void shouldSetAndGetValues() {
        FlowContext context = new FlowContext();

        context.set("key1", "value1");
        context.set("key2", 123);

        assertEquals("value1", context.get("key1"));
        assertEquals(Integer.valueOf(123), context.get("key2"));
    }

    @Test
    @DisplayName("Should get default value when key not found")
    void shouldGetDefaultValueWhenKeyNotFound() {
        FlowContext context = new FlowContext();

        assertEquals("default", context.get("nonexistent", "default"));
        assertEquals(42, context.get("missing", 42));
    }

    @Test
    @DisplayName("Should check if contains key")
    void shouldCheckIfContainsKey() {
        FlowContext context = new FlowContext();

        context.set("existing", "value");

        assertTrue(context.containsKey("existing"));
        assertFalse(context.containsKey("nonexistent"));
    }

    @Test
    @DisplayName("Should remove value")
    void shouldRemoveValue() {
        FlowContext context = new FlowContext();

        context.set("key", "value");
        context.remove("key");

        assertNull(context.get("key"));
    }

    @Test
    @DisplayName("Should get all data")
    void shouldGetAllData() {
        FlowContext context = new FlowContext();

        context.set("key1", "value1");
        context.set("key2", "value2");

        assertEquals(2, context.getAll().size());
    }

    @Test
    @DisplayName("Should stop processing")
    void shouldStopProcessing() {
        FlowContext context = new FlowContext();

        context.stop();

        assertFalse(context.shouldContinue());
    }

    @Test
    @DisplayName("Should mark as dropped")
    void shouldMarkAsDropped() {
        FlowContext context = new FlowContext();

        context.markDropped();

        assertTrue(context.isDropped());
        assertFalse(context.shouldContinue());
    }

    @Test
    @DisplayName("Should mark as persisted")
    void shouldMarkAsPersisted() {
        FlowContext context = new FlowContext();

        context.markPersisted();

        assertTrue(context.isPersisted());
    }

    @Test
    @DisplayName("Should mark as notified")
    void shouldMarkAsNotified() {
        FlowContext context = new FlowContext();

        context.markNotified();

        assertTrue(context.isNotified());
    }

    @Test
    @DisplayName("Should record and get node latency")
    void shouldRecordAndGetNodeLatency() {
        FlowContext context = new FlowContext();

        context.recordNodeLatency("Node1", 100L);
        context.recordNodeLatency("Node2", 200L);

        assertEquals(100L, context.getNodeLatency("Node1"));
        assertEquals(200L, context.getNodeLatency("Node2"));
        assertNull(context.getNodeLatency("Node3"));
    }

    @Test
    @DisplayName("Should get all node latencies")
    void shouldGetAllNodeLatencies() {
        FlowContext context = new FlowContext();

        context.recordNodeLatency("Node1", 100L);
        context.recordNodeLatency("Node2", 200L);

        assertEquals(2, context.getAllNodeLatencies().size());
    }

    @Test
    @DisplayName("Should clear context")
    void shouldClearContext() {
        FlowContext context = new FlowContext();

        context.set("key", "value");
        context.recordNodeLatency("Node", 100L);
        context.stop();
        context.markDropped();

        context.clear();

        assertTrue(context.shouldContinue());
        assertFalse(context.isDropped());
        assertEquals(0, context.getAll().size());
        assertEquals(0, context.getAllNodeLatencies().size());
    }
}
