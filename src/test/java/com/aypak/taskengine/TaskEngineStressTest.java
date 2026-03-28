package com.aypak.taskengine;

import com.aypak.taskengine.core.*;
import com.aypak.taskengine.executor.TaskEngine;
import com.aypak.taskengine.monitor.TaskStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
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

/**
 * Stress test for Task Engine.
 * Simulates high-frequency task submission and monitors performance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskEngineStressTest {

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private TestRestTemplate restTemplate;

    private static final int TOTAL_TASKS = 5000;
    private static final int CONCURRENT_THREADS = 50;

    @BeforeEach
    void setup() {
        try {
            taskEngine.register(TaskConfig.builder()
                    .taskName("StressTest")
                    .taskType(TaskType.HIGH_FREQ)
                    .priority(TaskPriority.HIGH)
                    .corePoolSize(16)
                    .maxPoolSize(32)
                    .queueCapacity(10000)
                    .queueAlertThreshold(80)
                    .rejectionPolicy(RejectionPolicy.CALLER_RUNS)
                    .build(), new StressTaskProcessor());
        } catch (IllegalArgumentException e) {
            // Task already registered, ignore
        }
    }

    @Test
    void stressTestHighFrequencyTasks() throws InterruptedException {
        System.out.println("\n========== STRESS TEST START ==========");
        System.out.println("Submitting " + TOTAL_TASKS + " tasks with " + CONCURRENT_THREADS + " concurrent threads");

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_TASKS);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        // Set traceId for context propagation test
        MDC.put("traceId", "STRESS-TEST-" + System.currentTimeMillis());

        for (int i = 0; i < TOTAL_TASKS; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    taskEngine.execute("StressTest", new StressPayload(taskId, "data-" + taskId));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("Task " + taskId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all tasks to be submitted
        latch.await();
        long submitTime = System.currentTimeMillis() - startTime;

        System.out.println("All tasks submitted in " + submitTime + "ms");
        System.out.println("Success: " + successCount.get() + ", Failure: " + failureCount.get());

        // Wait for processing to complete
        Thread.sleep(3000);

        // Print monitoring results
        printMonitoringResults();

        // Verify results
        var stats = taskEngine.getStats("StressTest");
        assertNotNull(stats);

        System.out.println("\n========== STRESS TEST RESULTS ==========");
        System.out.println("Total submitted: " + TOTAL_TASKS);
        System.out.println("Submit time: " + submitTime + "ms");
        System.out.println("Submit rate: " + String.format("%.0f", TOTAL_TASKS * 1000.0 / submitTime) + " tasks/sec");
        System.out.println("Success count: " + stats.getSuccessCount().sum());
        System.out.println("Failure count: " + stats.getFailureCount().sum());
        System.out.println("Avg response time: " + stats.getAvgResponseTime() + "ms");
        System.out.println("==========================================\n");

        MDC.clear();
        executor.shutdown();
    }

    @Test
    void testQueuePressureAndScaling() throws InterruptedException {
        System.out.println("\n========== QUEUE PRESSURE TEST ==========");

        // Submit tasks faster than they can be processed to trigger queue buildup
        ExecutorService executor = Executors.newFixedThreadPool(100);

        for (int batch = 0; batch < 5; batch++) {
            System.out.println("\n--- Batch " + (batch + 1) + " ---");
            final int currentBatch = batch; // Make effectively final
            long batchStart = System.currentTimeMillis();

            // Submit 1000 tasks rapidly
            for (int i = 0; i < 1000; i++) {
                final int taskId = currentBatch * 1000 + i;
                executor.submit(() -> {
                    try {
                        taskEngine.execute("StressTest", new StressPayload(taskId, "batch-" + currentBatch));
                    } catch (Exception e) {
                        // Ignore
                    }
                });
            }

            Thread.sleep(500); // Brief pause between batches

            // Check queue depth
            var stats = taskEngine.getStats("StressTest");
            System.out.println("Queue depth: " + stats.getQueueDepth().get());
            System.out.println("Active threads: " + stats.getActiveThreads().get());
            System.out.println("Current max pool: " + stats.getCurrentMaxPoolSize().get());
        }

        Thread.sleep(3000); // Wait for processing
        printMonitoringResults();

        executor.shutdown();
    }

    private void printMonitoringResults() {
        System.out.println("\n========== MONITORING RESULTS ==========");

        // Get stats via REST API
        ResponseEntity<Map> response = restTemplate.getForEntity("/monitor/task/status", Map.class);

        if (response.getBody() != null) {
            Map<String, Object> stats = response.getBody();

            System.out.println("\n┌─────────────────────────────────────────────────────────────────────────┐");
            System.out.println("│                        TASK ENGINE MONITOR                              │");
            System.out.println("├─────────────────────────────────────────────────────────────────────────┤");

            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> taskStats = (Map<String, Object>) entry.getValue();

                System.out.printf("│ Task: %-20s Type: %-10s                           │%n",
                        entry.getKey(), taskStats.get("taskType"));
                System.out.println("├─────────────────────────────────────────────────────────────────────────┤");

                double qps = ((Number) taskStats.getOrDefault("currentQps", 0)).doubleValue();
                long avgRt = ((Number) taskStats.getOrDefault("avgResponseTime", 0)).longValue();
                long success = ((Number) taskStats.getOrDefault("successCount", 0)).longValue();
                long failure = ((Number) taskStats.getOrDefault("failureCount", 0)).longValue();
                int queueDepth = ((Number) taskStats.getOrDefault("queueDepth", 0)).intValue();
                int activeThreads = ((Number) taskStats.getOrDefault("activeThreads", 0)).intValue();
                int peakThreads = ((Number) taskStats.getOrDefault("peakThreads", 0)).intValue();
                int maxPool = ((Number) taskStats.getOrDefault("currentMaxPoolSize", 0)).intValue();

                System.out.printf("│   QPS: %-10.1f  Avg RT: %-6dms  Success: %-8d Failure: %-6d │%n",
                        qps, avgRt, success, failure);
                System.out.printf("│   Queue: %-5d  Active: %-4d  Peak: %-4d  MaxPool: %-4d           │%n",
                        queueDepth, activeThreads, peakThreads, maxPool);
                System.out.println("├─────────────────────────────────────────────────────────────────────────┤");
            }

            System.out.println("└─────────────────────────────────────────────────────────────────────────┘");
        }

        // Health check
        ResponseEntity<Map> healthResponse = restTemplate.getForEntity("/actuator/health", Map.class);
        System.out.println("\nHealth Status: " + (healthResponse.getBody() != null ?
                healthResponse.getBody().get("status") : "UNKNOWN"));
    }

    // Stress test payload
    record StressPayload(int id, String data) {}

    // Stress test processor - simulates varying workloads
    static class StressTaskProcessor implements ITaskProcessor<StressPayload> {
        private final AtomicLong counter = new AtomicLong(0);

        @Override
        public String getTaskName() { return "StressTest"; }

        @Override
        public TaskType getTaskType() { return TaskType.HIGH_FREQ; }

        @Override
        public TaskPriority getPriority() { return TaskPriority.HIGH; }

        @Override
        public void process(StressPayload payload) {
            long count = counter.incrementAndGet();

            // Simulate work with varying duration
            try {
                // Most tasks are fast (5-20ms)
                // Some tasks are slower (50-100ms) to simulate real-world variance
                long sleepTime = count % 20 == 0 ? 50 + (count % 50) : 5 + (count % 15);
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void onSuccess(StressPayload context) {
            // Optional: log success
        }

        @Override
        public void onFailure(StressPayload context, Throwable error) {
            System.err.println("Task failed for payload " + context.id() + ": " + error.getMessage());
        }
    }
}