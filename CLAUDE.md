<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# CLAUDE.md

This file provides guidance to Claude Code when working with the Task Engine project.

## Project Overview

Task Engine is a high-performance unified task processing center for Spring Boot applications. It provides standardized thread pool management, real-time monitoring, dynamic scaling, and task isolation capabilities.

**Performance**: 250K+ QPS, 100% success rate under sustained load.

## Multi-Module Structure

```
task-engine/ (parent POM)
├── pom.xml (packaging: pom)
├── task-engine-core/          # Task Engine Core module
│   ├── pom.xml
│   └── src/
│       └── main/java/com/aypak/taskengine/
│           ├── core/          # Core interfaces and enums
│           ├── executor/      # Thread pool management
│           ├── monitor/       # Metrics and monitoring
│           ├── api/           # REST endpoints
│           ├── config/        # Spring Boot configuration
│           └── event/         # Task events
├── flow-engine/               # Flow Engine module
│   ├── pom.xml
│   └── src/
│       └── main/java/com/aypak/flowengine/
│           ├── core/          # Flow core classes
│           ├── dispatcher/    # Shard dispatcher and workers
│           └── monitor/       # Flow metrics
└── alarm-engine/              # Alarm Engine module
    ├── pom.xml
    └── src/
        └── main/java/com/aypak/alarmengine/
            ├── core/          # Alarm event classes
            ├── engine/        # Alarm engine implementation
            ├── nodes/         # Processing nodes
            ├── batch/         # Batch processing
            ├── monitor/       # Alarm metrics
            └── config/        # Alarm configuration
```

## Module Dependencies

- **task-engine-core**: No internal dependencies (standalone)
- **flow-engine**: No internal dependencies (standalone)
- **alarm-engine**: Depends on **flow-engine** (uses `RejectPolicy`)

## Package Names

| Module | Package |
|--------|---------|
| task-engine-core | `com.aypak.taskengine` |
| flow-engine | `com.aypak.flowengine` |
| alarm-engine | `com.aypak.alarmengine` |

## Build Commands

```bash
# Compile all modules
mvn clean compile

# Run all tests
mvn test

# Run specific test in a module
mvn test -pl task-engine-core -Dtest=TaskConfigTest

# Package all modules (skip tests)
mvn package -DskipTests

# Install all modules to local repository
mvn clean install

# Build specific module only
mvn compile -pl task-engine-core -am
```

## Task Engine Core Module

### Task Types (Resource-Based Classification)

| Type | Pool Strategy | Thread Naming | Use Case |
|------|---------------|---------------|----------|
| `CPU_BOUND` | core=CPUs, max=CPUs*2, queue=100 | CPU-{name}-{id} | Compute-intensive: encryption, compression |
| `IO_BOUND` | core=16, max=64, queue=1000 | IO-{name}-{id} | I/O operations: network, DB, file |
| `HYBRID` | core=8, max=16, queue=500 | HYBRID-{name}-{id} | Mixed workload |
| `SCHEDULED` | ThreadPoolTaskScheduler | CRON-{name}-{id} | Timed/cron tasks |
| `BATCH` | core=2, max=4, queue=10000 | BATCH-{name}-{id} | Bulk processing: data sync, import/export |

### Operation Guide

**Prohibited**:
- `new Thread()` - Direct thread creation
- `Executors.newXXX()` - May create unbounded pools
- `CompletableFuture.runAsync()` - Uses common pool
- Direct `ThreadPoolTaskExecutor` injection

**Required**:
- Always use `taskEngine.register(config, processor)`
- Always use `taskEngine.execute(taskName, payload)`
- Configure pools via `application.yml`

See [docs/OPERATION-GUIDE.md](docs/OPERATION-GUIDE.md) for complete rules.

### Thread Safety

- **LongAdder**: High-contention counters (successCount, failureCount)
- **AtomicLong**: Single-writer values (EWMA, windowStartTime)
- **AtomicInteger**: Pool state (queueDepth, activeThreads)
- **ConcurrentHashMap**: Task registry

### EWMA for Response Time

```java
// alpha = 0.3
updated = (long)(0.3 * newValue + 0.7 * current);
```

### MDC Context Propagation

```java
// Before execution
Map<String, String> contextMap = MDC.getCopyOfContextMap();

// In task thread
if (contextMap != null) {
    MDC.setContextMap(contextMap);
}
try {
    processor.process(payload);
} finally {
    MDC.clear();
}
```

## Configuration

### application.yml

```yaml
task-engine:
  global-max-threads: 200      # Maximum total threads
  scale-factor: 2              # Threads to add/remove per scaling event
  scale-up-threshold: 80       # Queue % to trigger scale-up
  idle-timeout: 60000          # ms before scale-down
  shutdown-timeout: 30         # seconds for graceful shutdown
  qps-window-size: 60000       # ms for QPS calculation window
  queue-monitor-interval: 100  # ms between queue checks

  pools:
    type1-init:
      core-size: 1
      max-size: 8
    type2-cron:
      core-size: 4
      max-size: 4
    type3-high-freq:
      core-size: 16
      max-size: 32
      queue-capacity: 10000
    type4-background:
      core-size: 2
      max-size: 4
      queue-capacity: 100
```

## REST Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/monitor/task/status` | GET | All task metrics |
| `/monitor/task/status/{name}` | GET | Specific task metrics |
| `/monitor/task/config/{name}` | PUT | Update pool config |
| `/monitor/task/metrics/{name}` | DELETE | Reset specific task metrics |
| `/monitor/task/metrics` | DELETE | Reset all task metrics |
| `/actuator/health` | GET | Health check with task engine status |

## Common Tasks

### Adding a New Task Processor

1. Create processor class implementing `ITaskProcessor<T>`
2. Register in `@PostConstruct` or configuration class
3. Configure appropriate TaskType and pool settings

```java
@Component
public class MyTaskProcessor implements ITaskProcessor<MyPayload> {

    @Override
    public String getTaskName() { return "MyTask"; }

    @Override
    public TaskType getTaskType() {
        // Choose based on task characteristics:
        // - CPU_BOUND: compute-intensive (encryption, compression)
        // - IO_BOUND: I/O operations (network, DB, file)
        // - HYBRID: mixed workload
        // - SCHEDULED: timed/cron tasks
        // - BATCH: bulk processing
        return TaskType.IO_BOUND;
    }

    @Override
    public void process(MyPayload payload) {
        // Business logic
    }

    @Override
    public void onFailure(MyPayload payload, Throwable error) {
        log.error("Task failed", error);
    }
}
```

**IMPORTANT**:
- Do NOT create threads directly (`new Thread()`, `Executors.newXXX()`)
- Do NOT inject ThreadPoolTaskExecutor directly
- ALWAYS use `taskEngine.register()` and `taskEngine.execute()`
- See [Operation Guide](docs/OPERATION-GUIDE.md) for detailed rules

## Known Issues & Solutions

### 1. LongAdder.get() doesn't exist

Use `.sum()` instead:

```java
// Wrong
metrics.getSuccessCount().get()

// Correct
metrics.getSuccessCount().sum()
```

### 2. ThreadPoolExecutor.setMaximumPoolSize validation

When updating pool sizes, set max before core:

```java
// Wrong - throws IllegalArgumentException if newCore > oldMax
executor.setCorePoolSize(newCore);
executor.setMaximumPoolSize(newMax);

// Correct
executor.setMaximumPoolSize(newMax);
executor.setCorePoolSize(newCore);
```

## Commit Convention

```
feat: add new feature
fix: bug fix
docs: documentation
refactor: code refactoring
test: adding tests
chore: maintenance
perf: performance improvement
```

**IMPORTANT**: Do NOT automatically commit code changes. Always ask for user confirmation before executing git commit or git push commands.
