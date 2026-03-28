package com.aypak.taskengine;

import com.aypak.taskengine.core.*;
import com.aypak.taskengine.executor.TaskEngine;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared test utilities for Task Engine tests.
 * Reduces code duplication across stress tests.
 */
public final class TaskEngineTestUtils {

    private TaskEngineTestUtils() {
        // Utility class - no instantiation
    }

    // Task name constants
    public static final String TASK_STRESS_TEST = "StressTest";
    public static final String TASK_LONG_STRESS = "LongStressTest";
    public static final String TASK_HIGH_FREQ = "HighFreqAlert";
    public static final String TASK_BACKGROUND = "BackgroundCleanup";
    public static final String TASK_INIT = "InitTask";
    public static final String TASK_CRON = "CronCollector";

    /**
     * Safe sleep utility that handles InterruptedException properly.
     */
    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Register a task safely, ignoring if already registered.
     */
    public static void registerTaskSafely(TaskEngine taskEngine, TaskConfig config, ITaskProcessor<?> processor) {
        try {
            taskEngine.register(config, processor);
        } catch (IllegalArgumentException e) {
            // Task already registered - safe to ignore in tests
        }
    }

    /**
     * Print monitoring results from REST API.
     */
    @SuppressWarnings("unchecked")
    public static void printMonitoringResults(org.springframework.boot.test.web.client.TestRestTemplate restTemplate) {
        System.out.println("\n--- REST API Monitoring Results ---");
        org.springframework.http.ResponseEntity<Map> response = restTemplate.getForEntity("/monitor/task/status", Map.class);
        if (response.getBody() != null) {
            Map<String, Object> stats = response.getBody();
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                Map<String, Object> taskStats = (Map<String, Object>) entry.getValue();
                System.out.println("\nTask: " + entry.getKey());
                System.out.println("----------------------------------------");
                System.out.println("  Current QPS: " + taskStats.get("currentQps"));
                System.out.println("  Avg Response Time: " + taskStats.get("avgResponseTime") + " ms");
                System.out.println("  Success Count: " + taskStats.get("successCount"));
                System.out.println("  Failure Count: " + taskStats.get("failureCount"));
                System.out.println("  Queue Depth: " + taskStats.get("queueDepth") + " / " + taskStats.get("queueCapacity"));
                System.out.println("  Active Threads: " + taskStats.get("activeThreads"));
                System.out.println("  Peak Threads: " + taskStats.get("peakThreads"));
                System.out.println("  Max Pool Size: " + taskStats.get("currentMaxPoolSize"));
            }
        }
        org.springframework.http.ResponseEntity<Map> health = restTemplate.getForEntity("/actuator/health", Map.class);
        System.out.println("\nHealth Status: " + (health.getBody() != null ? health.getBody().get("status") : "UNKNOWN"));
    }

    /**
     * Print test banner with parameters.
     */
    public static void printTestBanner(String testName, Map<String, Object> params) {
        System.out.println("\n================================================================================");
        System.out.printf("           TASK ENGINE - %s%n", testName.toUpperCase());
        System.out.println("================================================================================");
        params.forEach((key, value) -> System.out.println(key + ": " + value));
        System.out.println("================================================================================\n");
    }

    /**
     * Reusable test payload with configurable processing time.
     */
    public record TestPayload(int id, String type, int processingTimeMs) {}

    /**
     * Reusable test processor with configurable name, type, and processing delay.
     */
    public static class SimpleTestProcessor implements ITaskProcessor<TestPayload> {
        private final String taskName;
        private final TaskType taskType;
        private final TaskPriority priority;
        private final AtomicInteger counter = new AtomicInteger(0);

        public SimpleTestProcessor(String taskName, TaskType taskType, TaskPriority priority) {
            this.taskName = taskName;
            this.taskType = taskType;
            this.priority = priority;
        }

        @Override
        public String getTaskName() { return taskName; }

        @Override
        public TaskType getTaskType() { return taskType; }

        @Override
        public TaskPriority getPriority() { return priority; }

        @Override
        public void process(TestPayload payload) {
            if (payload.processingTimeMs() > 0) {
                sleep(payload.processingTimeMs());
            }
            counter.incrementAndGet();
        }

        @Override
        public void onFailure(TestPayload payload, Throwable error) {
            System.err.println("Task failed: " + payload.type() + " - " + error.getMessage());
        }

        public int getCount() {
            return counter.get();
        }
    }
}