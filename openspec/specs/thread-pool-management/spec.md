# thread-pool-management Specification

## Purpose
TBD - created by archiving change rethread-pool-classification. Update Purpose after archive.
## Requirements
### Requirement: Pre-configured Thread Pools

The system SHALL create all thread pools at application startup based on configuration:

- Five pre-configured pool types (CPU_BOUND, IO_BOUND, HYBRID, SCHEDULED, BATCH)
- Configuration loaded from `application.yml`
- No runtime thread pool creation allowed

#### Scenario: Application startup
- **WHEN** the Spring Boot application starts
- **THEN** all five thread pools SHALL be created
- **AND** configuration SHALL be loaded from `application.yml`

#### Scenario: Missing configuration
- **WHEN** a pool type is not configured in `application.yml`
- **THEN** the system SHALL use default parameters
- **AND** log a warning with the default values being used

### Requirement: Global Thread Limit

The system SHALL enforce a global maximum thread limit across all pools:

- Default: 200 threads
- Configurable via `task-engine.global-max-threads`
- Sum of all pool max sizes SHALL NOT exceed this limit

#### Scenario: Configuration validation
- **WHEN** the sum of configured max sizes exceeds `global-max-threads`
- **THEN** the system SHALL fail fast at startup
- **AND** log an error with the configuration conflict

#### Scenario: Thread limit enforcement
- **WHEN** a new pool is registered
- **THEN** the system SHALL check if adding this pool would exceed the global limit
- **AND** reject the registration if the limit would be exceeded

### Requirement: Pool Isolation

Each task type SHALL have an isolated thread pool:

- Separate `ThreadPoolExecutor` instances per type
- No shared queues between pools
- Independent metrics collection

#### Scenario: Pool isolation verification
- **WHEN** CPU_BOUND pool is saturated with tasks
- **THEN** IO_BOUND pool SHALL still have available threads
- **AND** tasks in IO_BOUND pool SHALL NOT be affected

#### Scenario: Independent scaling
- **WHEN** CPU_BOUND pool triggers scale-up
- **THEN** IO_BOUND pool max size SHALL NOT change
- **AND** each pool scales independently

### Requirement: Configuration Structure

Thread pool configuration SHALL follow a standard structure:

```yaml
task-engine:
  global-max-threads: 200
  pools:
    cpu-bound:
      core-size: 4
      max-size: 8
      queue-capacity: 100
    io-bound:
      core-size: 16
      max-size: 64
      queue-capacity: 1000
```

#### Scenario: Full configuration
- **WHEN** all pool parameters are specified
- **THEN** the system SHALL use the configured values
- **AND** validate against global limits

#### Scenario: Partial configuration
- **WHEN** only `core-size` is specified
- **THEN** the system SHALL use defaults for `max-size` and `queue-capacity`
- **AND** log the effective configuration

### Requirement: No Custom Thread Pool Creation

Developers SHALL NOT be allowed to create custom thread pools:

- Thread pools are created only by `TaskThreadPoolFactory`
- No public API for creating new pools
- All tasks must use pre-configured pool types

#### Scenario: Task registration with valid type
- **WHEN** a task is registered with a valid `TaskType`
- **THEN** the task SHALL use the pre-configured pool for that type
- **AND** no new pool is created

#### Scenario: Attempted custom pool creation
- **WHEN** code attempts to create a new thread pool directly
- **THEN** the operation SHALL fail at compile time (no API available)
- **OR** throw `IllegalStateException` at runtime if bypassed

