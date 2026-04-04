# task-registration Specification

## Purpose
TBD - created by archiving change rethread-pool-classification. Update Purpose after archive.
## Requirements
### Requirement: Unified Registration Interface

The system SHALL provide a unified registration interface:

```java
public void register(TaskConfig config, ITaskProcessor<?> processor)
```

#### Scenario: Successful registration
- **WHEN** a task processor is registered with valid configuration
- **THEN** the task SHALL be registered successfully
- **AND** the task SHALL use the pre-configured pool for its type

#### Scenario: Duplicate registration
- **WHEN** a task with the same name is already registered
- **THEN** the system SHALL throw `IllegalArgumentException`
- **AND** the existing registration SHALL remain unchanged

### Requirement: Task Configuration Validation

The system SHALL validate task configuration before registration:

- Task name is required and must be non-blank
- Task type is required
- Queue alert threshold must be 0-100 (if specified)
- CRON tasks require schedule configuration

#### Scenario: Missing task name
- **WHEN** task name is null or blank
- **THEN** validation SHALL fail
- **AND** throw `IllegalArgumentException`

#### Scenario: Missing task type
- **WHEN** task type is null
- **THEN** validation SHALL fail
- **AND** throw `IllegalArgumentException`

#### Scenario: CRON task without schedule
- **WHEN** a task with type `SCHEDULED` has no cron expression, fixed rate, or fixed delay
- **THEN** validation SHALL fail
- **AND** throw `IllegalArgumentException`

### Requirement: Task Type Selection

The task processor SHALL declare its task type:

```java
TaskType getTaskType();
```

#### Scenario: CPU-bound processor
- **WHEN** a processor performs compute-intensive operations
- **THEN** `getTaskType()` SHALL return `TaskType.CPU_BOUND`

#### Scenario: IO-bound processor
- **WHEN** a processor performs I/O operations
- **THEN** `getTaskType()` SHALL return `TaskType.IO_BOUND`

### Requirement: Removed Priority Concept

The system SHALL NOT support task priority:

- `TaskPriority` enum has been removed
- `getPriority()` method has been removed from `ITaskProcessor`
- `priority` field has been removed from `TaskConfig`

#### Scenario: Registration without priority
- **WHEN** a task is registered
- **THEN** no priority configuration is required or accepted
- **AND** all tasks are treated equally by the scheduler

