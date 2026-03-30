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

## Tech Stack

- **Java 17** - Target JVM
- **Spring Boot 3.5.x** - Framework
- **Maven** - Build tool
- **Lombok** - Boilerplate reduction
- **JUnit 5** - Testing

## Project Structure

```
com.aypak.taskengine
├── core/               # Core interfaces and enums
│   ├── TaskType.java       # 4 task types: INIT, CRON, HIGH_FREQ, BACKGROUND
│   ├── TaskPriority.java   # HIGH, MEDIUM, LOW
│   ├── RejectionPolicy.java# ABORT_WITH_ALERT, CALLER_RUNS, DISCARD_OLDEST, BLOCK_WAIT
│   ├── ITaskProcessor.java # Main processor interface
│   ├── TaskConfig.java     # Immutable configuration (Builder pattern)
│   ├── TaskContext.java    # Execution context with MDC propagation
│   └── DynamicConfig.java  # Runtime configuration updates
├── executor/           # Thread pool management
│   ├── TaskEngine.java     # Public interface
│   ├── TaskEngineImpl.java # Main orchestrator
│   ├── TaskExecutor.java   # ThreadPoolExecutor wrapper
│   ├── TaskRegistry.java   # ConcurrentHashMap<String, TaskExecutor>
│   ├── TaskThreadPoolFactory.java  # Creates ThreadPoolTaskExecutor
│   ├── DynamicScaler.java  # Scheduled scaling logic
│   └── NamedThreadFactory.java      # Thread naming: {Type}-{Name}-{Id}
├── monitor/            # Metrics and monitoring
│   ├── TaskMetrics.java    # LongAdder-based thread-safe metrics
│   ├── MetricsCollector.java  # QPS calculation, EWMA
│   ├── QueueMonitor.java   # Queue depth monitoring (100ms interval)
│   └── TaskStatsResponse.java  # REST API response DTO
├── api/                # REST endpoints
│   └── TaskMonitorController.java  # /monitor/task/*
└── config/             # Spring Boot configuration
    ├── TaskEngineProperties.java   # @ConfigurationProperties
    ├── TaskEngineAutoConfiguration.java
    └── TaskEngineHealthIndicator.java
```

## Key Patterns

### Task Types (Physical Isolation)

| Type | Pool Strategy | Thread Naming | Use Case |
|------|---------------|---------------|----------|
| `INIT` | core=1, max=CPU, queue=0 | INIT-{name}-{id} | One-time startup |
| `CRON` | ThreadPoolTaskScheduler | CRON-{name}-{id} | Scheduled jobs |
| `HIGH_FREQ` | core=CPU*2, max=CPU*4, queue=10000 | HIGH_FREQ-{name}-{id} | High QPS |
| `BACKGROUND` | core=2, max=4, queue=100 | BACKGROUND-{name}-{id} | Maintenance |

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

## Build Commands

```bash
# Compile
mvn compile

# Run all tests
mvn test

# Run specific test
mvn test -Dtest=TaskEngineStressTest

# Run 15-minute stress test
mvn test -Dtest=TaskEngineLongStressTest

# Package (skip tests)
mvn package -DskipTests

# Clean build
mvn clean install
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
    public TaskType getTaskType() { return TaskType.HIGH_FREQ; }

    @Override
    public TaskPriority getPriority() { return TaskPriority.HIGH; }

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

### Modifying Metrics

1. Add field to `TaskMetrics.java` (use LongAdder for counters)
2. Update `recordSuccess()` or add new recording method
3. Add getter method
4. Update `TaskStatsResponse.java` to include new metric
5. Update `MetricsCollector.java` if needed

### Adding New REST Endpoint

1. Add method in `TaskMonitorController.java`
2. Follow existing patterns for response types
3. Use `TaskEngine` interface methods

## Performance Tuning

### High QPS Scenario (100K+)

```java
TaskConfig.builder()
    .taskType(TaskType.HIGH_FREQ)
    .corePoolSize(cpuCount * 8)      // More core threads
    .maxPoolSize(cpuCount * 16)      // Allow scaling
    .queueCapacity(100000)           // Large queue
    .rejectionPolicy(RejectionPolicy.CALLER_RUNS)  // Never drop
    .build()
```

### Low Latency Scenario

```java
TaskConfig.builder()
    .taskType(TaskType.HIGH_FREQ)
    .corePoolSize(32)                // Fixed pool
    .maxPoolSize(32)                 // No scaling
    .queueCapacity(1000)             // Small queue
    .rejectionPolicy(RejectionPolicy.ABORT_WITH_ALERT)  // Fast failure
    .queueAlertThreshold(50)         // Early warning
    .build()
```

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

### 3. Queue Alert Threshold Calculation

Threshold is calculated against `queueCapacity - 1`:

```java
// Alert when queueDepth > (capacity * threshold / 100)
int threshold = (int)(queueCapacity * queueAlertThreshold / 100.0);
```

## Testing

### Test Classes

| Test | Purpose | Duration |
|------|---------|----------|
| `TaskEngineStressTest` | Basic stress test | ~10s |
| `TaskEngineLongStressTest` | 15-minute sustained load | 15min |
| `TaskEngineMultiTaskParallelTest` | Multi-task isolation | ~70s |

### Test Configuration

Stress tests use:
- `cpuCount * 8` core threads
- `cpuCount * 16` max threads
- 100K queue capacity
- CALLER_RUNS rejection policy

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

## Dependencies

Key dependencies from pom.xml:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.13</version>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```