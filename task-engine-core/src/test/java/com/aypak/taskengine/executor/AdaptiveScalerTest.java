package com.aypak.taskengine.executor;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.monitor.TaskMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdaptiveScaler 单元测试。
 * Unit tests for AdaptiveScaler.
 */
@DisplayName("AdaptiveScaler Tests")
class AdaptiveScalerTest {

    @Mock
    private TaskEngineImpl taskEngine;

    @Mock
    private TaskExecutor executor;

    @Mock
    private TaskMetrics metrics;

    private TaskEngineProperties properties;
    private AdaptiveScaler scaler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        properties = new TaskEngineProperties();
        properties.setGlobalMaxThreads(200);
        properties.setScaleFactor(2);
        properties.setScaleUpThreshold(80);

        scaler = new AdaptiveScaler(taskEngine, properties);
    }

    @Test
    @DisplayName("Should create scaler with correct configuration")
    void shouldCreateScalerWithCorrectConfiguration() {
        assertNotNull(scaler);
        assertEquals(0.3, scaler.getQpsPredictor().getAlpha());
        assertEquals(0.2, scaler.getQueuePredictor().getAlpha());
        assertEquals(0, scaler.getTotalThreads());
    }

    @Test
    @DisplayName("Should start and stop scaler")
    void shouldStartAndStopScaler() {
        assertDoesNotThrow(() -> {
            scaler.start(1000);
            Thread.sleep(100);
            scaler.stop();
        });
    }

    @Test
    @DisplayName("Should detect rapid QPS uptrend condition")
    void shouldDetectRapidQpsUptrendCondition() {
        // Simulate rapid uptrend by updating predictor directly
        for (int i = 0; i < 5; i++) {
            scaler.getQpsPredictor().update((i + 1) * 100);
        }

        // Verify uptrend is detected
        assertTrue(scaler.getQpsPredictor().isRapidUptrend(3),
            "Should detect rapid uptrend after consecutive increases");
        assertEquals(1, scaler.getQpsPredictor().getTrendDirection(),
            "Trend direction should be uptrend");
    }

    @Test
    @DisplayName("Should detect traffic spike")
    void shouldDetectTrafficSpike() {
        // Setup: high QPS growth rate
        for (int i = 0; i < 3; i++) {
            scaler.getQpsPredictor().update((i + 1) * 200);
        }
        scaler.getQueuePredictor().update(100);
        scaler.getQueuePredictor().update(150);
        scaler.getQueuePredictor().update(200);

        // Verify traffic spike detection
        double growthRate = scaler.getQpsPredictor().getPredictedGrowthRate();
        int queueTrend = scaler.getQueuePredictor().getTrendDirection();

        assertTrue(growthRate > 50 || queueTrend == 1,
            "Should detect traffic spike or queue uptrend");
    }

    @Test
    @DisplayName("Should block scale up when global limit reached")
    void shouldBlockScaleUpWhenGlobalLimitReached() {
        // Setup: already at global limit
        properties.setGlobalMaxThreads(10);

        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(taskEngine.getStats("TestTask")).thenReturn(metrics);
        when(executor.getQueueSize()).thenReturn(500);
        when(executor.getQueueCapacity()).thenReturn(1000);
        when(executor.getActiveThreads()).thenReturn(10);
        when(executor.getMaxPoolSize()).thenReturn(10);
        when(metrics.getQps()).thenReturn(1000.0);
        when(metrics.getAvgResponseTime()).thenReturn(10L);
        when(metrics.getOriginalMaxPoolSize()).thenReturn(new java.util.concurrent.atomic.AtomicInteger(10));

        // Simulate conditions that would trigger scale up
        for (int i = 0; i < 5; i++) {
            scaler.getQpsPredictor().update((i + 1) * 100);
        }

        scaler.start(100);

        // Verify no scale up happened (already at limit)
        verify(executor, never()).setMaxPoolSize(anyInt());

        scaler.stop();
    }

    @Test
    @DisplayName("Should scale down on low utilization")
    void shouldScaleDownOnLowUtilization() {
        // Setup: low utilization scenario
        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(taskEngine.getStats("TestTask")).thenReturn(metrics);
        when(executor.getQueueSize()).thenReturn(5);
        when(executor.getQueueCapacity()).thenReturn(1000);
        when(executor.getActiveThreads()).thenReturn(2);
        when(executor.getMaxPoolSize()).thenReturn(20);
        when(metrics.getQps()).thenReturn(10.0);
        when(metrics.getAvgResponseTime()).thenReturn(10L);
        when(metrics.getOriginalMaxPoolSize()).thenReturn(new java.util.concurrent.atomic.AtomicInteger(10));

        // Simulate downtrend
        scaler.getQueuePredictor().update(100);
        scaler.getQueuePredictor().update(50);
        scaler.getQueuePredictor().update(20);
        scaler.getQueuePredictor().update(10);
        scaler.getQueuePredictor().update(5);

        scaler.start(100);

        // Verify scale down was considered
        verify(executor, timeout(1000).times(1)).setMaxPoolSize(anyInt());

        scaler.stop();
    }

    @Test
    @DisplayName("Should respect cooldown period")
    void shouldRespectCooldownPeriod() {
        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(taskEngine.getStats("TestTask")).thenReturn(metrics);
        when(executor.getQueueSize()).thenReturn(900);
        when(executor.getQueueCapacity()).thenReturn(1000);
        when(executor.getActiveThreads()).thenReturn(10);
        when(executor.getMaxPoolSize()).thenReturn(10);
        when(metrics.getQps()).thenReturn(100.0);
        when(metrics.getAvgResponseTime()).thenReturn(10L);
        when(metrics.getOriginalMaxPoolSize()).thenReturn(new java.util.concurrent.atomic.AtomicInteger(10));

        // Trigger rapid uptrend
        for (int i = 0; i < 5; i++) {
            scaler.getQpsPredictor().update((i + 1) * 100);
        }

        scaler.start(100);

        // Wait for first scale
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should only scale once due to cooldown
        int scaleCount = ((java.util.concurrent.atomic.AtomicInteger) getTotalThreads(scaler)).get();
        assertTrue(scaleCount <= 4, "Should respect cooldown and scale only once");

        scaler.stop();
    }

    @Test
    @DisplayName("Should handle null metrics gracefully")
    void shouldHandleNullMetricsGracefully() {
        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(taskEngine.getStats("TestTask")).thenReturn(null);

        scaler.start(100);

        // Should not throw exception
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        scaler.stop();
    }

    @Test
    @DisplayName("Should handle zero queue capacity gracefully")
    void shouldHandleZeroQueueCapacityGracefully() {
        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(taskEngine.getStats("TestTask")).thenReturn(metrics);
        when(executor.getQueueSize()).thenReturn(100);
        when(executor.getQueueCapacity()).thenReturn(0);

        scaler.start(100);

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Should not throw exception
        scaler.stop();
    }

    @Test
    @DisplayName("Should use double scaleFactor for traffic spike")
    void shouldUseDoubleScaleFactorForTrafficSpike() {
        int initialScaleFactor = properties.getScaleFactor();

        // Simulate traffic spike conditions
        for (int i = 0; i < 3; i++) {
            scaler.getQpsPredictor().update((i + 1) * 500);
        }
        scaler.getQueuePredictor().update(100);
        scaler.getQueuePredictor().update(200);
        scaler.getQueuePredictor().update(400);

        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(taskEngine.getStats("TestTask")).thenReturn(metrics);
        when(executor.getQueueSize()).thenReturn(800);
        when(executor.getQueueCapacity()).thenReturn(1000);
        when(executor.getActiveThreads()).thenReturn(10);
        when(executor.getMaxPoolSize()).thenReturn(10);
        when(metrics.getQps()).thenReturn(500.0);
        when(metrics.getAvgResponseTime()).thenReturn(10L);
        when(metrics.getOriginalMaxPoolSize()).thenReturn(new java.util.concurrent.atomic.AtomicInteger(10));

        scaler.start(100);

        // For traffic spike, should scale by 2x scaleFactor
        verify(executor, timeout(500).atLeastOnce()).setMaxPoolSize(anyInt());

        scaler.stop();
    }

    @Test
    @DisplayName("Should slowly scale down one thread at a time")
    void shouldSlowlyScaleDownOneThreadAtTime() {
        when(taskEngine.getExecutors()).thenReturn(Map.of("TestTask", executor));
        when(taskEngine.getStats("TestTask")).thenReturn(metrics);
        when(executor.getQueueSize()).thenReturn(5);
        when(executor.getQueueCapacity()).thenReturn(1000);
        when(executor.getActiveThreads()).thenReturn(3);
        when(executor.getMaxPoolSize()).thenReturn(20);
        when(metrics.getQps()).thenReturn(10.0);
        when(metrics.getAvgResponseTime()).thenReturn(10L);
        when(metrics.getOriginalMaxPoolSize()).thenReturn(new java.util.concurrent.atomic.AtomicInteger(10));

        // Simulate sustained downtrend
        for (int i = 0; i < 5; i++) {
            scaler.getQueuePredictor().update(100 - i * 15);
        }

        scaler.start(100);

        // Should scale down by only 1 thread at a time
        verify(executor, timeout(1000).atLeastOnce()).setMaxPoolSize(19);

        scaler.stop();
    }

    // Helper method to access private field via reflection
    private Object getTotalThreads(AdaptiveScaler scaler) {
        try {
            java.lang.reflect.Field field = AdaptiveScaler.class.getDeclaredField("totalThreads");
            field.setAccessible(true);
            return field.get(scaler);
        } catch (Exception e) {
            return null;
        }
    }
}
