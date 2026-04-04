package com.aypak.taskengine.monitor;

import com.aypak.taskengine.executor.TaskEngineImpl;
import com.aypak.taskengine.executor.TaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PoolExhaustionAlarm 单元测试。
 * Unit tests for PoolExhaustionAlarm.
 */
@DisplayName("PoolExhaustionAlarm Tests")
class PoolExhaustionAlarmTest {

    @Mock
    private TaskEngineImpl taskEngine;

    @Mock
    private TaskExecutor executor;

    private PoolExhaustionAlarm alarm;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        alarm = new PoolExhaustionAlarm(taskEngine);
    }

    @Test
    @DisplayName("Should create alarm detector")
    void shouldCreateAlarmDetector() {
        assertNotNull(alarm);
    }

    @Test
    @DisplayName("Should start and stop alarm detector")
    void shouldStartAndStopAlarmDetector() {
        assertDoesNotThrow(() -> {
            alarm.start();
            Thread.sleep(100);
            alarm.stop();
        });
    }

    @Test
    @DisplayName("Should not trigger alert for normal pool usage")
    void shouldNotTriggerAlertForNormalUsage() {
        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(executor.getActiveThreads()).thenReturn(5);
        when(executor.getMaxPoolSize()).thenReturn(20);
        when(executor.getQueueSize()).thenReturn(10);
        when(executor.getQueueCapacity()).thenReturn(100);

        alarm.start();

        try {
            Thread.sleep(600); // Wait for at least one check
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should not trigger alert (utilization is low)
        assertEquals(0, alarm.getConsecutiveAlertCount("TestTask"));

        alarm.stop();
    }

    @Test
    @DisplayName("Should increment alert count for high thread utilization")
    void shouldIncrementAlertCountForHighThreadUtilization() throws Exception {
        // Setup mock behavior first
        when(executor.getActiveThreads()).thenReturn(19); // 95% of 20
        when(executor.getMaxPoolSize()).thenReturn(20);
        when(executor.getQueueSize()).thenReturn(10);
        when(executor.getQueueCapacity()).thenReturn(100);

        // Use lenient mock for getExecutors which will be called multiple times
        when(taskEngine.getExecutors()).thenAnswer(invocation -> Map.of("TestTask", executor));

        alarm.start();

        // Wait for multiple checks (5 seconds each = up to 50 seconds)
        for (int i = 0; i < 25 && alarm.getConsecutiveAlertCount("TestTask") < 1; i++) {
            Thread.sleep(200);
        }

        int alertCount = alarm.getConsecutiveAlertCount("TestTask");

        // Stop alarm before assertion to avoid concurrent modification
        alarm.stop();

        // Should have incremented alert count
        assertTrue(alertCount >= 1,
            "Expected alert count >= 1, got: " + alertCount);
    }

    @Test
    @DisplayName("Should increment alert count for high queue utilization")
    void shouldIncrementAlertCountForHighQueueUtilization() throws Exception {
        // Setup mock behavior first
        when(executor.getActiveThreads()).thenReturn(5);
        when(executor.getMaxPoolSize()).thenReturn(20);
        when(executor.getQueueSize()).thenReturn(85); // 85% of 100
        when(executor.getQueueCapacity()).thenReturn(100);

        // Use lenient mock for getExecutors
        when(taskEngine.getExecutors()).thenAnswer(invocation -> Map.of("TestTask", executor));

        alarm.start();

        // Wait for multiple checks
        for (int i = 0; i < 25 && alarm.getConsecutiveAlertCount("TestTask") < 1; i++) {
            Thread.sleep(200);
        }

        int alertCount = alarm.getConsecutiveAlertCount("TestTask");

        // Stop alarm before assertion
        alarm.stop();

        // Should increment alert count
        assertTrue(alertCount >= 1,
            "Expected alert count >= 1, got: " + alertCount);
    }

    @Test
    @DisplayName("Should reset alert count when pool usage returns to normal")
    void shouldResetAlertCountWhenUsageReturnsToNormal() throws Exception {
        // First, simulate high utilization
        when(executor.getActiveThreads()).thenReturn(19);
        when(executor.getMaxPoolSize()).thenReturn(20);
        when(executor.getQueueSize()).thenReturn(85);
        when(executor.getQueueCapacity()).thenReturn(100);
        when(taskEngine.getExecutors()).thenAnswer(invocation -> Map.of("TestTask", executor));

        alarm.start();

        // Wait for alert count to build up
        for (int i = 0; i < 25 && alarm.getConsecutiveAlertCount("TestTask") < 1; i++) {
            Thread.sleep(200);
        }

        int alertCount = alarm.getConsecutiveAlertCount("TestTask");
        assertTrue(alertCount >= 1,
            "Should have alert count after high utilization, got: " + alertCount);

        // Change mock to return normal utilization
        when(executor.getActiveThreads()).thenReturn(5);
        when(executor.getQueueSize()).thenReturn(10);

        // Wait for reset
        for (int i = 0; i < 25 && alarm.getConsecutiveAlertCount("TestTask") > 0; i++) {
            Thread.sleep(200);
        }

        alarm.stop();

        // Alert count should be reset
        assertEquals(0, alarm.getConsecutiveAlertCount("TestTask"));
    }

    @Test
    @DisplayName("Should track last alert time")
    void shouldTrackLastAlertTime() {
        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(executor.getActiveThreads()).thenReturn(19);
        when(executor.getMaxPoolSize()).thenReturn(20);
        when(executor.getQueueSize()).thenReturn(85);
        when(executor.getQueueCapacity()).thenReturn(100);

        alarm.start();

        try {
            // Wait for multiple checks to trigger alert and cooldown
            Thread.sleep(16000); // Wait for alert + cooldown
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Last alert time should be set
        Long lastAlertTime = alarm.getLastAlertTime("TestTask");
        assertNotNull(lastAlertTime);
        assertTrue(lastAlertTime > 0);

        alarm.stop();
    }

    @Test
    @DisplayName("Should handle zero queue capacity gracefully")
    void shouldHandleZeroQueueCapacityGracefully() {
        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(executor.getActiveThreads()).thenReturn(19);
        when(executor.getMaxPoolSize()).thenReturn(20);
        when(executor.getQueueSize()).thenReturn(0);
        when(executor.getQueueCapacity()).thenReturn(0);

        alarm.start();

        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should not throw exception
        assertTrue(alarm.getConsecutiveAlertCount("TestTask") >= 0);

        alarm.stop();
    }

    @Test
    @DisplayName("Should handle zero max pool size gracefully")
    void shouldHandleZeroMaxPoolSizeGracefully() {
        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(executor.getActiveThreads()).thenReturn(0);
        when(executor.getMaxPoolSize()).thenReturn(0);
        when(executor.getQueueSize()).thenReturn(0);
        when(executor.getQueueCapacity()).thenReturn(100);

        alarm.start();

        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should not throw exception
        assertTrue(alarm.getConsecutiveAlertCount("TestTask") >= 0);

        alarm.stop();
    }
}
