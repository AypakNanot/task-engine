# Task Type Classification

## Capability Overview

Task types classify tasks based on their resource characteristics to enable optimal thread pool configuration and resource isolation.

## ADDED Requirements

### Requirement: Five Task Types

The system SHALL provide five task types based on resource characteristics:

1. **CPU_BOUND** - CPU-intensive tasks with minimal I/O operations
2. **IO_BOUND** - I/O-intensive tasks with significant wait time
3. **HYBRID** - Mixed tasks with both computation and I/O
4. **SCHEDULED** - Time-triggered tasks (cron expressions, fixed rate/delay)
5. **BATCH** - Bulk processing tasks with large volumes

#### Scenario: CPU_BOUND task registration
- **WHEN** a task performs compute-intensive operations (encryption, compression, complex calculations)
- **THEN** the task MUST be registered with `TaskType.CPU_BOUND`
- **AND** the thread pool uses core count-based sizing (core=CPUs, max=CPUs*2)

#### Scenario: IO_BOUND task registration
- **WHEN** a task performs I/O operations (network calls, file operations, database queries)
- **THEN** the task MUST be registered with `TaskType.IO_BOUND`
- **AND** the thread pool uses high concurrency sizing (core=16, max=64)

#### Scenario: HYBRID task registration
- **WHEN** a task contains both computation and I/O but cannot be split
- **THEN** the task MUST be registered with `TaskType.HYBRID`
- **AND** the thread pool uses balanced sizing (core=8, max=16)

#### Scenario: SCHEDULED task registration
- **WHEN** a task is triggered by cron expression or fixed rate/delay
- **THEN** the task MUST be registered with `TaskType.SCHEDULED`
- **AND** the thread pool uses fixed sizing with no queue (core=4, max=4, queue=0)

#### Scenario: BATCH task registration
- **WHEN** a task processes large volumes of data in bulk
- **THEN** the task MUST be registered with `TaskType.BATCH`
- **AND** the thread pool uses low-priority sizing with large queue (core=2, max=4, queue=10000)

### Requirement: Task Type Prefix

Each task type SHALL have a unique prefix for thread naming:

| Type | Prefix |
|------|--------|
| CPU_BOUND | `CPU` |
| IO_BOUND | `IO` |
| HYBRID | `HYBRID` |
| SCHEDULED | `CRON` |
| BATCH | `BATCH` |

#### Scenario: Thread name generation
- **WHEN** a task is executed
- **THEN** the thread name SHALL follow the format `{prefix}-{taskName}-{id}`
- **EXAMPLE** `CPU-ImageCompressor-3`

### Requirement: Mixed Task Decomposition

For tasks that involve both CPU and I/O operations, developers SHALL decompose them into separate subtasks:

- CPU subtasks submitted to `CPU_BOUND` pool
- I/O subtasks submitted to `IO_BOUND` pool

#### Scenario: Image processing pipeline
- **WHEN** processing requires image compression (CPU) and network upload (I/O)
- **THEN** the developer SHALL create two separate task processors
- **AND** compose them at the application layer

#### Scenario: Data export workflow
- **WHEN** exporting requires database query (I/O) and file generation (CPU)
- **THEN** the developer SHALL create separate tasks for each phase
- **AND** coordinate via application-level orchestration

### Requirement: Default Thread Pool Parameters

Each task type SHALL have default thread pool parameters based on CPU count:

| Type | Core | Max | Queue | Rationale |
|------|------|-----|-------|-----------|
| CPU_BOUND | CPUs | CPUs*2 | 100 | Minimize context switching |
| IO_BOUND | 16 | 64 | 1000 | Absorb I/O wait time |
| HYBRID | 8 | 16 | 500 | Balanced configuration |
| SCHEDULED | 4 | 4 | 0 | No queuing for scheduled tasks |
| BATCH | 2 | 4 | 10000 | Low priority with large buffer |

#### Scenario: Automatic CPU-based calculation
- **WHEN** the system has 8 CPU cores
- **THEN** `CPU_BOUND` default core size SHALL be 8
- **AND** `CPU_BOUND` default max size SHALL be 16

#### Scenario: Configuration override
- **WHEN** application.yml specifies custom pool sizes
- **THEN** the configured values SHALL override defaults
- **BUT** the global max threads limit SHALL still apply
