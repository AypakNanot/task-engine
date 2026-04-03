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
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.MEDIUM)
                .build();

        assertNotNull(config);
        assertEquals("TestTask", config.getTaskName());
        assertEquals(TaskType.HIGH_FREQ, config.getTaskType());
        assertEquals(TaskPriority.MEDIUM, config.getPriority());
    }

    @Test
    @DisplayName("Should validate successfully with all required fields")
    void shouldValidateSuccessfullyWithAllRequiredFields() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.MEDIUM)
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should throw exception when taskName is null")
    void shouldThrowExceptionWhenTaskNameIsNull() {
        TaskConfig config = TaskConfig.builder()
                .taskName(null)
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.MEDIUM)
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
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.MEDIUM)
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
                .priority(TaskPriority.MEDIUM)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("taskType"));
    }

    @Test
    @DisplayName("Should throw exception when priority is null")
    void shouldThrowExceptionWhenPriorityIsNull() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(TaskType.HIGH_FREQ)
                .priority(null)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("priority"));
    }

    @Test
    @DisplayName("Should throw exception when queueAlertThreshold is negative")
    void shouldThrowExceptionWhenQueueAlertThresholdIsNegative() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.MEDIUM)
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
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.MEDIUM)
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
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.MEDIUM)
                .queueAlertThreshold(0)
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should accept queueAlertThreshold of 100")
    void shouldAcceptQueueAlertThresholdOf100() {
        TaskConfig config = TaskConfig.builder()
                .taskName("TestTask")
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.MEDIUM)
                .queueAlertThreshold(100)
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should throw exception when CRON task has no schedule config")
    void shouldThrowExceptionWhenCronTaskHasNoScheduleConfig() {
        TaskConfig config = TaskConfig.builder()
                .taskName("CronTask")
                .taskType(TaskType.CRON)
                .priority(TaskPriority.MEDIUM)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("CRON"));
    }

    @Test
    @DisplayName("Should validate CRON task with cronExpression")
    void shouldValidateCronTaskWithCronExpression() {
        TaskConfig config = TaskConfig.builder()
                .taskName("CronTask")
                .taskType(TaskType.CRON)
                .priority(TaskPriority.MEDIUM)
                .cronExpression("0 0 12 * * ?")
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should validate CRON task with fixedRate")
    void shouldValidateCronTaskWithFixedRate() {
        TaskConfig config = TaskConfig.builder()
                .taskName("CronTask")
                .taskType(TaskType.CRON)
                .priority(TaskPriority.MEDIUM)
                .fixedRate(5000L)
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should validate CRON task with fixedDelay")
    void shouldValidateCronTaskWithFixedDelay() {
        TaskConfig config = TaskConfig.builder()
                .taskName("CronTask")
                .taskType(TaskType.CRON)
                .priority(TaskPriority.MEDIUM)
                .fixedDelay(5000L)
                .build();

        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should validate non-CRON tasks without schedule config")
    void shouldValidateNonCronTasksWithoutScheduleConfig() {
        TaskConfig initConfig = TaskConfig.builder()
                .taskName("InitTask")
                .taskType(TaskType.INIT)
                .priority(TaskPriority.HIGH)
                .build();

        TaskConfig highFreqConfig = TaskConfig.builder()
                .taskName("HighFreqTask")
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.MEDIUM)
                .build();

        TaskConfig backgroundConfig = TaskConfig.builder()
                .taskName("BackgroundTask")
                .taskType(TaskType.BACKGROUND)
                .priority(TaskPriority.LOW)
                .build();

        assertDoesNotThrow(() -> initConfig.validate());
        assertDoesNotThrow(() -> highFreqConfig.validate());
        assertDoesNotThrow(() -> backgroundConfig.validate());
    }

    @Test
    @DisplayName("Should create config with all optional fields")
    void shouldCreateConfigWithAllOptionalFields() {
        TaskConfig config = TaskConfig.builder()
                .taskName("FullConfigTask")
                .taskType(TaskType.HIGH_FREQ)
                .priority(TaskPriority.HIGH)
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
        assertEquals(TaskType.HIGH_FREQ, config.getTaskType());
        assertEquals(TaskPriority.HIGH, config.getPriority());
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
        assertEquals("INIT", TaskType.INIT.getPrefix());
        assertEquals("CRON", TaskType.CRON.getPrefix());
        assertEquals("HIGH_FREQ", TaskType.HIGH_FREQ.getPrefix());
        assertEquals("BACKGROUND", TaskType.BACKGROUND.getPrefix());
    }

    @Test
    @DisplayName("Should get correct level from TaskPriority")
    void shouldGetCorrectLevelFromTaskPriority() {
        assertEquals(3, TaskPriority.HIGH.getLevel());
        assertEquals(2, TaskPriority.MEDIUM.getLevel());
        assertEquals(1, TaskPriority.LOW.getLevel());
    }
}
