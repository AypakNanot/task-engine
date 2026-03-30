# TaskEngine 任务引擎文档

## 目录

1. [概述](#1-概述)
2. [核心特性](#2-核心特性)
3. [架构设计](#3-架构设计)
4. [使用指南](#4-使用指南)
5. [配置说明](#5-配置说明)
6. [监控与管理](#6-监控与管理)
7. [最佳实践](#7-最佳实践)
8. [故障排查](#8-故障排查)

---

## 1. 概述

TaskEngine 是一个高性能的 unified task processing center（统一任务处理中心），专为 Spring Boot 应用设计。它提供了标准化的线程池管理、实时监控、动态扩展和任务隔离能力。

### 1.1 性能指标

- **吞吐量**: 250K+ QPS
- **成功率**: 持续负载下 100% 成功率
- **延迟**: 毫秒级响应时间
- **线程安全**: 使用 LongAdder 和 AtomicLong 实现无锁高性能计数

### 1.2 技术栈

| 组件 | 版本/技术 |
|------|----------|
| Java | 17 |
| Spring Boot | 3.5.13 |
| Lombok | 最新 |
| JUnit 5 | 测试框架 |

### 1.3 包结构

```
com.aypak.engine.task
├── core/               # 核心接口和枚举
│   ├── TaskType.java       # 4 种任务类型
│   ├── TaskPriority.java   # 优先级
│   ├── RejectionPolicy.java# 拒绝策略
│   ├── ITaskProcessor.java # 处理器接口
│   ├── TaskConfig.java     # 配置 Builder
│   ├── TaskContext.java    # 执行上下文
│   └── DynamicConfig.java  # 动态配置
├── executor/           # 线程池执行器
│   ├── TaskEngine.java     # 对外接口
│   ├── TaskEngineImpl.java # 主编排器
│   ├── TaskExecutor.java   # 线程池封装
│   ├── TaskRegistry.java   # 任务注册表
│   └── NamedThreadFactory.java
├── monitor/            # 监控指标
│   ├── TaskMetrics.java    # 线程安全指标
│   ├── MetricsCollector.java
│   └── QueueMonitor.java   # 队列监控
├── api/                # REST 端点
│   └── TaskMonitorController.java
└── config/             # Spring 配置
    ├── TaskEngineProperties.java
    └── TaskEngineAutoConfiguration.java
```

---

## 2. 核心特性

### 2.1 四种任务类型隔离

TaskEngine 根据任务的特性将任务分为四种类型，每种类型有专用的线程池策略：

| 类型 | 线程池配置 | 队列容量 | 使用场景 |
|------|-----------|---------|---------|
| **INIT** | core=1, max=CPU | 0 | 系统启动一次性任务 |
| **CRON** | ThreadPoolTaskScheduler | N/A | 定时/周期任务 |
| **HIGH_FREQ** | core=CPU×2, max=CPU×4 | 10000 | 高频业务处理 |
| **BACKGROUND** | core=2, max=4 | 100 | 后台维护任务 |

### 2.2 动态扩展能力

- **自动扩展**: 当队列深度超过阈值（默认 80%）时自动增加线程
- **自动收缩**: 空闲超时（默认 60 秒）后减少线程
- **弹性因子**: 每次扩展/收缩的线程数可配置

### 2.3 四种拒绝策略

```java
public enum RejectionPolicy {
    ABORT_WITH_ALERT,    // 中止并告警 - 记录错误，增加失败指标
    CALLER_RUNS,         // 调用者运行 - 在调用者线程执行
    BLOCK_WAIT,          // 阻塞等待 - 等待队列空间
    DISCARD_OLDEST       // 丢弃最旧 - 移除最旧任务
}
```

### 2.4 线程安全设计

- **LongAdder**: 用于高争用计数器（successCount, failureCount）
- **AtomicLong**: 用于单写入者值（EWMA, windowStartTime）
- **AtomicInteger**: 用于池状态（queueDepth, activeThreads）
- **ConcurrentHashMap**: 任务注册表

---

## 3. 架构设计

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      TaskEngine                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                 TaskEngineImpl                       │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │    │
│  │  │   Registry   │  │   Scaler     │  │  Monitor  │ │    │
│  │  └──────────────┘  └──────────────┘  └───────────┘ │    │
│  └─────────────────────────────────────────────────────┘    │
│         │                    │                    │          │
│         ▼                    ▼                    ▼          │
│  ┌─────────────┐      ┌─────────────┐     ┌───────────┐    │
│  │ INIT Pool   │      │ HIGH_FREQ   │     │ BACKGROUND│    │
│  │ (1-8)       │      │ (16-32)     │     │ (2-4)     │    │
│  └─────────────┘      └─────────────┘     └───────────┘    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    REST API Layer                            │
│  /monitor/task/status    /monitor/task/config/{name}        │
│  /monitor/task/metrics   /monitor/task/registrations        │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件

| 组件 | 职责 |
|------|------|
| `TaskEngine` | 对外接口，提供注册、执行、监控功能 |
| `TaskEngineImpl` | 主编排器，协调各组件 |
| `TaskExecutor` | ThreadPoolExecutor 封装 |
| `TaskRegistry` | ConcurrentHashMap 任务注册表 |
| `TaskThreadPoolFactory` | 创建 ThreadPoolTaskExecutor |
| `DynamicScaler` | 定时扩展逻辑 |
| `NamedThreadFactory` | 线程命名：{Type}-{Name}-{Id} |
| `TaskMetrics` | 基于 LongAdder 的线程安全指标 |
| `MetricsCollector` | QPS 计算、EWMA 平均 |
| `QueueMonitor` | 队列深度监控（100ms 间隔） |

### 3.3 线程命名规范

```
格式：{TaskType}-{taskName}-{id}

示例:
- INIT-DataLoader-1
- HIGH_FREQ-OrderProcessor-15
- CRON-DailyReport-3
- BACKGROUND-LogCleanup-2
```

---

## 4. 使用指南

### 4.1 添加依赖

```xml
<dependency>
    <groupId>com.aypak</groupId>
    <artifactId>task-engine</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 4.2 快速开始

#### 步骤 1：配置 application.yml

```yaml
task-engine:
  global-max-threads: 200
  scale-factor: 2
  scale-up-threshold: 80
  idle-timeout: 60000
  shutdown-timeout: 30
  queue-monitor-interval: 100
```

#### 步骤 2：创建任务处理器

```java
import com.aypak.engine.task.core.ITaskProcessor;
import com.aypak.engine.task.core.TaskPriority;
import com.aypak.engine.task.core.TaskType;
import org.springframework.stereotype.Component;

@Component
public class OrderTaskProcessor implements ITaskProcessor<OrderPayload> {

    @Override
    public String getTaskName() {
        return "OrderProcessor";
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
    public void process(OrderPayload payload) {
        // 业务处理逻辑
        System.out.println("Processing order: " + payload.getOrderId());
    }

    @Override
    public void onFailure(OrderPayload payload, Throwable error) {
        log.error("Order task failed", error);
    }
}
```

#### 步骤 3：注册任务

```java
@Component
public class TaskRegistrationConfig {

    private final TaskEngine taskEngine;

    public TaskRegistrationConfig(TaskEngine taskEngine) {
        this.taskEngine = taskEngine;
    }

    @PostConstruct
    public void registerTasks() {
        // 注册高频任务
        TaskConfig config = TaskConfig.builder()
            .taskName("OrderProcessor")
            .taskType(TaskType.HIGH_FREQ)
            .priority(TaskPriority.HIGH)
            .corePoolSize(16)
            .maxPoolSize(32)
            .queueCapacity(10000)
            .rejectionPolicy(RejectionPolicy.CALLER_RUNS)
            .queueAlertThreshold(80)
            .build();

        taskEngine.register(config, new OrderTaskProcessor());
    }
}
```

#### 步骤 4：执行任务

```java
@Service
public class OrderService {

    private final TaskEngine taskEngine;

    public OrderService(TaskEngine taskEngine) {
        this.taskEngine = taskEngine;
    }

    public void submitOrder(OrderPayload order) {
        taskEngine.execute("OrderProcessor", order);
    }
}
```

### 4.4 四种任务类型详细用法

#### INIT 任务 - 初始化任务

```java
@Component
public class DataLoaderProcessor implements ITaskProcessor<Void> {
    @Override
    public String getTaskName() { return "DataLoader"; }
    @Override
    public TaskType getTaskType() { return TaskType.INIT; }
    @Override
    public TaskPriority getPriority() { return TaskPriority.HIGH; }
    @Override
    public void process(Void context) {
        // 系统启动时加载数据
        loadCacheData();
    }
}
```

#### CRON 任务 - 定时任务

```java
@Component
public class DailyReportProcessor implements ITaskProcessor<Void> {
    @Override
    public String getTaskName() { return "DailyReport"; }
    @Override
    public TaskType getTaskType() { return TaskType.CRON; }
    @Override
    public TaskPriority getPriority() { return TaskPriority.MEDIUM; }

    // 需要额外配置 cronExpression
    // 可通过 TaskConfig 配置 cronExpression、fixedRate 或 fixedDelay
}
```

#### HIGH_FREQ 任务 - 高频任务

```java
@Component
public class MessageProcessor implements ITaskProcessor<Message> {
    @Override
    public String getTaskName() { return "MessageProcessor"; }
    @Override
    public TaskType getTaskType() { return TaskType.HIGH_FREQ; }
    @Override
    public TaskPriority getPriority() { return TaskPriority.HIGH; }
    @Override
    public void process(Message msg) {
        // 高吞吐处理
    }
}
```

#### BACKGROUND 任务 - 后台任务

```java
@Component
public class LogCleanupProcessor implements ITaskProcessor<Void> {
    @Override
    public String getTaskName() { return "LogCleanup"; }
    @Override
    public TaskType getTaskType() { return TaskType.BACKGROUND; }
    @Override
    public TaskPriority getPriority() { return TaskPriority.LOW; }
    @Override
    public void process(Void context) {
        // 日志清理
    }
}
```

### 4.5 MDC 上下文传播

TaskEngine 自动处理 MDC 上下文传播：

```java
// 主线程设置 MDC
MDC.put("userId", "12345");
MDC.put("traceId", "abc-def-ghi");

// 任务执行时自动继承 MDC
taskEngine.execute("MyTask", payload);

// 任务线程中可以获取 MDC
String userId = MDC.get("userId"); // 可获取
```

---

## 5. 配置说明

### 5.1 全局配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `global-max-threads` | 200 | 所有线程池全局最大线程数 |
| `scale-factor` | 2 | 每次扩展增加/减少的线程数 |
| `scale-up-threshold` | 80 | 队列深度百分比触发扩展 |
| `idle-timeout` | 60000 | 缩小前空闲超时 (毫秒) |
| `shutdown-timeout` | 30 | 优雅关闭超时 (秒) |
| `qps-window-size` | 60000 | QPS 计算窗口大小 (毫秒) |
| `queue-monitor-interval` | 100 | 队列监控间隔 (毫秒) |

### 5.2 线程池特定配置

```yaml
task-engine:
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

### 5.3 完整配置示例

```yaml
task-engine:
  # 全局配置
  global-max-threads: 200
  scale-factor: 2
  scale-up-threshold: 80
  idle-timeout: 60000
  shutdown-timeout: 30
  qps-window-size: 60000
  queue-monitor-interval: 100

  # 线程池配置
  pools:
    type1-init:
      core-size: 1
      max-size: 8
    type2-cron:
      core-size: 4
      max-size: 4
    type3-high-freq:
      core-size: 32
      max-size: 64
      queue-capacity: 50000
    type4-background:
      core-size: 2
      max-size: 4
      queue-capacity: 100
```

---

## 6. 监控与管理

### 6.1 REST API

| 端点 | 方法 | 描述 |
|------|------|------|
| `/monitor/task/status` | GET | 获取所有任务状态 |
| `/monitor/task/status/{name}` | GET | 获取特定任务状态 |
| `/monitor/task/registrations` | GET | 获取所有任务注册信息 |
| `/monitor/task/config/{name}` | PUT | 更新任务配置 |
| `/monitor/task/metrics/{name}` | DELETE | 重置特定任务指标 |
| `/monitor/task/metrics` | DELETE | 重置所有任务指标 |

### 6.2 监控指标说明

```json
{
  "OrderProcessor": {
    "taskName": "OrderProcessor",
    "taskType": "HIGH_FREQ",
    "successCount": 150000,
    "failureCount": 12,
    "qps": 2500.5,
    "avgResponseTime": 3,
    "queueDepth": 150,
    "activeThreads": 20,
    "peakThreads": 32,
    "originalMaxPoolSize": 32,
    "currentMaxPoolSize": 32
  }
}
```

### 6.3 监控 Dashboard 集成

TaskEngine 可与 Grafana/Prometheus 集成：

```java
@Configuration
public class MetricsExportConfig {
    // 导出指标到 Prometheus
    @Bean
    public CollectorRegistry collectorRegistry(TaskEngine taskEngine) {
        CollectorRegistry registry = new CollectorRegistry();
        // ... 注册指标
        return registry;
    }
}
```

---

## 7. 最佳实践

### 7.1 高 QPS 场景配置

```java
TaskConfig.builder()
    .taskType(TaskType.HIGH_FREQ)
    .corePoolSize(cpuCount * 8)      // 更多核心线程
    .maxPoolSize(cpuCount * 16)      // 允许扩展
    .queueCapacity(100000)           // 大队列
    .rejectionPolicy(RejectionPolicy.CALLER_RUNS)  // 永不丢弃
    .build()
```

### 7.2 低延迟场景配置

```java
TaskConfig.builder()
    .taskType(TaskType.HIGH_FREQ)
    .corePoolSize(32)                // 固定池
    .maxPoolSize(32)                 // 不扩展
    .queueCapacity(1000)             // 小队列
    .rejectionPolicy(RejectionPolicy.ABORT_WITH_ALERT)  // 快速失败
    .queueAlertThreshold(50)         // 早期告警
    .build()
```

### 7.3 错误处理建议

```java
@Override
public void onFailure(OrderPayload payload, Throwable error) {
    // 1. 记录详细错误
    log.error("Order task failed: orderId={}, error={}",
              payload.getOrderId(), error.getMessage(), error);

    // 2. 发送告警
    alertService.send("ORDER_TASK_FAILED", payload.getOrderId());

    // 3. 降级处理或重试
    retryService.scheduleRetry(payload);
}
```

### 7.4 性能调优建议

1. **CPU 密集型任务**: corePoolSize = CPU 核心数 + 1
2. **IO 密集型任务**: corePoolSize = CPU 核心数 × 2 ~ × 8
3. **队列大小**: 根据内存限制和吞吐需求平衡
4. **拒绝策略**: 关键业务用 BLOCK_WAIT 或 CALLER_RUNS

---

## 8. 故障排查

### 8.1 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 任务堆积 | 处理速度慢于提交速度 | 增加 corePoolSize 或优化处理逻辑 |
| 线程泄漏 | 任务阻塞未释放 | 检查死锁，增加超时设置 |
| OOM | 队列过大 | 减小 queueCapacity，启用拒绝策略 |
| CPU 过高 | 线程过多 | 减少 maxPoolSize |

### 8.2 排查工具

```bash
# 查看任务状态
curl http://localhost:8080/monitor/task/status

# 查看特定任务
curl http://localhost:8080/monitor/task/status/OrderProcessor

# 重置指标
curl -X DELETE http://localhost:8080/monitor/task/metrics
```

### 8.3 线程 dump 分析

```bash
# 获取线程 dump
jstack <pid> > thread-dump.txt

# 查找 TaskEngine 线程
grep "HIGH_FREQ\|INIT\|CRON\|BACKGROUND" thread-dump.txt
```

---

## 附录

### A. TaskConfig 参数说明

| 参数 | 必填 | 说明 |
|------|------|------|
| taskName | 是 | 唯一任务标识符 |
| taskType | 是 | 任务类型 (INIT/CRON/HIGH_FREQ/BACKGROUND) |
| priority | 是 | 任务优先级 (HIGH/MEDIUM/LOW) |
| corePoolSize | 否 | 核心线程数（不指定用类型默认值） |
| maxPoolSize | 否 | 最大线程数 |
| queueCapacity | 否 | HIGH_FREQ 类型的队列容量 |
| rejectionPolicy | 否 | 拒绝策略，默认 ABORT_WITH_ALERT |
| queueAlertThreshold | 否 | 队列告警阈值百分比 (0-100) |
| cronExpression | 条件 | CRON 任务必填 |
| fixedRate | 条件 | CRON 任务固定速率 |
| fixedDelay | 条件 | CRON 任务固定延迟 |

### B. 常见问题 FAQ

**Q: 如何选择合适的任务类型？**
A:
- 启动一次性任务 → INIT
- 定时/周期任务 → CRON
- 高吞吐业务 → HIGH_FREQ
- 后台维护 → BACKGROUND

**Q: 如何保证任务顺序？**
A: TaskEngine 不保证全局顺序。如果需要顺序处理，请使用单线程池或将相关任务发往同一分片。

**Q: 如何优雅停机？**
A: TaskEngine 已注册 JVM ShutdownHook，会自动等待队列清空。确保 shutdown-timeout 设置合理。

---

*文档版本：1.0 | 最后更新：2026-03-30*
