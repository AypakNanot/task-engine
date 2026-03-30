package com.aypak.engine.task;

import com.aypak.engine.task.core.*;
import com.aypak.engine.task.executor.TaskEngine;
import com.aypak.engine.task.monitor.TaskMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("stress")
class TaskEngineLongStressTest {

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private TestRestTemplate restTemplate;

    private static final long TEST_DURATION_MS = 15 * 60 * 1000;
    private static final int SUBMITTER_THREADS = 200;
    private static final String TASK_NAME = TaskEngineTestUtils.TASK_LONG_STRESS;

    private final int cpuCount = Runtime.getRuntime().availableProcessors();

    @BeforeEach
    void setup() {
        TaskEngineTestUtils.registerTaskSafely(taskEngine, TaskConfig.builder()
                .taskName(TASK_NAME)
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.HIGH)
                .corePoolSize(cpuCount * 8)
                .maxPoolSize(cpuCount * 16)
                .queueCapacity(100000)
                .queueAlertThreshold(95)
                .rejectionPolicy(RejectionPolicy.CALLER_RUNS)
                .build(), new FastTaskProcessor());
    }

    @Test
    void fifteenMinuteLoadTest() throws InterruptedException {
        TaskEngineTestUtils.printTestBanner("15 MINUTE SUSTAINED LOAD TEST", java.util.Map.of(
                "Test Duration", (TEST_DURATION_MS / 1000 / 60) + " minutes",
                "Submitter Threads", SUBMITTER_THREADS,
                "Target QPS", "5000+"
        ));

        AtomicLong totalSubmitted = new AtomicLong(0);
        AtomicLong totalSucceeded = new AtomicLong(0);
        AtomicLong totalFailed = new AtomicLong(0);
        AtomicLong peakQps = new AtomicLong(0);
        AtomicLong minQps = new AtomicLong(Long.MAX_VALUE);

        AtomicBoolean running = new AtomicBoolean(true);
        long startTime = System.currentTimeMillis();

        Thread reporter = new Thread(() -> {
            long lastCount = 0;
            long lastTime = startTime;
            while (running.get()) {
                TaskEngineTestUtils.sleep(10000);
                if (!running.get()) break;

                long now = System.currentTimeMillis();
                long currentCount = totalSucceeded.get();
                long intervalCount = currentCount - lastCount;
                long intervalMs = now - lastTime;
                double intervalQps = intervalCount * 1000.0 / intervalMs;
                double overallQps = currentCount * 1000.0 / (now - startTime);

                if (intervalQps > peakQps.get()) peakQps.set((long) intervalQps);
                if (intervalQps < minQps.get() && intervalCount > 0) minQps.set((long) intervalQps);

                long elapsedSec = (now - startTime) / 1000;
                long min = elapsedSec / 60;
                long sec = elapsedSec % 60;

                TaskMetrics metrics = taskEngine.getStats(TASK_NAME);
                System.out.printf("[%02d:%02d] QPS: %6.0f | Overall: %6.0f | Submitted: %d | Success: %d | Failed: %d | Queue: %d | Active: %d%n",
                        min, sec, intervalQps, overallQps,
                        totalSubmitted.get(), currentCount, totalFailed.get(),
                        metrics.getQueueDepth().get(), metrics.getActiveThreads().get());

                lastCount = currentCount;
                lastTime = now;
            }
        });

        Thread[] submitters = new Thread[SUBMITTER_THREADS];
        for (int t = 0; t < SUBMITTER_THREADS; t++) {
            final int threadId = t;
            submitters[t] = new Thread(() -> {
                int taskId = threadId * 1000000;
                while (running.get()) {
                    final int id = taskId++;
                    try {
                        taskEngine.execute(TASK_NAME, new TaskPayload(id, "load"));
                        totalSubmitted.incrementAndGet();
                        totalSucceeded.incrementAndGet();
                    } catch (Exception e) {
                        totalFailed.incrementAndGet();
                    }
                }
            });
        }

        reporter.start();
        for (Thread t : submitters) { t.start(); }

        System.out.println("Starting 15-minute load test...\n");
        TaskEngineTestUtils.sleep(TEST_DURATION_MS);

        System.out.println("\nStopping test...");
        running.set(false);
        reporter.join(1000);
        for (Thread t : submitters) { t.join(1000); }

        System.out.println("Waiting for remaining tasks...");
        TaskEngineTestUtils.sleep(5000);

        long endTime = System.currentTimeMillis();
        long actualDuration = endTime - startTime;
        long finalSuccess = totalSucceeded.get();
        long finalFailed = totalFailed.get();
        long finalSubmitted = totalSubmitted.get();
        double overallQps = finalSuccess * 1000.0 / actualDuration;
        double submitRate = finalSubmitted * 1000.0 / actualDuration;
        TaskMetrics finalMetrics = taskEngine.getStats(TASK_NAME);

        System.out.println("\n================================================================================");
        System.out.println("                        FINAL TEST RESULTS");
        System.out.println("================================================================================");
        System.out.println("\n--- Test Configuration ---");
        System.out.println("Duration: " + actualDuration + " ms (" + (actualDuration / 60000.0) + " minutes)");
        System.out.println("Submitter Threads: " + SUBMITTER_THREADS);
        System.out.println("CPU Count: " + cpuCount);
        System.out.println("Core Pool Size: " + finalMetrics.getOriginalMaxPoolSize().get());
        System.out.println("Max Pool Size: " + finalMetrics.getCurrentMaxPoolSize().get());
        System.out.println("\n--- Throughput Metrics ---");
        System.out.println("Submit Rate: " + String.format("%,.0f", submitRate) + " tasks/sec");
        System.out.println("Overall Processing QPS: " + String.format("%,.0f", overallQps) + " tasks/sec");
        System.out.println("Peak Interval QPS: " + peakQps.get() + " tasks/sec");
        System.out.println("Min Interval QPS: " + (minQps.get() == Long.MAX_VALUE ? 0 : minQps.get()) + " tasks/sec");
        System.out.println("\n--- Task Counts ---");
        System.out.println("Total Submitted: " + finalSubmitted);
        System.out.println("Total Succeeded: " + finalSuccess);
        System.out.println("Total Failed: " + finalFailed);
        System.out.println("Success Rate: " + String.format("%.4f", finalSuccess * 100.0 / finalSubmitted) + "%");
        System.out.println("\n--- Performance Metrics ---");
        System.out.println("Average Response Time: " + finalMetrics.getAvgResponseTime() + " ms");
        System.out.println("Peak Active Threads: " + finalMetrics.getPeakThreads().get());
        System.out.println("Final Queue Depth: " + finalMetrics.getQueueDepth().get());
        System.out.println("\n================================================================================");

        TaskEngineTestUtils.printMonitoringResults(restTemplate);
    }

    record TaskPayload(int id, String data) {}

    static class FastTaskProcessor implements ITaskProcessor<TaskPayload> {
        public String getTaskName() { return TASK_NAME; }
        public TaskType getTaskType() { return TaskType.HIGH_FREQ; }
        public TaskPriority getPriority() { return TaskPriority.HIGH; }
        public void process(TaskPayload payload) {
            int sleepTime = (payload.id() % 10 == 0) ? 2 : 1;
            TaskEngineTestUtils.sleep(sleepTime);
        }
    }
}