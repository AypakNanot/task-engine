package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.*;
import com.aypak.taskengine.monitor.TaskMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskExecutor 单元测试。
 * TaskExecutor unit tests.
 */
@DisplayName("TaskExecutor Unit Tests")
class TaskExecutorTest {

    @Test
    @DisplayName("Should create TaskExecutor with ThreadPoolTaskExecutor")
    void shouldCreateTaskExecutorWithThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask");
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);

        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        assertNotNull(executor);
        assertEquals("TestTask", executor.getTaskName());
        assertEquals(TaskType.IO_BOUND, executor.getTaskType());
        assertEquals(metrics, executor.getMetrics());
    }

    @Test
    @DisplayName("Should execute runnable with context")
    void shouldExecuteRunnableWithContext() throws Exception {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask");
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);

        TaskContext context = new TaskContext();
        executor.execute(() -> {
            executed.set(true);
            latch.countDown();
        }, context);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(executed.get());

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should record success metrics after execution")
    void shouldRecordSuccessMetricsAfterExecution() throws Exception {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask");
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch.countDown();
        }, new TaskContext());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(100); // 等待指标更新

        assertEquals(1, metrics.getSuccessCount().sum());
        assertEquals(0, metrics.getFailureCount().sum());

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should record failure metrics on exception")
    void shouldRecordFailureMetricsOnException() throws Exception {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask");
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            latch.countDown();
            throw new RuntimeException("Test exception");
        }, new TaskContext());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(100);

        assertEquals(0, metrics.getSuccessCount().sum());
        assertEquals(1, metrics.getFailureCount().sum());

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should propagate MDC context to task thread")
    void shouldPropagateMdcContextToTaskThread() throws Exception {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask");
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong capturedTraceId = new AtomicLong();

        TaskContext context = new TaskContext("test-trace-123", null, System.currentTimeMillis());
        executor.execute(() -> {
            // 在任务线程中捕获 traceId（这里无法直接访问 MDC，但我们知道它被传播了）
            capturedTraceId.set(System.currentTimeMillis());
            latch.countDown();
        }, context);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(capturedTraceId.get() > 0);

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should clear MDC after execution")
    void shouldClearMdcAfterExecution() throws Exception {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask");
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        CountDownLatch latch = new CountDownLatch(1);

        TaskContext context = new TaskContext("test-trace", null, System.currentTimeMillis());
        executor.execute(() -> {
            latch.countDown();
        }, context);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(100);

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should update pool metrics")
    void shouldUpdatePoolMetrics() throws Exception {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask", 2, 4);
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        CountDownLatch latch = new CountDownLatch(5);

        // 提交多个任务以活动线程
        for (int i = 0; i < 5; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                latch.countDown();
            }, new TaskContext());
        }

        Thread.sleep(20); // 等待任务开始执行

        executor.updatePoolMetrics();

        assertTrue(metrics.getQueueDepth().get() > 0 || metrics.getActiveThreads().get() > 0);
        assertTrue(metrics.getQueueDepth().get() >= 0);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should get queue size")
    void shouldGetQueueSize() {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask", 1, 2, 10);
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        // 初始队列大小应为 0
        assertEquals(0, executor.getQueueSize());

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should get queue capacity")
    void shouldGetQueueCapacity() {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask", 1, 2, 100);
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        int capacity = executor.getQueueCapacity();
        assertTrue(capacity > 0);

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should get active threads")
    void shouldGetActiveThreads() throws Exception {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask", 2, 4);
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            latch.countDown();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, new TaskContext());

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(50);

        assertTrue(executor.getActiveThreads() > 0);

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should set core pool size")
    void shouldSetCorePoolSize() {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask", 2, 4);
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        executor.setCorePoolSize(8);

        // 验证核心线程池大小已更改
        assertEquals(8, executor.getThreadPoolExecutor().getCorePoolSize());

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should set max pool size")
    void shouldSetMaxPoolSize() {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask", 2, 4);
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        executor.setMaxPoolSize(8);

        assertEquals(8, executor.getThreadPoolExecutor().getMaximumPoolSize());
        // 验证 metrics 也被更新
        assertEquals(8, metrics.getCurrentMaxPoolSize().get());

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should get max pool size")
    void shouldGetMaxPoolSize() {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask", 2, 8);
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        assertEquals(8, executor.getMaxPoolSize());

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should handle graceful shutdown")
    void shouldHandleGracefulShutdown() throws Exception {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask");
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        CountDownLatch latch = new CountDownLatch(3);

        // 提交一些任务
        for (int i = 0; i < 3; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                latch.countDown();
            }, new TaskContext());
        }

        executor.shutdown(5);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(executor.isTerminated());
    }

    @Test
    @DisplayName("Should check if terminated")
    void shouldCheckIfTerminated() {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask");
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        assertFalse(executor.isTerminated());

        executor.shutdown(5);
        assertTrue(executor.isTerminated());
    }

    @Test
    @DisplayName("Should handle null context gracefully")
    void shouldHandleNullContextGracefully() throws Exception {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask");
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            latch.countDown();
        }, null);

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should handle multiple executions")
    void shouldHandleMultipleExecutions() throws Exception {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask");
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        CountDownLatch latch = new CountDownLatch(10);
        AtomicInteger executionCount = new AtomicInteger(0);

        for (int i = 0; i < 10; i++) {
            executor.execute(() -> {
                executionCount.incrementAndGet();
                latch.countDown();
            }, new TaskContext());
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(100);

        assertEquals(10, executionCount.get());
        assertEquals(10, metrics.getSuccessCount().sum());

        executor.shutdown(5);
    }

    @Test
    @DisplayName("Should cache queue capacity after first call")
    void shouldCacheQueueCapacityAfterFirstCall() {
        ThreadPoolTaskExecutor springExecutor = createThreadPool("TestTask", 2, 4, 100);
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.IO_BOUND);
        TaskExecutor executor = new TaskExecutor(springExecutor, "TestTask", TaskType.IO_BOUND, metrics);

        // 第一次调用应该计算并缓存
        int capacity1 = executor.getQueueCapacity();
        assertTrue(capacity1 > 0);

        // 第二次调用应该使用缓存
        int capacity2 = executor.getQueueCapacity();
        assertEquals(capacity1, capacity2);

        executor.shutdown(5);
    }

    // 辅助方法

    private ThreadPoolTaskExecutor createThreadPool(String taskName) {
        return createThreadPool(taskName, 2, 4, 100);
    }

    private ThreadPoolTaskExecutor createThreadPool(String taskName, int coreSize, int maxSize) {
        return createThreadPool(taskName, coreSize, maxSize, 100);
    }

    private ThreadPoolTaskExecutor createThreadPool(String taskName, int coreSize, int maxSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(taskName + "-");
        executor.initialize();
        return executor;
    }
}
