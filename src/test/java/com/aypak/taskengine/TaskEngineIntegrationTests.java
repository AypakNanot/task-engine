package com.aypak.taskengine;

import com.aypak.taskengine.core.*;
import com.aypak.taskengine.executor.TaskEngine;
import com.aypak.taskengine.monitor.TaskStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskEngineIntegrationTests {

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setup() {
        // Register test task processors (check if already registered first)
        if (!taskEngine.getStats().containsKey("HighFreqAlert")) {
            taskEngine.register(TaskConfig.builder()
                    .taskName("HighFreqAlert")
                    .taskType(TaskType.HIGH_FREQ)
                    .priority(TaskPriority.HIGH)
                    .queueCapacity(1000)
                    .rejectionPolicy(RejectionPolicy.CALLER_RUNS)
                    .build(), new AlertProcessor());
        }

        if (!taskEngine.getStats().containsKey("BackgroundCleanup")) {
            taskEngine.register(TaskConfig.builder()
                    .taskName("BackgroundCleanup")
                    .taskType(TaskType.BACKGROUND)
                    .priority(TaskPriority.LOW)
                    .queueCapacity(100)
                    .rejectionPolicy(RejectionPolicy.DISCARD_OLDEST)
                    .build(), new CleanupProcessor());
        }

        if (!taskEngine.getStats().containsKey("InitTask")) {
            taskEngine.register(TaskConfig.builder()
                    .taskName("InitTask")
                    .taskType(TaskType.INIT)
                    .priority(TaskPriority.HIGH)
                    .build(), new InitProcessor());
        }
    }

    @Test
    void testTaskRegistration() {
        assertNotNull(taskEngine.getStats("HighFreqAlert"));
        assertNotNull(taskEngine.getStats("BackgroundCleanup"));
        assertNotNull(taskEngine.getStats("InitTask"));
    }

    @Test
    void testTaskExecution() {
        for (int i = 0; i < 10; i++) {
            taskEngine.execute("HighFreqAlert", "Alert-" + i);
        }

        // Wait for execution
        sleep(500);

        var stats = taskEngine.getStats("HighFreqAlert");
        assertNotNull(stats);
        assertTrue(stats.getSuccessCount().get() >= 10);
    }

    @Test
    void testMetricsEndpoint() {
        // Execute some tasks
        for (int i = 0; i < 5; i++) {
            taskEngine.execute("HighFreqAlert", "Test-" + i);
            taskEngine.execute("BackgroundCleanup", "Cleanup-" + i);
        }

        sleep(300);

        // Call REST endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity("/monitor/task/status", Map.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("HighFreqAlert"));
    }

    @Test
    void testDynamicConfigUpdate() {
        // Update pool size (must set core before max)
        DynamicConfig config = DynamicConfig.builder()
                .corePoolSize(10)
                .maxPoolSize(20)
                .build();

        taskEngine.updateConfig("HighFreqAlert", config);

        var stats = taskEngine.getStats("HighFreqAlert");
        assertEquals(20, stats.getCurrentMaxPoolSize().get());
    }

    @Test
    void testMetricsReset() {
        // Execute tasks
        for (int i = 0; i < 5; i++) {
            taskEngine.execute("HighFreqAlert", "Reset-" + i);
        }

        sleep(300);

        // Reset metrics
        taskEngine.resetMetrics("HighFreqAlert");

        var stats = taskEngine.getStats("HighFreqAlert");
        assertEquals(0, stats.getSuccessCount().get());
    }

    // Test Processors
    static class AlertProcessor implements ITaskProcessor<String> {
        @Override
        public String getTaskName() { return "HighFreqAlert"; }

        @Override
        public TaskType getTaskType() { return TaskType.HIGH_FREQ; }

        @Override
        public TaskPriority getPriority() { return TaskPriority.HIGH; }

        @Override
        public void process(String context) {
            // Simulate work
            sleep(10);
        }
    }

    static class CleanupProcessor implements ITaskProcessor<String> {
        @Override
        public String getTaskName() { return "BackgroundCleanup"; }

        @Override
        public TaskType getTaskType() { return TaskType.BACKGROUND; }

        @Override
        public TaskPriority getPriority() { return TaskPriority.LOW; }

        @Override
        public void process(String context) {
            sleep(50);
        }
    }

    static class InitProcessor implements ITaskProcessor<String> {
        @Override
        public String getTaskName() { return "InitTask"; }

        @Override
        public TaskType getTaskType() { return TaskType.INIT; }

        @Override
        public TaskPriority getPriority() { return TaskPriority.HIGH; }

        @Override
        public void process(String context) {
            sleep(100);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}