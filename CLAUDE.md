# CLAUDE.md

This file provides guidance to Claude Code when working with this project.

## Project Overview

Task Engine is a unified task processing center for Spring Boot applications. It provides standardized thread pool management, monitoring, and dynamic scaling capabilities.

## Tech Stack

- **Java 17**
- **Spring Boot 3.5.x**
- **Maven** build
- **Lombok** for boilerplate reduction

## Project Structure

```
com.aypak.taskengine
├── core/           # Enums, interfaces, config classes
│   ├── TaskType.java
│   ├── TaskPriority.java
│   ├── RejectionPolicy.java
│   ├── ITaskProcessor.java
│   ├── TaskConfig.java
│   ├── TaskContext.java
│   └── DynamicConfig.java
├── executor/       # Thread pool management
│   ├── TaskEngine.java
│   ├── TaskEngineImpl.java
│   ├── TaskExecutor.java
│   ├── TaskRegistry.java
│   ├── TaskThreadPoolFactory.java
│   ├── DynamicScaler.java
│   └── NamedThreadFactory.java
├── monitor/        # Metrics and monitoring
│   ├── TaskMetrics.java
│   ├── MetricsCollector.java
│   ├── QueueMonitor.java
│   └── TaskStatsResponse.java
├── api/            # REST endpoints
│   └── TaskMonitorController.java
└── config/         # Spring configuration
    ├── TaskEngineProperties.java
    ├── TaskEngineAutoConfiguration.java
    └── TaskEngineHealthIndicator.java
```

## Key Patterns

### Task Types

| Type | Pool Strategy | Use Case |
|------|---------------|----------|
| `INIT` | Short-lived, core=1, max=CPU | One-time startup |
| `CRON` | ThreadPoolTaskScheduler | Periodic/scheduled |
| `HIGH_FREQ` | Fixed high-perf, queue=10000 | High QPS streaming |
| `BACKGROUND` | Shared low-priority | Maintenance |

### Thread Naming

Format: `{TaskType}-{TaskName}-{ThreadId}`

Example: `HIGH_FREQ-AlertProcessor-15`

### Metrics Collection

- Uses `AtomicLong`/`AtomicInteger` for thread-safety
- EWMA for average response time (alpha=0.3)
- Sliding window for QPS calculation (default 60s)

## Build Commands

```bash
# Compile
mvn compile

# Run tests
mvn test

# Run specific test
mvn test -Dtest=TaskEngineStressTest

# Package
mvn package -DskipTests
```

## Configuration

Key properties in `application.yml`:

```yaml
task-engine:
  global-max-threads: 200
  scale-factor: 2
  scale-up-threshold: 80
  idle-timeout: 60000
  shutdown-timeout: 30
```

## REST Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/monitor/task/status` | GET | All task metrics |
| `/monitor/task/status/{name}` | GET | Specific task |
| `/monitor/task/config/{name}` | PUT | Update pool config |
| `/monitor/task/metrics/{name}` | DELETE | Reset metrics |

## Making Changes

### Adding New Task Type

1. Add enum value in `TaskType.java`
2. Update `TaskThreadPoolFactory` defaults
3. Update `TaskEngineProperties.PoolConfig`

### Modifying Metrics

1. Update `TaskMetrics.java` for new counters
2. Update `TaskStatsResponse.java` for API
3. Update `MetricsCollector.java` for collection

### Adding New Endpoint

1. Add method in `TaskMonitorController.java`
2. Follow existing patterns for response types

## Testing

- Unit tests use JUnit 5
- Integration tests use `@SpringBootTest`
- Stress tests verify high-load scenarios

## Commit Convention

```
feat: add new feature
fix: bug fix
docs: documentation
refactor: code refactoring
test: adding tests
chore: maintenance
```