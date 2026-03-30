# Task Engine

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A high-performance unified task processing center for Spring Boot applications, providing standardized thread pool management, real-time monitoring, dynamic scaling, and task isolation capabilities.

## Table of Contents

- [Background](#background)
- [Features](#features)
- [Performance](#performance)
- [Quick Start](#quick-start)
- [Task Types](#task-types)
- [Configuration](#configuration)
- [REST API](#rest-api)
- [Architecture](#architecture)
- [Best Practices](#best-practices)
- [Documentation](#documentation)

## Background

### Problem Statement

In enterprise applications, developers often create thread pools independently, leading to:

- **Resource Waste**: Excessive idle threads consuming memory
- **No Monitoring**: Lack of visibility into task execution status
- **Memory Issues**: Potential Direct Memory OOM from unbounded pools
- **Inconsistent Standards**: Different rejection policies, naming conventions

### Solution

Task Engine provides a centralized task processing framework that:

- Standardizes thread pool creation and management
- Provides real-time monitoring via REST API
- Implements dynamic scaling based on load
- Ensures task isolation by type

## Features

| Feature | Description |
|---------|-------------|
| **Task Isolation** | 4 task types with physically isolated thread pools |
| **Real-time Monitoring** | QPS, response time, success/failure counts, queue depth |
| **Queue Alerts** | Threshold-based monitoring with automatic logging |
| **Dynamic Scaling** | Auto scale up/down thread pools based on queue pressure |
| **Graceful Shutdown** | 30-second timeout for task compensation |
| **Context Propagation** | TraceID transfer across async boundaries via MDC (Slf4j) |
| **Health Indicator** | Spring Boot Actuator integration |
| **REST API** | Full CRUD operations for monitoring and configuration |

## Performance

### 15-Minute Sustained Load Test

| Metric | Result |
|--------|--------|
| **Overall QPS** | **250,874 tasks/sec** |
| **Peak Interval QPS** | 293,646 tasks/sec |
| **Total Tasks** | 227,075,682 |
| **Success Rate** | **100%** |
| **Avg Response Time** | 1 ms |
| **Peak Threads** | 392 |
| **Test Duration** | 15 minutes |

### Multi-Task Parallel Test

| Task Type | QPS | Tasks | Avg RT | Peak Threads |
|-----------|-----|-------|--------|--------------|
| HIGH_FREQ | 105,439 | 6.75M | 1ms | 120 |
| BACKGROUND | 186 | 11,940 | 50ms | 12 |
| INIT | 539 | 34,492 | 10ms | 8 |

> Performance tested on: 12 CPU cores, 200 submitter threads, 100K queue capacity

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.aypak</groupId>
    <artifactId>task-engine</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. Implement Task Processor

```java
import com.aypak.engine.task.core.ITaskProcessor;
import com.aypak.engine.task.core.TaskPriority;
import com.aypak.engine.task.core.TaskType;

public class AlertProcessor implements ITaskProcessor<String> {

    @Override
    public String getTaskName() {
        return "AlertProcessor";
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.HIGH_FREQ;
    }

    @Override
    public TaskPriority getPriority() {
        return TaskPriority.HIGH;
    }

    @Override
    public void process(String payload) {
        // Your business logic here
        sendAlert(payload);
    }

    @Override
    public void onSuccess(String payload) {
        log.info("Alert sent successfully: {}", payload);
    }

    @Override
    public void onFailure(String payload, Throwable error) {
        log.error("Failed to send alert: {}", payload, error);
    }
}
```

### 3. Register and Execute

```java
@Service
public class AlertService {

    @Autowired
    private TaskEngine taskEngine;

    @PostConstruct
    public void init() {
        // Register task processor with configuration
        taskEngine.register(TaskConfig.builder()
            .taskName("AlertProcessor")
            .taskType(TaskType.HIGH_FREQ)
            .priority(TaskPriority.HIGH)
            .corePoolSize(16)
            .maxPoolSize(32)
            .queueCapacity(10000)
            .queueAlertThreshold(80)           // Alert at 80% queue capacity
            .rejectionPolicy(RejectionPolicy.CALLER_RUNS)
            .build(), new AlertProcessor());
    }

    public void sendAlert(String message) {
        taskEngine.execute("AlertProcessor", message);
    }
}
```

### 4. Configure (Optional)

```yaml
# application.yml
task-engine:
  global-max-threads: 200      # Maximum total threads across all pools
  scale-factor: 2              # Threads to add/remove per scaling event
  scale-up-threshold: 80       # Queue % to trigger scale-up
  idle-timeout: 60000          # ms before scale-down
  shutdown-timeout: 30         # seconds for graceful shutdown
```

## Task Types

Task Engine supports 4 task types with physical isolation:

| Type | Description | Default Pool | Use Case | Priority |
|------|-------------|--------------|----------|----------|
| `INIT` | One-time initialization | core=1, max=CPU | App startup tasks | HIGH |
| `CRON` | Scheduled/periodic tasks | ThreadPoolTaskScheduler | Data collection, reports | MEDIUM |
| `HIGH_FREQ` | High QPS streaming | core=CPU*2, max=CPU*4, queue=10000 | Alerts, events, messages | HIGH |
| `BACKGROUND` | Low priority maintenance | core=2, max=4, queue=100 | Log cleanup, archival | LOW |

### Task Type Selection Guide

```
Is it a startup task? → INIT
Is it scheduled (Cron)? → CRON
Is QPS > 1000/sec? → HIGH_FREQ
Is it low priority? → BACKGROUND
Otherwise → HIGH_FREQ (default)
```

## Configuration

### TaskConfig Builder

```java
TaskConfig config = TaskConfig.builder()
    .taskName("MyTask")                    // Required: unique name
    .taskType(TaskType.HIGH_FREQ)          // Required: task type
    .priority(TaskPriority.HIGH)           // Required: priority
    .corePoolSize(16)                      // Core threads
    .maxPoolSize(32)                       // Maximum threads
    .queueCapacity(10000)                  // Queue size
    .queueAlertThreshold(80)               // Alert threshold %
    .rejectionPolicy(RejectionPolicy.CALLER_RUNS)
    .build();
```

### Rejection Policies

| Policy | Behavior | Use Case |
|--------|----------|----------|
| `ABORT_WITH_ALERT` | Drop task, log error | Critical alerts where loss is unacceptable |
| `CALLER_RUNS` | Execute in caller thread | Ensure task execution (cleanup jobs) |
| `DISCARD_OLDEST` | Remove oldest, add new | Non-critical data (metrics, logs) |
| `BLOCK_WAIT` | Block until space available | Guaranteed delivery |

### Dynamic Configuration

Update pool settings at runtime:

```java
// Via Java API
taskEngine.updateConfig("AlertProcessor", DynamicConfig.builder()
    .corePoolSize(20)
    .maxPoolSize(40)
    .build());

// Via REST API
PUT /monitor/task/config/AlertProcessor
{
    "corePoolSize": 20,
    "maxPoolSize": 40
}
```

## REST API

### Get All Task Status

```bash
GET /monitor/task/status
```

Response:
```json
{
  "AlertProcessor": {
    "taskName": "AlertProcessor",
    "taskType": "HIGH_FREQ",
    "currentQps": 105439.2,
    "avgResponseTime": 1,
    "successCount": 6752831,
    "failureCount": 0,
    "queueDepth": 0,
    "queueCapacity": 5000,
    "activeThreads": 0,
    "peakThreads": 120,
    "currentMaxPoolSize": 118
  }
}
```

### Get Specific Task Status

```bash
GET /monitor/task/status/{taskName}
```

### Update Pool Configuration

```bash
PUT /monitor/task/config/{taskName}
Content-Type: application/json

{
  "corePoolSize": 20,
  "maxPoolSize": 40
}
```

### Reset Metrics

```bash
DELETE /monitor/task/metrics/{taskName}  # Reset specific task
DELETE /monitor/task/metrics             # Reset all tasks
```

### Health Check

```bash
GET /actuator/health
```

Response includes task engine health:
```json
{
  "status": "UP",
  "components": {
    "taskEngine": {
      "status": "UP",
      "details": {
        "totalTasks": 3,
        "healthyTasks": 3,
        "warningTasks": 0,
        "failedTasks": 0
      }
    }
  }
}
```

## Architecture

### Package Structure

```
com.aypak.taskengine
├── core/               # Core interfaces and enums
│   ├── TaskType.java       # Task type enumeration
│   ├── TaskPriority.java   # Priority levels
│   ├── RejectionPolicy.java# Rejection strategies
│   ├── ITaskProcessor.java # Task processor interface
│   ├── TaskConfig.java     # Configuration builder
│   ├── TaskContext.java    # Execution context with MDC
│   └── DynamicConfig.java  # Runtime config updates
├── executor/           # Thread pool management
│   ├── TaskEngine.java     # Main interface
│   ├── TaskEngineImpl.java # Implementation
│   ├── TaskExecutor.java   # Pool wrapper
│   ├── TaskRegistry.java   # Task registration
│   ├── TaskThreadPoolFactory.java  # Pool creation
│   ├── DynamicScaler.java  # Auto-scaling logic
│   └── NamedThreadFactory.java      # Thread naming
├── monitor/            # Metrics and monitoring
│   ├── TaskMetrics.java    # Thread-safe metrics (LongAdder)
│   ├── MetricsCollector.java  # Metrics aggregation
│   ├── QueueMonitor.java   # Queue depth monitoring
│   └── TaskStatsResponse.java  # API response
├── api/                # REST endpoints
│   └── TaskMonitorController.java
└── config/             # Spring configuration
    ├── TaskEngineProperties.java
    ├── TaskEngineAutoConfiguration.java
    └── TaskEngineHealthIndicator.java
```

### Key Design Decisions

1. **LongAdder for High-Contention Counters**: Better performance than AtomicLong under high write contention
2. **EWMA for Response Time**: Exponential Weighted Moving Average (alpha=0.3) for smooth metrics
3. **Physical Isolation**: Each task type gets its own thread pool to prevent cross-contamination
4. **Graceful Shutdown**: 30-second timeout allows in-flight tasks to complete

### Thread Naming Convention

All threads follow: `{TaskType}-{TaskName}-{ThreadId}`

Examples:
- `HIGH_FREQ-AlertProcessor-15`
- `CRON-DataCollector-2`
- `BACKGROUND-LogCleaner-3`

## Best Practices

### 1. Choose the Right Task Type

```java
// Alerts need low latency → HIGH_FREQ
taskEngine.register(config(HIGH_FREQ, queue=10000), new AlertProcessor());

// Daily cleanup can wait → BACKGROUND
taskEngine.register(config(BACKGROUND, queue=100), new CleanupProcessor());

// Startup initialization → INIT
taskEngine.register(config(INIT), new StartupProcessor());
```

### 2. Configure Appropriate Queue Size

```java
// High-throughput: large queue, CALLER_RUNS policy
.queueCapacity(100000)
.rejectionPolicy(RejectionPolicy.CALLER_RUNS)

// Low-latency: small queue, fast failure
.queueCapacity(1000)
.rejectionPolicy(RejectionPolicy.ABORT_WITH_ALERT)
```

### 3. Monitor Queue Depth

Set appropriate alert thresholds:

```java
.queueAlertThreshold(80)  // Alert at 80% capacity
```

### 4. Use TraceID for Debugging

```java
// Set traceId before executing
MDC.put("traceId", UUID.randomUUID().toString());
taskEngine.execute("MyTask", payload);
// Task Engine automatically propagates MDC context
```

### 5. Handle Failures Gracefully

```java
@Override
public void onFailure(MyPayload payload, Throwable error) {
    log.error("Task failed for {}: {}", payload.getId(), error.getMessage());
    // Optionally: retry, send to dead-letter queue, alert
}
```

## Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=TaskEngineStressTest

# Run 15-minute stress test
mvn test -Dtest=TaskEngineLongStressTest

# Run multi-task parallel test
mvn test -Dtest=TaskEngineMultiTaskParallelTest
```

## Documentation

For more detailed information, see the full documentation:

- **[TaskEngine Documentation](docs/TASKENGINE.md)** - Complete guide for TaskEngine including architecture, configuration, monitoring, and best practices
- **[AlarmEngine Documentation](docs/ALARMENGINE.md)** - Complete guide for AlarmEngine including 9-node pipeline, sharding strategy, batch persistence, and performance optimization

## Requirements

- Java 17+
- Spring Boot 3.x
- Maven 3.6+

## License

MIT License

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.