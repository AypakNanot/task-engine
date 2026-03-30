package com.aypak.engine.flow.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FlowEvent 单元测试。
 * FlowEvent unit tests.
 */
@DisplayName("FlowEvent Tests")
class FlowEventTest {

    @Test
    @DisplayName("Should create event with auto-generated ID")
    void shouldCreateEventWithAutoId() {
        FlowEvent<String, String> event = new FlowEvent<>("shard-key-123", "payload");

        assertNotNull(event.getId());
        assertEquals("shard-key-123", event.getShardKey());
        assertEquals("payload", event.getPayload());
        assertTrue(event.getCreateTime() > 0);
        assertEquals(FlowEvent.ProcessingStatus.PENDING, event.getStatus());
    }

    @Test
    @DisplayName("Should create event with custom ID")
    void shouldCreateEventWithCustomId() {
        FlowEvent<String, String> event = new FlowEvent<>("custom-id-456", "shard-key", "payload");

        assertEquals("custom-id-456", event.getId());
        assertEquals("shard-key", event.getShardKey());
        assertEquals("payload", event.getPayload());
    }

    @Test
    @DisplayName("Should set and get timestamps")
    void shouldSetAndGetTimestamps() {
        FlowEvent<String, String> event = new FlowEvent<>("key", "data");

        long receiveTime = System.currentTimeMillis();
        event.setReceiveTime(receiveTime);

        long completeTime = System.currentTimeMillis();
        event.setCompleteTime(completeTime);

        assertEquals(receiveTime, event.getReceiveTime());
        assertEquals(completeTime, event.getCompleteTime());
        assertTrue(event.getLatency() >= 0);
    }

    @Test
    @DisplayName("Should return -1 for latency when not completed")
    void shouldReturnNegativeOneForLatencyWhenNotCompleted() {
        FlowEvent<String, String> event = new FlowEvent<>("key", "data");

        assertEquals(-1, event.getLatency());
    }

    @Test
    @DisplayName("Should set and get status")
    void shouldSetAndGetStatus() {
        FlowEvent<String, String> event = new FlowEvent<>("key", "data");

        event.setStatus(FlowEvent.ProcessingStatus.PROCESSING);
        assertEquals(FlowEvent.ProcessingStatus.PROCESSING, event.getStatus());

        event.setStatus(FlowEvent.ProcessingStatus.COMPLETED);
        assertEquals(FlowEvent.ProcessingStatus.COMPLETED, event.getStatus());
    }

    @Test
    @DisplayName("Should set and get error message")
    void shouldSetAndGetErrorMessage() {
        FlowEvent<String, String> event = new FlowEvent<>("key", "data");

        event.setErrorMessage("Test error");
        assertEquals("Test error", event.getErrorMessage());
    }

    @Test
    @DisplayName("Should have all processing status values")
    void shouldHaveAllProcessingStatusValues() {
        assertNotNull(FlowEvent.ProcessingStatus.PENDING);
        assertNotNull(FlowEvent.ProcessingStatus.PROCESSING);
        assertNotNull(FlowEvent.ProcessingStatus.COMPLETED);
        assertNotNull(FlowEvent.ProcessingStatus.PERSISTED);
        assertNotNull(FlowEvent.ProcessingStatus.DROPPED);
        assertNotNull(FlowEvent.ProcessingStatus.FAILED);
    }
}
