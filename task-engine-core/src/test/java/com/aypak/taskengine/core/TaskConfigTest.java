package com.aypak.taskengine.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskConfig 单元测试。
 * TaskConfig unit tests.
 */
@DisplayName("TaskConfig Unit Tests")
class TaskConfigTest {

    @Test
    @DisplayName("Should create TaskConfig with all required fields")
    void shouldCreateTaskConfigWithAllRequiredFields() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(TaskType.IO_BOUND)
                .build();

        assertNotNull(config);
        assertEquals("TestTask", config.getTaskName());
        assertEquals(TaskType.IO_BOUND, config.getTaskType());
    }

    @Test
    @DisplayName("Should validate successfully with all required fields")
    void shouldValidateSuccessfullyWithAllRequiredFields() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(TaskType.IO_BOUND)
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should throw exception when taskName is null")
    void shouldThrowExceptionWhenTaskNameIsNull() {
        TaskConfig config = TaskConfig.builder()
                .taskName(null)
                .taskType(TaskType.IO_BOUND)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("taskName"));
    }

    @Test
    @DisplayName("Should throw exception when taskName is blank")
    void shouldThrowExceptionWhenTaskNameIsBlank() {
        TaskConfig config = TaskConfig.builder()
                .taskName("   ")
                .taskType(TaskType.IO_BOUND)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("taskName"));
    }

    @Test
    @DisplayName("Should throw exception when taskType is null")
    void shouldThrowExceptionWhenTaskTypeIsNull() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(null)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("taskType"));
    }

    @Test
    @DisplayName("Should throw exception when queueAlertThreshold is negative")
    void shouldThrowExceptionWhenQueueAlertThresholdIsNegative() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(TaskType.IO_BOUND)
                .queueAlertThreshold(-1)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("queueAlertThreshold"));
    }

    @Test
    @DisplayName("Should throw exception when queueAlertThreshold is over 100")
    void shouldThrowExceptionWhenQueueAlertThresholdIsOver100() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(TaskType.IO_BOUND)
                .queueAlertThreshold(101)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("queueAlertThreshold"));
    }

    @Test
    @DisplayName("Should accept queueAlertThreshold of 0")
    void shouldAcceptQueueAlertThresholdOf0() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(TaskType.IO_BOUND)
                .queueAlertThreshold(0)
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should accept queueAlertThreshold of 100")
    void shouldAcceptQueueAlertThresholdOf100() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(TaskType.IO_BOUND)
                .queueAlertThreshold(100)
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should throw exception when SCHEDULED task has no schedule config")
    void shouldThrowExceptionWhenScheduledTaskHasNoScheduleConfig() {
        TaskConfig config = TaskConfig.builder()
                .taskName("ScheduledTask")
                .taskType(TaskType.SCHEDULED)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("SCHEDULED"));
    }

    @Test
    @DisplayName("Should validate SCHEDULED task with cronExpression")
    void shouldValidateScheduledTaskWithCronExpression() {
        TaskConfig config = TaskConfig.builder()
                .taskName("ScheduledTask")
                .taskType(TaskType.SCHEDULED)
                .cronExpression("0 0 12 * * ?")
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should validate SCHEDULED task with fixedRate")
    void shouldValidateScheduledTaskWithFixedRate() {
        TaskConfig config = TaskConfig.builder()
                .taskName("ScheduledTask")
                .taskType(TaskType.SCHEDULED)
                .fixedRate(5000L)
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should validate SCHEDULED task with fixedDelay")
    void shouldValidateScheduledTaskWithFixedDelay() {
        TaskConfig config = TaskConfig.builder()
                .taskName("ScheduledTask")
                .taskType(TaskType.SCHEDULED)
                .fixedDelay(5000L)
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should validate non-SCHEDULED tasks without schedule config")
    void shouldValidateNonScheduledTasksWithoutScheduleConfig() {
        TaskConfig cpuConfig = TaskConfig.builder()
                .taskName("CpuTask")
                .taskType(TaskType.CPU_BOUND)
                .build();

        TaskConfig ioConfig = TaskConfig.builder()
                .taskName("IoTask")
                .taskType(TaskType.IO_BOUND)
                .build();

        TaskConfig batchConfig = TaskConfig.builder()
                .taskName("BatchTask")
                .taskType(TaskType.BATCH)
                .build();

        assertDoesNotThrow(() -> cpuConfig.validate());
        assertDoesNotThrow(() -> ioConfig.validate());
        assertDoesNotThrow(() -> batchConfig.validate());
    }

    @Test
    @DisplayName("Should create config with all optional fields")
    void shouldCreateConfigWithAllOptionalFields() {
        TaskConfig config = TaskConfig.builder()
                .taskName("FullConfigTask")
                .taskType(TaskType.IO_BOUND)
                .corePoolSize(8)
                .maxPoolSize(16)
                .queueCapacity(10000)
                .rejectionPolicy(RejectionPolicy.CALLER_RUNS)
                .queueAlertThreshold(75)
                .cronExpression("0 0 12 * * ?")
                .fixedRate(5000L)
                .fixedDelay(6000L)
                .build();

        assertEquals("FullConfigTask", config.getTaskName());
        assertEquals(TaskType.IO_BOUND, config.getTaskType());
        assertEquals(8, config.getCorePoolSize());
        assertEquals(16, config.getMaxPoolSize());
        assertEquals(10000, config.getQueueCapacity());
        assertEquals(RejectionPolicy.CALLER_RUNS, config.getRejectionPolicy());
        assertEquals(75, config.getQueueAlertThreshold());
        assertEquals("0 0 12 * * ?", config.getCronExpression());
        assertEquals(5000L, config.getFixedRate());
        assertEquals(6000L, config.getFixedDelay());
    }

    @Test
    @DisplayName("Should get correct prefix from TaskType")
    void shouldGetCorrectPrefixFromTaskType() {
        assertEquals("CPU", TaskType.CPU_BOUND.getPrefix());
        assertEquals("IO", TaskType.IO_BOUND.getPrefix());
        assertEquals("HYBRID", TaskType.HYBRID.getPrefix());
        assertEquals("CRON", TaskType.SCHEDULED.getPrefix());
        assertEquals("BATCH", TaskType.BATCH.getPrefix());
    }
}
