# Spec: Task Isolation

## ADDED Requirements

### Requirement: Physical Thread Pool Isolation
Tasks of different types must execute in separate thread pools to prevent interference.

#### Scenario: Initialization task isolation
**Given** a task with type `INIT` is registered
**When** task is submitted for execution
**Then** it executes in a dedicated short-lived pool
**And** pool has `core=1`, `max=CPU cores`
**And** pool is destroyed after all init tasks complete

#### Scenario: Periodic task isolation
**Given** a task with type `CRON` is registered
**When** task is scheduled with cron expression
**Then** it executes in `ThreadPoolTaskScheduler`
**And** scheduling supports FixedRate, FixedDelay, and Cron

#### Scenario: High-frequency task isolation
**Given** a task with type `HIGH_FREQ` is registered
**When** task is submitted for execution
**Then** it executes in fixed high-performance pool
**And** pool has bounded blocking queue
**And** queue capacity is configurable

#### Scenario: Background task isolation
**Given** a task with type `BACKGROUND` is registered
**When** task is submitted for execution
**Then** it executes in shared low-priority pool
**And** pool has `core=2`, `max=4`
**And** multiple background tasks share same pool

---

### Requirement: Pool Configuration Per Type
Each task type has default pool configuration that can be overridden.

#### Scenario: Default pool configuration
**Given** no custom config is provided for a task
**When** task is registered
**Then** pool uses default settings for its type

| Type | Core | Max | Queue |
|------|------|-----|-------|
| INIT | 1 | CPU | 0 |
| CRON | 4 | 4 | 0 |
| HIGH_FREQ | CPU*2 | CPU*4 | 10000 |
| BACKGROUND | 2 | 4 | 100 |

#### Scenario: Custom pool configuration
**Given** a developer provides custom `TaskConfig`
**When** task is registered
**Then** pool uses custom settings
**And** custom settings override defaults

---

### Requirement: Cross-Type Isolation Guarantee
Task execution in one pool must not block tasks in other pools.

#### Scenario: Long-running background task
**Given** a background task takes 5 minutes to execute
**When** a high-frequency alert task is submitted
**Then** alert task executes immediately in its own pool
**And** background task execution has no impact on alert timing

#### Scenario: Pool exhaustion in one type
**Given** all threads in HIGH_FREQ pool are busy
**When** an INIT task is submitted
**Then** INIT task executes in its own pool
**And** no blocking occurs between pools