# Design: Task Engine Architecture

## 1. System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Task Engine Core                             │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │   Type 1    │  │   Type 2    │  │   Type 3    │  │   Type 4    │
│  │  Init Pool  │  │  Cron Pool  │  │  High-Freq  │  │  Background │ │
│  │  (Short)    │  │ (Scheduler) │  │  Pool       │  │  Pool       │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘ │
├─────────────────────────────────────────────────────────────────────┤
│                      TaskRegistry (ConcurrentHashMap)                │
│                      MetricsCollector (Thread-Safe)                 │
│                      DynamicScaler (Threshold-Based)                │
└─────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      REST API Layer                                  │
│  POST /task/register   │ GET /monitor/task/status                   │
│  POST /task/execute    │ PUT /task/config/{taskId}                  │
└─────────────────────────────────────────────────────────────────────┘
```

## 2. Task Type Isolation Strategy

### Type 1: Initialization Tasks
- **Pool**: Dedicated short-lived pool (core=1, max=CPU cores)
- **Lifecycle**: Created on demand, destroyed after completion
- **Use Case**: One-time startup tasks, initialization logic

### Type 2: Periodic Tasks
- **Pool**: `ThreadPoolTaskScheduler` (scheduled execution)
- **Support**: Cron, FixedRate, FixedDelay
- **Use Case**: Data collection, periodic sync, heartbeat

### Type 3: High-Frequency Streaming Tasks
- **Pool**: Fixed high-performance pool (core=CPU*2, max=CPU*4)
- **Queue**: Bounded blocking queue (configurable capacity)
- **Use Case**: Alert processing, event streaming, high QPS operations

### Type 4: Background Maintenance Tasks
- **Pool**: Shared low-priority pool (core=2, max=4)
- **Use Case**: Log cleanup, data archival, batch processing

## 3. Core Interfaces

### ITaskProcessor Interface
```java
public interface ITaskProcessor<T> {
    String getTaskName();
    TaskType getTaskType();
    TaskPriority getPriority();
    void process(T context);
    default void onFailure(T context, Throwable error) {}
    default void onSuccess(T context) {}
}
```

### TaskConfig Configuration
```java
public class TaskConfig {
    private String taskName;
    private TaskType taskType;
    private TaskPriority priority;
    private int corePoolSize;
    private int maxPoolSize;
    private int queueCapacity;
    private RejectionPolicy rejectionPolicy;
    private int queueAlertThreshold; // percentage (0-100)
}
```

## 4. Metrics Collection Design

### Per-Task Metrics (Thread-Safe)
```
ConcurrentHashMap<String, TaskMetrics>
├── currentQps: AtomicLong (sliding window)
├── avgResponseTime: AtomicLong (EWMA)
├── successCount: AtomicLong
├── failureCount: AtomicLong
├── queueDepth: AtomicInteger
├── activeThreads: AtomicInteger
└── peakThreads: AtomicInteger
```

### Queue Monitoring
- Poll queue depth every 100ms
- Alert when depth > threshold percentage
- Log warning with task name and current depth

## 5. Dynamic Scaling Algorithm

### Scale-Up Trigger
```
IF queueDepth > (queueCapacity * scaleUpThreshold / 100)
   AND activeThreads == maxPoolSize
   AND currentMaxPoolSize < globalMaxLimit
THEN
   increase maxPoolSize by scaleFactor
   log scaling event
```

### Scale-Down Trigger
```
IF idleTime > idleTimeout
   AND currentMaxPoolSize > originalMaxPoolSize
THEN
   decrease maxPoolSize by scaleFactor
   log scaling event
```

### Global Limits
- `globalMaxThreads`: Maximum total threads across all pools (default: 200)
- `scaleFactor`: Threads to add/remove per scaling event (default: 2)

## 6. Rejection Policies

| Policy | Behavior | Use Case |
|--------|----------|----------|
| `ABORT_WITH_ALERT` | Drop task, log error, send alert | Critical alerts |
| `CALLER_RUNS` | Execute in caller thread | Cleanup tasks |
| `DISCARD_OLDEST` | Remove oldest task, add new | Non-critical data |
| `BLOCK_WAIT` | Block until space available | Guaranteed delivery |

## 7. Graceful Shutdown

```
1. Stop accepting new tasks
2. Wait up to 30 seconds for queued tasks
3. Log remaining tasks if timeout
4. Force shutdown if timeout exceeded
```

### Shutdown Hook Registration
```java
@PreDestroy
public void gracefulShutdown() {
    for (TaskExecutor executor : executors.values()) {
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            log.warn("Forced shutdown for {}", executor.getTaskName());
            executor.shutdownNow();
        }
    }
}
```

## 8. Context Propagation

### TraceID Transfer Mechanism (via MDC/Slf4j)
```java
public class TaskContext {
    private String traceId;      // From MDC.get("traceId")
    private Map<String, String> baggage;  // MDC context map copy
    private long submitTime;
}

// Before task submission
TaskContext context = new TaskContext(
    MDC.get("traceId"),
    MDC.getCopyOfContextMap(),
    System.currentTimeMillis()
);

// Inside task execution (via TaskDecorator)
MDC.put("traceId", context.getTraceId());
MDC.putAll(context.getBaggage());
```

**Note**: TraceID sourced from Slf4j MDC (Mapped Diagnostic Context). No external tracing framework required.

## 9. Thread Naming Convention

Format: `{TaskType}-{TaskName}-{ThreadId}`

Examples:
- `INIT-DatabaseInitializer-1`
- `CRON-DataCollector-2`
- `HIGH_FREQ-AlertProcessor-15`
- `BACKGROUND-LogCleaner-3`

## 10. Configuration Properties

```yaml
task-engine:
  global-max-threads: 200
  scale-factor: 2
  scale-up-threshold: 80  # percent
  idle-timeout: 60000     # ms
  shutdown-timeout: 30000 # ms

  pools:
    type1-init:
      core-size: 1
      max-size: 8
    type2-cron:
      pool-size: 4
    type3-high-freq:
      core-size: 16
      max-size: 32
      queue-capacity: 10000
    type4-background:
      core-size: 2
      max-size: 4
```

## 11. Error Handling

- Uncaught exceptions logged with full context
- Failure callbacks invoked on error
- Metrics updated for both success and failure
- Alert triggered on repeated failures (configurable threshold)