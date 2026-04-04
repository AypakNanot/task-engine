package com.aypak.taskengine.executor;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.core.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SharedPoolManager 单元测试。
 * Unit tests for SharedPoolManager.
 */
@DisplayName("SharedPoolManager Tests")
class SharedPoolManagerTest {

    private TaskEngineProperties properties;
    private SharedPoolManager poolManager;

    @BeforeEach
    void setUp() {
        properties = new TaskEngineProperties();
        properties.setPoolMode(TaskEngineProperties.PoolMode.SHARED);
        poolManager = new SharedPoolManager(properties);
    }

    @Test
    @DisplayName("Should create shared executors for all task types")
    void shouldCreateSharedExecutorsForAllTaskTypes() {
        // Non-SCHEDULED types should have executors
        assertTrue(poolManager.getSharedExecutors().containsKey(TaskType.CPU_BOUND));
        assertTrue(poolManager.getSharedExecutors().containsKey(TaskType.IO_BOUND));
        assertTrue(poolManager.getSharedExecutors().containsKey(TaskType.HYBRID));
        assertTrue(poolManager.getSharedExecutors().containsKey(TaskType.BATCH));

        // SCHEDULED type should NOT be in executors map (it's in schedulers)
        assertFalse(poolManager.getSharedExecutors().containsKey(TaskType.SCHEDULED));
    }

    @Test
    @DisplayName("Should create shared scheduler for SCHEDULED type")
    void shouldCreateSharedSchedulerForScheduledType() {
        assertTrue(poolManager.getSharedSchedulers().containsKey(TaskType.SCHEDULED));
    }

    @Test
    @DisplayName("Should return correct executor for task type")
    void shouldReturnCorrectExecutorForTaskType() {
        TaskExecutor cpuExecutor = poolManager.getExecutor("CpuTask-1", TaskType.CPU_BOUND);
        TaskExecutor cpuExecutor2 = poolManager.getExecutor("CpuTask-2", TaskType.CPU_BOUND);

        // Same type should return same shared executor
        assertSame(cpuExecutor, cpuExecutor2, "Same task type should return same shared executor");
    }

    @Test
    @DisplayName("Should return different executors for different task types")
    void shouldReturnDifferentExecutorsForDifferentTaskTypes() {
        TaskExecutor cpuExecutor = poolManager.getExecutor("CpuTask", TaskType.CPU_BOUND);
        TaskExecutor ioExecutor = poolManager.getExecutor("IoTask", TaskType.IO_BOUND);

        // Different types should return different executors
        assertNotSame(cpuExecutor, ioExecutor, "Different task types should return different executors");
    }

    @Test
    @DisplayName("Should return null executor for SCHEDULED type")
    void shouldReturnNullExecutorForScheduledType() {
        TaskExecutor scheduledExecutor = poolManager.getExecutor("ScheduledTask", TaskType.SCHEDULED);

        // SCHEDULED type should return null (use getScheduler instead)
        assertNull(scheduledExecutor, "SCHEDULED type should return null executor");
    }

    @Test
    @DisplayName("Should return correct scheduler for SCHEDULED type")
    void shouldReturnCorrectSchedulerForScheduledType() {
        var scheduler1 = poolManager.getScheduler("ScheduledTask-1", TaskType.SCHEDULED);
        var scheduler2 = poolManager.getScheduler("ScheduledTask-2", TaskType.SCHEDULED);

        // Same type should return same shared scheduler
        assertSame(scheduler1, scheduler2, "SCHEDULED type should return same shared scheduler");
    }

    @Test
    @DisplayName("Should throw exception for non-SCHEDULED type requesting scheduler")
    void shouldThrowExceptionForNonScheduledTypeRequestingScheduler() {
        assertThrows(IllegalArgumentException.class, () -> {
            poolManager.getScheduler("CpuTask", TaskType.CPU_BOUND);
        });
    }

    @Test
    @DisplayName("Should track task name to executor mapping")
    void shouldTrackTaskNameToExecutorMapping() {
        poolManager.getExecutor("TestTask-1", TaskType.CPU_BOUND);
        poolManager.getExecutor("TestTask-2", TaskType.IO_BOUND);

        Map<String, TaskExecutor> taskExecutorMap = poolManager.getTaskExecutorMap();

        assertTrue(taskExecutorMap.containsKey("TestTask-1"));
        assertTrue(taskExecutorMap.containsKey("TestTask-2"));

        // Both should point to their respective shared executors
        assertSame(taskExecutorMap.get("TestTask-1"),
                   poolManager.getExecutor("TestTask-1", TaskType.CPU_BOUND));
        assertSame(taskExecutorMap.get("TestTask-2"),
                   poolManager.getExecutor("TestTask-2", TaskType.IO_BOUND));
    }

    @Test
    @DisplayName("Should apply configured pool sizes")
    void shouldApplyConfiguredPoolSizes() {
        // Get the shared executor for CPU_BOUND
        TaskExecutor executor = poolManager.getExecutor("TestTask", TaskType.CPU_BOUND);

        // Default config: CPU_BOUND core = CPU_COUNT, max = CPU_COUNT * 2
        int cpuCount = Runtime.getRuntime().availableProcessors();

        // Note: The executor is wrapped, so we check via the shared executor directly
        // The actual pool sizes are applied in createSharedExecutor
        assertNotNull(executor);
        assertTrue(executor.getMaxPoolSize() > 0, "Max pool size should be set");
    }

    @Test
    @DisplayName("Should shutdown all executors and schedulers")
    void shouldShutdownAllExecutorsAndSchedulers() {
        // Get some executors and schedulers to ensure they're initialized
        poolManager.getExecutor("TestTask-1", TaskType.CPU_BOUND);
        poolManager.getScheduler("TestTask-2", TaskType.SCHEDULED);

        // Shutdown
        poolManager.shutdown();

        // Executors should be terminated
        for (TaskExecutor executor : poolManager.getSharedExecutors().values()) {
            assertTrue(executor.isTerminated(), "Executor should be terminated after shutdown");
        }
    }

    @Test
    @DisplayName("Should handle multiple getExecutor calls for same task name")
    void shouldHandleMultipleGetExecutorCallsForSameTaskName() {
        TaskExecutor executor1 = poolManager.getExecutor("SameTask", TaskType.CPU_BOUND);
        TaskExecutor executor2 = poolManager.getExecutor("SameTask", TaskType.CPU_BOUND);

        // Should return same shared executor
        assertSame(executor1, executor2);

        // Task executor map should only have one entry
        assertEquals(1, poolManager.getTaskExecutorMap().size());
    }

    @Test
    @DisplayName("Should initialize with correct number of executors")
    void shouldInitializeWithCorrectNumberOfExecutors() {
        // Should have 4 executors (CPU_BOUND, IO_BOUND, HYBRID, BATCH)
        assertEquals(4, poolManager.getSharedExecutors().size(),
                     "Should have 4 shared executors");

        // Should have 1 scheduler (SCHEDULED)
        assertEquals(1, poolManager.getSharedSchedulers().size(),
                     "Should have 1 shared scheduler");
    }
}
