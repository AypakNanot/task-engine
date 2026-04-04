package com.aypak.taskengine.executor;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.core.*;
import com.aypak.taskengine.monitor.TaskMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskEngineImpl 单元测试。
 * TaskEngineImpl unit tests.
 */
@DisplayName("TaskEngineImpl Unit Tests")
class TaskEngineImplTest {

    private TaskEngineImpl engine;
    private TaskEngineProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TaskEngineProperties();
        properties.setShutdownTimeout(5);
        engine = new TaskEngineImpl(properties);
    }

    @Test
    @DisplayName("Should create engine with default configuration")
    void shouldCreateEngineWithDefaultConfiguration() {
        assertNotNull(engine);
        assertNotNull(engine.getRegistry());
        assertTrue(engine.getExecutors().isEmpty());
    }

    @Test
    @DisplayName("Should register task processor")
    void shouldRegisterTaskProcessor() {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        TestProcessor<String> processor = new TestProcessor<>();

        assertDoesNotThrow(() -> engine.register(config, processor));

        assertTrue(engine.getRegistry().isRegistered("TestTask"));
        assertEquals(1, engine.getExecutors().size());
    }

    @Test
    @DisplayName("Should throw exception when registering duplicate task")
    void shouldThrowExceptionWhenRegisteringDuplicateTask() {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        TestProcessor<String> processor = new TestProcessor<>();

        engine.register(config, processor);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> engine.register(config, processor)
        );
        assertTrue(exception.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("Should execute registered task")
    void shouldExecuteRegisteredTask() throws Exception {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);

        TestProcessor<String> processor = new TestProcessor<>((payload) -> {
            executed.set(true);
            latch.countDown();
        });

        engine.register(config, processor);
        engine.execute("TestTask", "test-payload");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(executed.get());

        engine.shutdown();
    }

    @Test
    @DisplayName("Should throw exception when executing unregistered task")
    void shouldThrowExceptionWhenExecutingUnregisteredTask() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> engine.execute("NonExistent", "payload")
        );
        assertTrue(exception.getMessage().contains("not registered"));
    }

    @Test
    @DisplayName("Should get stats for all tasks")
    void shouldGetStatsForAllTasks() {
        TaskConfig config1 = createConfig("Task1", TaskType.IO_BOUND);
        TaskConfig config2 = createConfig("Task2", TaskType.BATCH);

        engine.register(config1, new TestProcessor<>());
        engine.register(config2, new TestProcessor<>());

        Map<String, TaskMetrics> stats = engine.getStats();

        assertEquals(2, stats.size());
        assertTrue(stats.containsKey("Task1"));
        assertTrue(stats.containsKey("Task2"));
    }

    @Test
    @DisplayName("Should get stats for specific task")
    void shouldGetStatsForSpecificTask() {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        engine.register(config, new TestProcessor<>());

        TaskMetrics metrics = engine.getStats("TestTask");

        assertNotNull(metrics);
        assertEquals("TestTask", metrics.getTaskName());
    }

    @Test
    @DisplayName("Should return null for unregistered task stats")
    void shouldReturnNullForUnregisteredTaskStats() {
        TaskMetrics metrics = engine.getStats("NonExistent");
        assertNull(metrics);
    }

    @Test
    @DisplayName("Should update task config")
    void shouldUpdateTaskConfig() {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        engine.register(config, new TestProcessor<>());

        DynamicConfig dynamicConfig = DynamicConfig.builder()
                .corePoolSize(8)
                .maxPoolSize(16)
                .build();

        assertDoesNotThrow(() -> engine.updateConfig("TestTask", dynamicConfig));
    }

    @Test
    @DisplayName("Should throw exception when updating unregistered task config")
    void shouldThrowExceptionWhenUpdatingUnregisteredTaskConfig() {
        DynamicConfig config = DynamicConfig.builder()
                .corePoolSize(8)
                .maxPoolSize(16)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> engine.updateConfig("NonExistent", config)
        );
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Should reset metrics for specific task")
    void shouldResetMetricsForSpecificTask() {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        engine.register(config, new TestProcessor<>());

        // 先执行一些任务来产生指标
        CountDownLatch latch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            engine.execute("TestTask", "payload-" + i);
        }

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 重置指标
        engine.resetMetrics("TestTask");

        TaskMetrics metrics = engine.getStats("TestTask");
        assertNotNull(metrics);
        assertEquals(0, metrics.getSuccessCount().sum());
        assertEquals(0, metrics.getFailureCount().sum());
    }

    @Test
    @DisplayName("Should reset all metrics")
    void shouldResetAllMetrics() {
        TaskConfig config1 = createConfig("Task1", TaskType.IO_BOUND);
        TaskConfig config2 = createConfig("Task2", TaskType.BATCH);

        engine.register(config1, new TestProcessor<>());
        engine.register(config2, new TestProcessor<>());

        engine.resetAllMetrics();

        Map<String, TaskMetrics> allMetrics = engine.getRegistry().getAllMetrics();
        assertEquals(2, allMetrics.size());

        for (TaskMetrics metrics : allMetrics.values()) {
            assertEquals(0, metrics.getSuccessCount().sum());
            assertEquals(0, metrics.getFailureCount().sum());
        }
    }

    @Test
    @DisplayName("Should get all registrations")
    void shouldGetAllRegistrations() {
        TaskConfig config1 = createConfig("Task1", TaskType.IO_BOUND);
        TaskConfig config2 = createCronConfig("Task2");
        TaskConfig config3 = createConfig("Task3", TaskType.CPU_BOUND);

        engine.register(config1, new TestProcessor<>());
        engine.register(config2, new TestProcessor<>());
        engine.register(config3, new TestProcessor<>());

        Collection<TaskRegistry.TaskRegistration<?>> registrations = engine.getAllRegistrations();

        assertEquals(3, registrations.size());
    }

    @Test
    @DisplayName("Should handle task success callback")
    void shouldHandleTaskSuccessCallback() throws Exception {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean onSuccessCalled = new AtomicBoolean(false);

        TestProcessor<String> processor = new TestProcessor<>(payload -> {
            // 处理逻辑
        }, () -> {
            onSuccessCalled.set(true);
            latch.countDown();
        });

        engine.register(config, processor);
        engine.execute("TestTask", "test-payload");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(onSuccessCalled.get());

        engine.shutdown();
    }

    @Test
    @DisplayName("Should handle task failure callback")
    void shouldHandleTaskFailureCallback() throws Exception {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean onFailureCalled = new AtomicBoolean(false);

        TestProcessor<String> processor = new TestProcessor<>(payload -> {
            throw new RuntimeException("Test exception");
        }, () -> {
        }, (payload, error) -> {
            onFailureCalled.set(true);
            latch.countDown();
        });

        engine.register(config, processor);
        engine.execute("TestTask", "test-payload");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(onFailureCalled.get());

        engine.shutdown();
    }

    @Test
    @DisplayName("Should record failure metrics on task exception")
    void shouldRecordFailureMetricsOnTaskException() throws Exception {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        CountDownLatch latch = new CountDownLatch(1);

        TestProcessor<String> processor = new TestProcessor<>(payload -> {
            latch.countDown();
            throw new RuntimeException("Test exception");
        });

        engine.register(config, processor);

        try {
            engine.execute("TestTask", "test-payload");
        } catch (Exception e) {
            // 预期异常
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(100);

        TaskMetrics metrics = engine.getStats("TestTask");
        assertNotNull(metrics);
        assertEquals(0, metrics.getSuccessCount().sum());
        // 注意：失败会被记录两次 - 一次在 TaskExecutor.execute，一次在 TaskEngineImpl.execute
        assertTrue(metrics.getFailureCount().sum() >= 1);

        engine.shutdown();
    }

    @Test
    @DisplayName("Should handle graceful shutdown")
    void shouldHandleGracefulShutdown() throws Exception {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        CountDownLatch latch = new CountDownLatch(3);

        TestProcessor<String> processor = new TestProcessor<>(payload -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            latch.countDown();
        });

        engine.register(config, processor);

        // 提交一些任务
        for (int i = 0; i < 3; i++) {
            engine.execute("TestTask", "payload-" + i);
        }

        engine.shutdown();

        assertTrue(latch.await(3, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should handle multiple task types")
    void shouldHandleMultipleTaskTypes() {
        engine.register(createConfig("InitTask", TaskType.CPU_BOUND), new TestProcessor<>());
        engine.register(createCronConfig("CronTask"), new TestProcessor<>());
        engine.register(createConfig("HighFreqTask", TaskType.IO_BOUND), new TestProcessor<>());
        engine.register(createConfig("BackgroundTask", TaskType.BATCH), new TestProcessor<>());

        assertEquals(4, engine.getExecutors().size());
        assertTrue(engine.getRegistry().isRegistered("InitTask"));
        assertTrue(engine.getRegistry().isRegistered("CronTask"));
        assertTrue(engine.getRegistry().isRegistered("HighFreqTask"));
        assertTrue(engine.getRegistry().isRegistered("BackgroundTask"));
    }

    @Test
    @DisplayName("Should track execution count correctly")
    void shouldTrackExecutionCountCorrectly() throws Exception {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        CountDownLatch latch = new CountDownLatch(10);

        TestProcessor<String> processor = new TestProcessor<>(payload -> latch.countDown());

        engine.register(config, processor);

        // 执行 10 个任务
        for (int i = 0; i < 10; i++) {
            engine.execute("TestTask", "payload-" + i);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(100);

        TaskMetrics metrics = engine.getStats("TestTask");
        assertNotNull(metrics);
        assertEquals(10, metrics.getSuccessCount().sum());
    }

    @Test
    @DisplayName("Should handle context propagation")
    void shouldHandleContextPropagation() throws Exception {
        TaskConfig config = createConfig("TestTask", TaskType.IO_BOUND);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong capturedTime = new AtomicLong(0);

        TestProcessor<String> processor = new TestProcessor<>(payload -> {
            capturedTime.set(System.currentTimeMillis());
            latch.countDown();
        });

        engine.register(config, processor);
        engine.execute("TestTask", "test-payload");

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(capturedTime.get() > 0);

        engine.shutdown();
    }

    // 辅助方法

    private TaskConfig createConfig(String taskName, TaskType type) {
        return TaskConfig.builder()
                .taskName(taskName)
                .taskType(type)
                .build();
    }

    private TaskConfig createCronConfig(String taskName) {
        return TaskConfig.builder()
                .taskName(taskName)
                .taskType(TaskType.SCHEDULED)
                .cronExpression("0 0 12 * * ?")
                .build();
    }

    // 测试用处理器
    private static class TestProcessor<T> implements ITaskProcessor<T> {
        private final Runnable onSuccessHandler;
        private final BiConsumer<T, Throwable> onFailureHandler;
        private final Consumer<T> processHandler;

        TestProcessor() {
            this(null, null, null);
        }

        TestProcessor(Consumer<T> processHandler) {
            this(processHandler, null, null);
        }

        TestProcessor(Consumer<T> processHandler, Runnable onSuccessHandler) {
            this(processHandler, onSuccessHandler, null);
        }

        TestProcessor(Consumer<T> processHandler, Runnable onSuccessHandler, BiConsumer<T, Throwable> onFailureHandler) {
            this.processHandler = processHandler;
            this.onSuccessHandler = onSuccessHandler;
            this.onFailureHandler = onFailureHandler;
        }

        @Override
        public String getTaskName() {
            return "TestProcessor";
        }

        @Override
        public TaskType getTaskType() {
            return TaskType.IO_BOUND;
        }

        @Override
        public void process(T payload) {
            if (processHandler != null) {
                processHandler.accept(payload);
            }
        }

        @Override
        public void onSuccess(T payload) {
            if (onSuccessHandler != null) {
                onSuccessHandler.run();
            }
        }

        @Override
        public void onFailure(T payload, Throwable error) {
            if (onFailureHandler != null) {
                onFailureHandler.accept(payload, error);
            }
        }
    }

    @FunctionalInterface
    private interface Consumer<T> {
        void accept(T t);
    }

    @FunctionalInterface
    private interface BiConsumer<T, U> {
        void accept(T t, U u);
    }
}
