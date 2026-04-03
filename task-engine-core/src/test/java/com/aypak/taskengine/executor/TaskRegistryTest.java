package com.aypak.taskengine.executor;

import com.aypak.taskengine.core.ITaskProcessor;
import com.aypak.taskengine.core.TaskConfig;
import com.aypak.taskengine.core.TaskPriority;
import com.aypak.taskengine.core.TaskType;
import com.aypak.taskengine.monitor.TaskMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskRegistry 单元测试。
 * TaskRegistry unit tests.
 */
@DisplayName("TaskRegistry Unit Tests")
class TaskRegistryTest {

    @Test
    @DisplayName("Should register task with metrics")
    void shouldRegisterTaskWithMetrics() {
        TaskRegistry registry = new TaskRegistry();

        TaskConfig config = createConfig("TestTask", TaskType.HIGH_FREQ);
        ITaskProcessor<String> processor = createProcessor();
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.HIGH_FREQ);

        registry.registerWithMetrics(config, processor, metrics);

        assertTrue(registry.isRegistered("TestTask"));
        assertNotNull(registry.getRegistration("TestTask"));
        assertNotNull(registry.getMetrics("TestTask"));
    }

    @Test
    @DisplayName("Should register task without metrics (creates new)")
    void shouldRegisterTaskWithoutMetricsCreatesNew() {
        TaskRegistry registry = new TaskRegistry();

        TaskConfig config = createConfig("TestTask", TaskType.HIGH_FREQ);
        ITaskProcessor<String> processor = createProcessor();

        registry.register(config, processor);

        assertTrue(registry.isRegistered("TestTask"));
        assertNotNull(registry.getRegistration("TestTask"));
        assertNotNull(registry.getMetrics("TestTask"));
    }

    @Test
    @DisplayName("Should throw exception when registering duplicate task")
    void shouldThrowExceptionWhenRegisteringDuplicateTask() {
        TaskRegistry registry = new TaskRegistry();

        TaskConfig config = createConfig("TestTask", TaskType.HIGH_FREQ);
        ITaskProcessor<String> processor = createProcessor();

        registry.register(config, processor);

        // 尝试重复注册
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(config, processor)
        );
        assertTrue(exception.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("Should get registration by name")
    void shouldGetRegistrationByName() {
        TaskRegistry registry = new TaskRegistry();

        TaskConfig config = createConfig("TestTask", TaskType.HIGH_FREQ);
        ITaskProcessor<String> processor = createProcessor();

        registry.register(config, processor);

        TaskRegistry.TaskRegistration<?> registration = registry.getRegistration("TestTask");

        assertNotNull(registration);
        assertEquals("TestTask", registration.getConfig().getTaskName());
        assertEquals(TaskType.HIGH_FREQ, registration.getConfig().getTaskType());
        assertSame(processor, registration.getProcessor());
    }

    @Test
    @DisplayName("Should return null for unregistered task")
    void shouldReturnNullForUnregisteredTask() {
        TaskRegistry registry = new TaskRegistry();

        TaskRegistry.TaskRegistration<?> registration = registry.getRegistration("NonExistent");
        TaskMetrics metrics = registry.getMetrics("NonExistent");

        assertNull(registration);
        assertNull(metrics);
    }

    @Test
    @DisplayName("Should get metrics by name")
    void shouldGetMetricsByName() {
        TaskRegistry registry = new TaskRegistry();

        TaskConfig config = createConfig("TestTask", TaskType.HIGH_FREQ);
        ITaskProcessor<String> processor = createProcessor();
        TaskMetrics metrics = new TaskMetrics("TestTask", TaskType.HIGH_FREQ);

        registry.registerWithMetrics(config, processor, metrics);

        TaskMetrics retrievedMetrics = registry.getMetrics("TestTask");

        assertNotNull(retrievedMetrics);
        assertEquals("TestTask", retrievedMetrics.getTaskName());
        assertSame(metrics, retrievedMetrics);
    }

    @Test
    @DisplayName("Should get all registrations")
    void shouldGetAllRegistrations() {
        TaskRegistry registry = new TaskRegistry();

        // 注册多个任务
        for (int i = 0; i < 5; i++) {
            TaskConfig config = createConfig("Task" + i, TaskType.HIGH_FREQ);
            ITaskProcessor<String> processor = createProcessor();
            registry.register(config, processor);
        }

        Collection<TaskRegistry.TaskRegistration<?>> registrations = registry.getAllRegistrations();

        assertEquals(5, registrations.size());
    }

    @Test
    @DisplayName("Should get all metrics")
    void shouldGetAllMetrics() {
        TaskRegistry registry = new TaskRegistry();

        // 注册多个任务
        for (int i = 0; i < 5; i++) {
            TaskConfig config = createConfig("Task" + i, TaskType.HIGH_FREQ);
            ITaskProcessor<String> processor = createProcessor();
            registry.register(config, processor);
        }

        Map<String, TaskMetrics> allMetrics = registry.getAllMetrics();

        assertEquals(5, allMetrics.size());
        for (int i = 0; i < 5; i++) {
            assertTrue(allMetrics.containsKey("Task" + i));
        }
    }

    @Test
    @DisplayName("Should return empty collection when no tasks registered")
    void shouldReturnEmptyCollectionWhenNoTasksRegistered() {
        TaskRegistry registry = new TaskRegistry();

        Collection<TaskRegistry.TaskRegistration<?>> registrations = registry.getAllRegistrations();
        Map<String, TaskMetrics> metrics = registry.getAllMetrics();

        assertTrue(registrations.isEmpty());
        assertTrue(metrics.isEmpty());
    }

    @Test
    @DisplayName("Should deregister task")
    void shouldDeregisterTask() {
        TaskRegistry registry = new TaskRegistry();

        TaskConfig config = createConfig("TestTask", TaskType.HIGH_FREQ);
        ITaskProcessor<String> processor = createProcessor();

        registry.register(config, processor);
        assertTrue(registry.isRegistered("TestTask"));

        registry.deregister("TestTask");

        assertFalse(registry.isRegistered("TestTask"));
        assertNull(registry.getRegistration("TestTask"));
        assertNull(registry.getMetrics("TestTask"));
    }

    @Test
    @DisplayName("Should handle deregister non-existent task gracefully")
    void shouldHandleDeregisterNonExistentTaskGracefully() {
        TaskRegistry registry = new TaskRegistry();

        // 注销不存在的任务不应抛出异常
        assertDoesNotThrow(() -> registry.deregister("NonExistent"));
    }

    @Test
    @DisplayName("Should get task count")
    void shouldGetTaskCount() {
        TaskRegistry registry = new TaskRegistry();

        assertEquals(0, registry.getTaskCount());

        registry.register(createConfig("Task1", TaskType.HIGH_FREQ), createProcessor());
        assertEquals(1, registry.getTaskCount());

        registry.register(createConfig("Task2", TaskType.BACKGROUND), createProcessor());
        assertEquals(2, registry.getTaskCount());

        registry.deregister("Task1");
        assertEquals(1, registry.getTaskCount());
    }

    @Test
    @DisplayName("Should register tasks with different types")
    void shouldRegisterTasksWithDifferentTypes() {
        TaskRegistry registry = new TaskRegistry();

        registry.register(createConfig("InitTask", TaskType.INIT), createProcessor());
        registry.register(createConfig("CronTask", TaskType.CRON), createProcessor());
        registry.register(createConfig("HighFreqTask", TaskType.HIGH_FREQ), createProcessor());
        registry.register(createConfig("BackgroundTask", TaskType.BACKGROUND), createProcessor());

        assertEquals(4, registry.getTaskCount());
        assertTrue(registry.isRegistered("InitTask"));
        assertTrue(registry.isRegistered("CronTask"));
        assertTrue(registry.isRegistered("HighFreqTask"));
        assertTrue(registry.isRegistered("BackgroundTask"));
    }

    @Test
    @DisplayName("Should store and retrieve processor correctly")
    void shouldStoreAndRetrieveProcessorCorrectly() {
        TaskRegistry registry = new TaskRegistry();

        TestProcessor<String> testProcessor = new TestProcessor<>();
        TaskConfig config = createConfig("TestTask", TaskType.HIGH_FREQ);

        registry.register(config, testProcessor);

        @SuppressWarnings("unchecked")
        TaskRegistry.TaskRegistration<String> registration =
                (TaskRegistry.TaskRegistration<String>) registry.getRegistration("TestTask");

        assertNotNull(registration);
        assertSame(testProcessor, registration.getProcessor());
    }

    @Test
    @DisplayName("Should return unmodifiable collection for getAllRegistrations")
    void shouldReturnUnmodifiableCollectionForGetAllRegistrations() {
        TaskRegistry registry = new TaskRegistry();

        registry.register(createConfig("Task1", TaskType.HIGH_FREQ), createProcessor());

        Collection<TaskRegistry.TaskRegistration<?>> registrations = registry.getAllRegistrations();

        assertThrows(UnsupportedOperationException.class, () -> registrations.add(null));
    }

    @Test
    @DisplayName("Should return unmodifiable map for getAllMetrics")
    void shouldReturnUnmodifiableMapForGetAllMetrics() {
        TaskRegistry registry = new TaskRegistry();

        registry.register(createConfig("Task1", TaskType.HIGH_FREQ), createProcessor());

        Map<String, TaskMetrics> metrics = registry.getAllMetrics();

        assertThrows(UnsupportedOperationException.class, () -> metrics.put("key", null));
    }

    @Test
    @DisplayName("Should handle concurrent registration")
    void shouldHandleConcurrentRegistration() throws InterruptedException {
        TaskRegistry registry = new TaskRegistry();

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                TaskConfig config = createConfig("ConcurrentTask" + index, TaskType.HIGH_FREQ);
                ITaskProcessor<String> processor = createProcessor();
                registry.register(config, processor);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(10, registry.getTaskCount());
        for (int i = 0; i < 10; i++) {
            assertTrue(registry.isRegistered("ConcurrentTask" + i));
        }
    }

    // 辅助方法

    private TaskConfig createConfig(String taskName, TaskType type) {
        return TaskConfig.builder()
                .taskName(taskName)
                .taskType(type)
                .priority(TaskPriority.MEDIUM)
                .build();
    }

    private ITaskProcessor<String> createProcessor() {
        return new TestProcessor<>();
    }

    // 测试用处理器
    private static class TestProcessor<T> implements ITaskProcessor<T> {
        @Override
        public String getTaskName() {
            return "TestProcessor";
        }

        @Override
        public TaskType getTaskType() {
            return TaskType.HIGH_FREQ;
        }

        @Override
        public TaskPriority getPriority() {
            return TaskPriority.MEDIUM;
        }

        @Override
        public void process(T payload) {
            // 空实现
        }
    }
}
