package com.aypak.engine.task;

import com.aypak.engine.task.core.*;
import com.aypak.engine.task.executor.TaskEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

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
@Tag("stress")
class TaskEngineStressTest {

    private static final String TASK_NAME = TaskEngineTestUtils.TASK_STRESS_TEST;

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private TestRestTemplate restTemplate;

    private static final int TOTAL_TASKS = 5000;
    private static final int CONCURRENT_THREADS = 50;

    @BeforeEach
    void setup() {
        TaskEngineTestUtils.registerTaskSafely(taskEngine, TaskConfig.builder()
                .taskName(TASK_NAME)
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.HIGH)
                .corePoolSize(16)
                .maxPoolSize(32)
                .queueCapacity(10000)
                .queueAlertThreshold(80)
                .rejectionPolicy(RejectionPolicy.CALLER_RUNS)
                .build(), new StressTaskProcessor());
    }

    @Test
    void stressTestHighFrequencyTasks() throws InterruptedException {
        TaskEngineTestUtils.printTestBanner("STRESS TEST", java.util.Map.of(
                "Total Tasks", TOTAL_TASKS,
                "Concurrent Threads", CONCURRENT_THREADS
        ));

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
                    taskEngine.execute(TASK_NAME, new StressPayload(taskId, "data-" + taskId));
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
        TaskEngineTestUtils.sleep(3000);

        // Print monitoring results
        TaskEngineTestUtils.printMonitoringResults(restTemplate);

        // Verify results
        var stats = taskEngine.getStats(TASK_NAME);
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
        TaskEngineTestUtils.printTestBanner("QUEUE PRESSURE TEST", java.util.Map.of(
                "Batches", 5,
                "Tasks per Batch", 1000
        ));

        // Submit tasks faster than they can be processed to trigger queue buildup
        ExecutorService executor = Executors.newFixedThreadPool(100);

        for (int batch = 0; batch < 5; batch++) {
            System.out.println("\n--- Batch " + (batch + 1) + " ---");
            final int currentBatch = batch;
            long batchStart = System.currentTimeMillis();

            // Submit 1000 tasks rapidly
            for (int i = 0; i < 1000; i++) {
                final int taskId = currentBatch * 1000 + i;
                executor.submit(() -> {
                    try {
                        taskEngine.execute(TASK_NAME, new StressPayload(taskId, "batch-" + currentBatch));
                    } catch (Exception e) {
                        // Ignore
                    }
                });
            }

            TaskEngineTestUtils.sleep(500); // Brief pause between batches

            // Check queue depth
            var stats = taskEngine.getStats(TASK_NAME);
            System.out.println("Queue depth: " + stats.getQueueDepth().get());
            System.out.println("Active threads: " + stats.getActiveThreads().get());
            System.out.println("Current max pool: " + stats.getCurrentMaxPoolSize().get());
        }

        TaskEngineTestUtils.sleep(3000); // Wait for processing
        TaskEngineTestUtils.printMonitoringResults(restTemplate);

        executor.shutdown();
    }

    // Stress test payload
    record StressPayload(int id, String data) {}

    // Stress test processor - simulates varying workloads
    static class StressTaskProcessor implements ITaskProcessor<StressPayload> {
        private final AtomicLong counter = new AtomicLong(0);

        @Override
        public String getTaskName() { return TASK_NAME; }

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