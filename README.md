# Task Engine

A unified task processing center for Spring Boot applications, providing standardized thread pool management, monitoring, and dynamic scaling capabilities.

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)

## Features

- **Task Isolation**: 4 task types with dedicated thread pools (INIT, CRON, HIGH_FREQ, BACKGROUND)
- **Real-time Monitoring**: QPS, response time, success/failure counts via REST API
- **Queue Alerts**: Threshold-based queue depth monitoring with logging
- **Dynamic Scaling**: Auto scale up/down thread pools based on load
- **Graceful Shutdown**: 30-second timeout for task compensation
- **Context Propagation**: TraceID transfer across async boundaries via MDC
- **Health Indicator**: Spring Boot Actuator integration

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
        System.out.println("Processing: " + payload);
    }

    @Override
    public void onFailure(String payload, Throwable error) {
        // Handle failure
        log.error("Task failed for: {}", payload, error);
    }
}
```

### 3. Register and Execute

```java
@Service
public class MyService {

    @Autowired
    private TaskEngine taskEngine;

    @PostConstruct
    public void init() {
        // Register task
        taskEngine.register(TaskConfig.builder()
            .taskName("AlertProcessor")
            .taskType(TaskType.HIGH_FREQ)
            .priority(TaskPriority.HIGH)
            .corePoolSize(16)
            .maxPoolSize(32)
            .queueCapacity(10000)
            .rejectionPolicy(RejectionPolicy.CALLER_RUNS)
            .queueAlertThreshold(80)
            .build(), new AlertProcessor());
    }

    public void sendAlert(String message) {
        // Execute task
        taskEngine.execute("AlertProcessor", message);
    }
}
```

## Task Types

| Type | Description | Pool Config | Use Case |
|------|-------------|-------------|----------|
| `INIT` | One-time initialization | core=1, max=CPU | Startup tasks |
| `CRON` | Scheduled tasks | ThreadPoolTaskScheduler | Periodic jobs |
| `HIGH_FREQ` | High QPS streaming | core=CPU*2, max=CPU*4, queue=10000 | Alerts, events |
| `BACKGROUND` | Low priority maintenance | core=2, max=4, queue=100 | Cleanup, archival |

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
    "currentQps": 1830.8,
    "avgResponseTime": 11,
    "successCount": 16142,
    "failureCount": 0,
    "queueDepth": 1913,
    "activeThreads": 16,
    "peakThreads": 16,
    "currentMaxPoolSize": 32,
    "queueCapacity": 10000,
    "queueUtilization": 19.13
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

## Configuration

```yaml
# application.yml
task-engine:
  global-max-threads: 200      # Maximum total threads
  scale-factor: 2               # Threads to add/remove per scaling event
  scale-up-threshold: 80        # Queue % to trigger scale-up
  idle-timeout: 60000           # ms before scale-down
  shutdown-timeout: 30          # seconds for graceful shutdown
  qps-window-size: 60000        # ms for QPS calculation
  queue-monitor-interval: 100   # ms between queue checks

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

## Rejection Policies

| Policy | Behavior | Use Case |
|--------|----------|----------|
| `ABORT_WITH_ALERT` | Drop task, log error | Critical alerts |
| `CALLER_RUNS` | Execute in caller thread | Cleanup tasks |
| `DISCARD_OLDEST` | Remove oldest, add new | Non-critical data |
| `BLOCK_WAIT` | Block until space available | Guaranteed delivery |

## Performance

Stress test results (5000 tasks, 50 concurrent threads):

| Metric | Value |
|--------|-------|
| Submit rate | 454,545 tasks/sec |
| Processing QPS | 1,830+ |
| Avg response time | 11-14ms |
| Success rate | 100% |

## Health Check

```bash
GET /actuator/health
```

Response includes task engine status:
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

## Thread Naming

All threads follow the format: `{TaskType}-{TaskName}-{ThreadId}`

Examples:
- `HIGH_FREQ-AlertProcessor-1`
- `CRON-DataCollector-2`
- `BACKGROUND-LogCleaner-3`

## License

MIT License

## Contributing

Pull requests are welcome. For major changes, please open an issue first.