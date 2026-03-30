# AlarmEngine 告警处理引擎文档

## 目录

1. [概述](#1-概述)
2. [核心特性](#2-核心特性)
3. [架构设计](#3-架构设计)
4. [9 节点流水线](#4-9-节点流水线)
5. [使用指南](#5-使用指南)
6. [配置说明](#6-配置说明)
7. [监控与指标](#7-监控与指标)
8. [性能优化](#8-性能优化)
9. [最佳实践](#9-最佳实践)
10. [故障排查](#10-故障排查)

---

## 1. 概述

AlarmEngine 是一个**高性能原生告警处理引擎**，在**零外部依赖**（无 Kafka、Redis、MQ）的情况下，支撑大规模设备告警的实时处理、存储与推送。

### 1.1 设计目标

| 目标 | 指标 |
|------|------|
| **高负载能力** | ≥ 10,000 条/秒 |
| **严格有序性** | 同一设备告警处理顺序与产生顺序一致 |
| **零数据丢失** | 高压（15 分钟+）环境下稳定运行 |
| **无第三方中间件** | 纯 Java + 数据库（JDBC） |

### 1.2 技术栈

| 组件 | 技术 |
|------|------|
| Java | 17 |
| Spring Boot | 3.5.13 |
| 数据库 | 任意支持 JDBC 的数据库 |
| 并发工具 | BlockingQueue, AtomicLong, CountDownLatch |

### 1.3 核心设计理念

1. **分片处理**：基于 DeviceID 哈希取模，保证同一设备有序
2. **批量入库**：双缓冲队列 + 定时/定量双触发
3. **背压保护**：有界队列防止 OOM
4. **优雅停机**：JVM 停机钩子确保数据不丢失

### 1.4 包结构

```
com.aypak.engine.alarm
├── batch/              # 批量处理
│   ├── DoubleBufferQueue.java   # 双缓冲队列
│   └── BatchDBExecutor.java     # 批量数据库执行器
├── config/             # Spring 配置
│   ├── AlarmEngineProperties.java
│   └── AlarmEngineAutoConfiguration.java
├── core/               # 核心接口和模型
│   ├── AlarmEvent.java          # 告警事件
│   ├── PipelineContext.java     # 流水线上下文
│   ├── PipelineNode.java        # 节点接口
│   ├── MaskingRule.java         # 屏蔽规则
│   └── RejectPolicy.java        # 拒绝策略
├── dispatcher/         # 分片调度
│   ├── ShardDispatcher.java     # 分片器
│   └── Worker.java              # Worker 执行器
├── engine/             # 引擎实现
│   ├── AlarmEngine.java         # 对外接口
│   ├── AlarmEngineImpl.java     # 实现类
│   └── GracefulShutdown.java    # 优雅停机
├── monitor/            # 监控指标
│   ├── AlarmMetrics.java        # 告警指标
│   ├── MetricsCollector.java    # 指标收集
│   └── MonitorTask.java         # 监控任务
├── nodes/              # 9 节点流水线
│   ├── ReceiveNode.java         # 接收节点
│   ├── FilterNode.java          # 过滤节点
│   ├── MaskingNode.java         # 屏蔽节点
│   ├── AnalysisNode.java        # 分析节点
│   ├── PersistenceNode.java     # 持久化节点
│   ├── NbNotifyNode.java        # 北向通知准备
│   ├── NbFilterNode.java        # 北向过滤
│   ├── NbMaskingNode.java       # 北向屏蔽
│   └── NbPushNode.java          # 北向推送
└── receiver/           # 告警接收
    ├── AlarmReceiver.java       # 告警接收器
    └── DropLogger.java          # 丢弃日志
```

---

## 2. 核心特性

### 2.1 分片并发模型

```
┌─────────────────────────────────────────────────────────┐
│                    ShardDispatcher                       │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐   │
│  │ Worker-0│  │ Worker-1│  │   ...   │  │Worker-15│   │
│  │(Hash=0) │  │(Hash=1) │  │         │  │(Hash=15)│   │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘   │
│       │              │                     │           │
│       ▼              ▼                     ▼           │
│  Device-A      Device-C              Device-B          │
│  Device-D      Device-H              Device-P          │
└─────────────────────────────────────────────────────────┘
```

**核心原理**：
- 根据 `DeviceID.hashCode() % workerCount` 路由到对应 Worker
- 同一设备的所有告警始终由同一 Worker 处理
- 消除锁竞争，保证时序性

### 2.2 批量数据库插入

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 批量大小 | 1000 | 达到此数量触发刷写 |
| 批量超时 | 500ms | 达到此时间触发刷写 |
| 双缓冲队列 | 2000 | 活跃 + 备用缓冲区 |

**优势**：
- 10000 次单条插入 → 10 次批量插入（每次 1000 条）
- 磁盘 I/O 降低 99.9%

### 2.3 四种拒绝策略

```java
public enum RejectPolicy {
    DROP,          // 丢弃新数据
    DROP_OLDEST,   // 丢弃最旧数据
    BLOCK,         // 阻塞等待（5 秒超时）
    CALLER_RUNS    // 调用者线程处理
}
```

### 2.4 内存保护机制

```
告警流入 → ArrayBlockingQueue(50000) → Worker 处理
                                │
                                ▼
                         队列满时触发拒绝策略
                         防止 OOM
```

---

## 3. 架构设计

### 3.1 整体架构图

```
┌────────────────────────────────────────────────────────────────┐
│                         AlarmEngine                             │
│  ┌────────────┐     ┌──────────────────┐     ┌─────────────┐  │
│  │    Receiver│ ──► │ ShardDispatcher  │ ──► │   Workers   │  │
│  │  (背压保护) │     │ (分片路由)       │     │  (流水线)   │  │
│  └────────────┘     └──────────────────┘     └─────────────┘  │
│                                              │                  │
│                                              ▼                  │
│                                        ┌─────────────┐         │
│                                        │ BatchDB     │         │
│                                        │ (批量入库)  │         │
│                                        └─────────────┘         │
│  ┌────────────┐                                                │
│  │   Monitor  │ ◄────────── 全链路指标采集                      │
│  │  (1s 快照)  │                                                │
│  └────────────┘                                                │
│  ┌────────────┐                                                │
│  │Graceful    │ ◄────────── JVM ShutdownHook                   │
│  │ Shutdown   │                                                │
│  └────────────┘                                                │
└────────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件

| 组件 | 职责 | 关键实现 |
|------|------|---------|
| **AlarmEngine** | 对外接口 | submit(), getMetrics(), shutdown() |
| **AlarmReceiver** | 告警接收 + 背压 | ArrayBlockingQueue + 拒绝策略 |
| **ShardDispatcher** | 分片路由 | DeviceID 哈希取模 |
| **Worker** | 流水线执行 | 独立队列 +9 节点处理 |
| **PipelineNode** | 处理节点 | 9 个可插拔节点 |
| **BatchDBExecutor** | 批量入库 | 双缓冲 + 定时/定量刷写 |
| **AlarmMetrics** | 指标收集 | LongAdder + EWMA |
| **MonitorTask** | 监控快照 | 1 秒间隔输出 |
| **GracefulShutdown** | 优雅停机 | ShutdownHook + 等待清空 |

---

## 4. 9 节点流水线

每条告警必须依次通过以下 9 个处理节点：

```
Receive → Filter → Masking → Analysis → Persistence → NB-Notify → NB-Filter → NB-Masking → NB-Push
   │         │          │          │           │            │            │            │         │
   ▼         ▼          ▼          ▼           ▼            ▼            ▼            ▼         ▼
  接收      过滤       屏蔽       分析        入库         通知准备     北向过滤    北向屏蔽   北向推送
```

### 4.1 节点详细说明

| 序号 | 节点 | 类名 | 职责 | 是否关键 |
|------|------|------|------|---------|
| 1 | **Receive** | `ReceiveNode` | 接收告警，记录进入时间 | 是 |
| 2 | **Filter** | `FilterNode` | 本地过滤（重复/无效告警） | 否 |
| 3 | **Masking** | `MaskingNode` | 本地屏蔽规则匹配 | 否 |
| 4 | **Analysis** | `AnalysisNode` | 业务逻辑分析（根因/衍生） | 是 |
| 5 | **Persistence** | `PersistenceNode` | 批量入库提交 | 是 |
| 6 | **NB-Notify** | `NbNotifyNode` | 北向通知准备 | 否 |
| 7 | **NB-Filter** | `NbFilterNode` | 北向过滤规则 | 否 |
| 8 | **NB-Masking** | `NbMaskingNode` | 北向屏蔽规则 | 否 |
| 9 | **NB-Push** | `NbPushNode` | HTTP 推送到北向系统 | 是 |

### 4.2 节点处理流程

```java
for (PipelineNode node : nodes) {
    try {
        boolean shouldContinue = node.process(event, context);
        if (!shouldContinue) {
            break; // 节点决定停止（如过滤）
        }
    } catch (Exception e) {
        node.onFailure(event, e);
        if (node.isCritical()) {
            event.setStatus(FAILED);
            return; // 关键节点失败，终止
        }
        // 非关键节点失败，继续处理
    }
}
```

### 4.3 关键节点说明

**关键节点失败会终止整个流程**：
- `ReceiveNode`: 接收失败无法继续
- `AnalysisNode`: 分析结果是后续处理基础
- `PersistenceNode`: 数据必须入库
- `NbPushNode`: 推送是最终目标

**非关键节点失败可继续**：
- `FilterNode`: 过滤失败不影响主流程
- `MaskingNode`: 屏蔽失败可稍后处理
- `NbFilterNode`: 北向过滤失败仍可推送

---

## 5. 使用指南

### 5.1 添加依赖

```xml
<dependency>
    <groupId>com.aypak</groupId>
    <artifactId>task-engine</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 5.2 快速开始

#### 步骤 1：配置 application.yml

```yaml
alarm-engine:
  enabled: true
  worker-count: 16
  worker-queue-capacity: 5000
  receiver-queue-capacity: 50000
  reject-policy: DROP
  batch-size: 1000
  batch-timeout-ms: 500
  shutdown-timeout-sec: 30
  monitor-interval-sec: 1
```

#### 步骤 2：创建数据库表

```sql
CREATE TABLE alarm_event (
    id VARCHAR(64) PRIMARY KEY,
    device_id VARCHAR(64) NOT NULL,
    alarm_type VARCHAR(64) NOT NULL,
    occur_time DATETIME NOT NULL,
    severity VARCHAR(16) NOT NULL,
    source_system VARCHAR(64),
    location VARCHAR(256),
    description VARCHAR(1024),
    submit_time BIGINT NOT NULL,
    INDEX idx_device (device_id),
    INDEX idx_occur (occur_time)
);
```

#### 步骤 3：自动启动（Spring Boot）

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

AlarmEngine 会自动启动（存在 DataSource 且 enabled=true）。

### 5.3 手动使用

#### 创建告警引擎

```java
@Autowired
private DataSource dataSource;

public void initAlarmEngine() {
    String insertSql = "INSERT INTO alarm_event " +
        "(device_id, alarm_type, occur_time, severity, source_system, " +
        "location, description, submit_time) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    AlarmEngine engine = new AlarmEngineImpl(
        dataSource,
        insertSql,
        16,              // workerCount
        5000,            // workerQueueCapacity
        50000,           // receiverQueueCapacity
        RejectPolicy.DROP
    );

    engine.start();
}
```

#### 提交告警

```java
@Autowired
private AlarmEngine alarmEngine;

public void submitAlarm(AlarmData data) {
    AlarmEvent event = AlarmEvent.builder()
        .id(generateId())
        .deviceId(data.getDeviceId())
        .alarmType(data.getAlarmType())
        .occurTime(data.getOccurTime())
        .severity(AlarmEvent.Severity.MAJOR)
        .sourceSystem("IoT-Platform")
        .location("Building-A-Floor-3")
        .description("Temperature exceeded threshold")
        .build();

    boolean accepted = alarmEngine.submit(event);
    if (!accepted) {
        log.warn("Alarm rejected: {}", event.getId());
    }
}
```

### 5.4 自定义处理节点

#### 扩展 AnalysisNode

```java
public class CustomAnalysisNode extends AnalysisNode {

    @Override
    public String getNodeName() {
        return "CustomAnalysis";
    }

    @Override
    public boolean process(AlarmEvent event, PipelineContext context) {
        // 自定义分析逻辑
        if (isRootCause(event)) {
            event.putPayload("isRootCause", true);
        }

        // 调用父类处理
        return super.process(event, context);
    }

    private boolean isRootCause(AlarmEvent event) {
        // 根因分析逻辑
        return event.getSeverity() == AlarmEvent.Severity.CRITICAL;
    }
}
```

#### 扩展 NbPushNode

```java
public class HttpPushNode extends NbPushNode {

    private final String targetUrl;
    private final RestTemplate restTemplate;

    public HttpPushNode(String targetUrl) {
        this.targetUrl = targetUrl;
        this.restTemplate = new RestTemplate();
    }

    @Override
    protected boolean doPush(AlarmEvent event, PipelineContext context) {
        Map<String, Object> payload = buildPushPayload(event);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                targetUrl,
                payload,
                String.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("HTTP push failed", e);
            return false;
        }
    }

    private Map<String, Object> buildPushPayload(AlarmEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("alarmId", event.getId());
        data.put("deviceId", event.getDeviceId());
        data.put("alarmType", event.getAlarmType());
        data.put("severity", event.getSeverity().name());
        data.put("occurTime", event.getOccurTime());
        data.put("description", event.getDescription());
        return data;
    }
}
```

### 5.5 告警屏蔽规则

```java
// 创建屏蔽规则
MaskingRule rule = MaskingRule.builder()
    .id("mask-001")
    .name("Night Time Mask")
    .targetType(MaskingRule.TargetType.DEVICE)
    .targetId("device-12345")
    .alarmType("TEMP_HIGH")
    .startTime(LocalTime.of(22, 0))
    .endTime(LocalTime.of(6, 0))
    .enabled(true)
    .build();

// 注册到流水线
// MaskingNode 会自动匹配并屏蔽
```

---

## 6. 配置说明

### 6.1 完整配置参数

```yaml
alarm-engine:
  # 基础配置
  enabled: true                    # 是否启用
  worker-count: 16                 # Worker 数量
  worker-queue-capacity: 5000      # 每个 Worker 队列容量
  receiver-queue-capacity: 50000   # 接收器队列容量
  reject-policy: DROP              # 拒绝策略：DROP/DROP_OLDEST/BLOCK/CALLER_RUNS

  # 批量入库配置
  batch-size: 1000                 # 批量大小
  batch-timeout-ms: 500            # 批量超时 (毫秒)

  # 停机配置
  shutdown-timeout-sec: 30         # 优雅停机超时 (秒)

  # 监控配置
  monitor-interval-sec: 1          # 监控快照间隔 (秒)
```

### 6.2 配置建议

#### 高吞吐场景（100K+ QPS）

```yaml
alarm-engine:
  worker-count: 32                 # 更多 Worker
  worker-queue-capacity: 10000     # 更大 Worker 队列
  receiver-queue-capacity: 100000  # 更大接收队列
  reject-policy: CALLER_RUNS       # 永不丢弃
  batch-size: 2000                 # 更大批量
  batch-timeout-ms: 200            # 更快超时
```

#### 低延迟场景

```yaml
alarm-engine:
  worker-count: 16                 # 固定 Worker
  worker-queue-capacity: 1000      # 小队列
  receiver-queue-capacity: 5000    # 小接收队列
  reject-policy: ABORT_WITH_ALERT  # 快速失败
  batch-size: 500                  # 小批量快速刷写
  batch-timeout-ms: 100            # 快速超时
```

### 6.3 拒绝策略选择指南

| 策略 | 适用场景 | 数据丢失风险 |
|------|---------|-------------|
| **DROP** | 非关键告警，优先保新鲜度 | 高 |
| **DROP_OLDEST** | 需要最新数据 | 中 |
| **BLOCK** | 需要保证交付 | 无（可能超时） |
| **CALLER_RUNS** | 关键告警，不允许丢失 | 无 |

---

## 7. 监控与指标

### 7.1 监控快照输出

每秒输出一次全链路监控快照：

```
================================================================================
AlarmEngine Monitor - 2026-03-30T14:30:25.123
--------------------------------------------------------------------------------
Incoming QPS    : 12580 | Success/Error : 12575 / 5 | Dropped : 0
Queue Depth     : R[1250] W[3200] | Total: 4450
Processing RT   : 3 ms (avg) | DB Latency: 45 ms (avg)
Success Rate    : 99.96% | Failure Rate: 0.04%
Worker Status   : W0[150] W1[180] W2[210] W3[190] W4[200] W5[175] W6[195] W7[205]
================================================================================
```

### 7.2 指标说明

| 指标 | 含义 | 告警阈值 |
|------|------|---------|
| **Incoming QPS** | 每秒进入告警数 | > 20000 |
| **Success/Error** | 成功/失败计数 | Error > 1% |
| **Dropped** | 丢弃数量 | > 0 |
| **Queue Depth R/W** | 接收队列/Worker 队列深度 | R>40000, W>4000 |
| **Processing RT** | 平均处理时延 | > 10ms |
| **DB Latency** | 数据库写入延迟 | > 100ms |
| **Success Rate** | 成功率 | < 99% |

### 7.3 AlarmMetrics API

```java
@Autowired
private AlarmEngine alarmEngine;

public void printMetrics() {
    AlarmMetrics metrics = alarmEngine.getMetrics();

    long incomingCount = metrics.getIncomingCount();
    long successCount = metrics.getSuccessCount();
    long failureCount = metrics.getFailureCount();
    long droppedCount = metrics.getDroppedCount();
    long qps = metrics.getQPS();
    long avgProcessingRT = metrics.getAvgProcessingRT();
    long avgDBLatency = metrics.getAvgDBLatency();
    int receiverQueueDepth = metrics.getReceiverQueueDepth();
    int totalWorkerQueueDepth = metrics.getTotalWorkerQueueDepth();
    double successRate = metrics.getSuccessRate();
    double failureRate = metrics.getFailureRate();

    log.info("Metrics: QPS={}, SuccessRate={}%, RT={}ms",
             qps, successRate, avgProcessingRT);
}
```

### 7.4 指标快照类

```java
AlarmMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();

// 可直接用于日志或监控导出
log.info("Snapshot: {}", snapshot);
// 输出：QPS: 12580 | Success: 12575 | Failure: 5 | ...
```

---

## 8. 性能优化

### 8.1 分片数量调优

**原则**：Worker 数量应该接近 CPU 核心数的 2 倍

| CPU 核心数 | 推荐 Worker 数 |
|-----------|---------------|
| 4 核 | 8 |
| 8 核 | 16 |
| 16 核 | 32 |
| 32 核 | 64 |

**原因**：
- 太多 Worker 导致频繁上下文切换
- 太少 Worker 无法充分利用 CPU

### 8.2 批量参数调优

| 参数 | 调优方向 | 影响 |
|------|---------|------|
| batchSize | 增大 → 减少 DB 交互次数 | DB 延迟增加，吞吐增加 |
| batchTimeoutMs | 减小 → 更快刷写 | 实时性提升，批效率降低 |

**推荐配置**：
- 高吞吐：batchSize=2000, batchTimeoutMs=200
- 低延迟：batchSize=500, batchTimeoutMs=100

### 8.3 队列容量调优

```
可用内存 ÷ 单告警大小 ≈ 最大队列容量

假设：
- 可用内存：2GB = 2,147,483,648 bytes
- 单告警大小：~1KB = 1024 bytes
- 最大容量：2,147,483,648 ÷ 1024 ≈ 2,097,152

安全起见，设置为最大容量的 50%: 1,048,576
```

### 8.4 数据库优化建议

1. **批量插入**：使用 `executeBatch()` 而非单条插入
2. **索引优化**：在 `device_id` 和 `occur_time` 上建立索引
3. **连接池**：使用 HikariCP，配置 `maximumPoolSize=10-20`
4. **事务提交**：批量插入后统一提交

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
```

---

## 9. 最佳实践

### 9.1 设备顺序保证

```java
// DeviceID 相同的告警保证顺序处理
AlarmEvent event1 = AlarmEvent.builder()
    .deviceId("device-001")  // 相同 DeviceID
    .id("alarm-001")
    // ...
    .build();

AlarmEvent event2 = AlarmEvent.builder()
    .deviceId("device-001")  // 相同 DeviceID
    .id("alarm-002")
    // ...
    .build();

// event1 一定在 event2 之前处理（如果提交顺序如此）
alarmEngine.submit(event1);
alarmEngine.submit(event2);
```

### 9.2 零数据丢失配置

```yaml
alarm-engine:
  reject-policy: BLOCK        # 阻塞等待，不丢弃
  receiver-queue-capacity: 100000  # 大队列缓冲
  worker-queue-capacity: 10000
  shutdown-timeout-sec: 60    # 更长停机等待时间
```

### 9.3 异常处理

```java
public class RobustAnalysisNode extends AnalysisNode {

    @Override
    public boolean process(AlarmEvent event, PipelineContext context) {
        try {
            // 业务逻辑
            analyzeEvent(event);
            return true;
        } catch (BusinessException e) {
            // 业务异常，标记事件
            event.setStatus(AlarmEvent.ProcessingStatus.FAILED);
            event.setErrorMessage("Analysis failed: " + e.getMessage());
            return false;
        } catch (Exception e) {
            // 未知异常，记录但不中断
            log.error("Unexpected error in analysis", e);
            return true; // 继续处理
        }
    }
}
```

### 9.4 监控集成

```java
@Component
public class AlarmMetricsExporter {

    private final AlarmEngine alarmEngine;

    public AlarmMetricsExporter(AlarmEngine alarmEngine) {
        this.alarmEngine = alarmEngine;
    }

    @Scheduled(fixedRate = 5000)  // 每 5 秒导出一次
    public void exportMetrics() {
        AlarmMetrics metrics = alarmEngine.getMetrics();

        // 导出到 Prometheus/Graphite 等
        prometheusRegistry.gauge("alarm_qps", metrics.getQPS());
        prometheusRegistry.gauge("alarm_success_rate", metrics.getSuccessRate());
        prometheusRegistry.gauge("alarm_queue_depth", (double)metrics.getReceiverQueueDepth());
    }
}
```

### 9.5 优雅停机验证

```bash
# 1. 提交大量告警
for i in {1..10000}; do
    curl -X POST http://localhost:8080/alarms \
         -d "{\"deviceId\":\"device-$i\",\"type\":\"TEST\"}"
done

# 2. 触发停机
kill -15 <pid>

# 3. 检查日志
# 应该看到：
# "Step 1: Stopping AlarmReceiver..."
# "Step 2: Waiting for Worker queues to drain..."
# "All queues drained successfully"
# "Step 4: Flushing batch buffer to database..."
# "AlarmEngine shutdown completed in XXXms"
```

---

## 10. 故障排查

### 10.1 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|---------|---------|
| 告警丢失 | 队列满，DROP 策略 | 增大队列或改用 BLOCK |
| 处理延迟高 | Worker 不足 | 增加 worker-count |
| DB 写入慢 | 批量太小 | 增大 batchSize |
| OOM | 队列过大 | 减小 queueCapacity |
| 顺序错乱 | DeviceID 不一致 | 检查 DeviceID 生成逻辑 |

### 10.2 排查命令

```bash
# 查看 Worker 队列深度
# 通过日志或 JMX 查看

# 线程 dump 分析
jstack <pid> > thread-dump.txt
grep "AlarmWorker" thread-dump.txt

# 堆 dump 分析
jmap -dump:format=b,file=heap.hprof <pid>
```

### 10.3 日志级别配置

```yaml
logging:
  level:
    com.aypak.engine.alarm: DEBUG
    com.aypak.engine.alarm.batch: DEBUG
    com.aypak.engine.alarm.dispatcher: DEBUG
```

### 10.4 监控告警配置

```yaml
# Prometheus Alert Rules
groups:
- name: alarm-engine
  rules:
  - alert: AlarmEngineHighDropRate
    expr: rate(alarm_dropped_total[1m]) > 0
    for: 1m
    annotations:
      summary: "AlarmEngine is dropping alarms"

  - alert: AlarmEngineHighLatency
    expr: alarm_processing_rt_ms > 100
    for: 5m
    annotations:
      summary: "AlarmEngine processing latency too high"

  - alert: AlarmEngineQueueFull
    expr: alarm_queue_depth / alarm_queue_capacity > 0.9
    for: 2m
    annotations:
      summary: "AlarmEngine queue is almost full"
```

---

## 附录

### A. 数据库表结构

```sql
-- 告警事件表
CREATE TABLE alarm_event (
    id VARCHAR(64) PRIMARY KEY COMMENT '告警 ID',
    device_id VARCHAR(64) NOT NULL COMMENT '设备 ID',
    alarm_type VARCHAR(64) NOT NULL COMMENT '告警类型',
    occur_time DATETIME NOT NULL COMMENT '发生时间',
    severity VARCHAR(16) NOT NULL COMMENT '严重度',
    source_system VARCHAR(64) COMMENT '来源系统',
    location VARCHAR(256) COMMENT '位置',
    description VARCHAR(1024) COMMENT '描述',
    submit_time BIGINT NOT NULL COMMENT '提交时间戳',
    INDEX idx_device (device_id),
    INDEX idx_occur (occur_time),
    INDEX idx_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件表';
```

### B. DeviceID 哈希计算

```java
// ShardDispatcher.getShardIndex()
private int getShardIndex(String deviceId) {
    int hash = deviceId.hashCode();
    hash = Math.abs(hash);  // 确保为正
    return hash % workerCount;  // 取模路由
}
```

### C. 优雅停机流程

```
1. 停止接收新告警 (AlarmReceiver.stop())
2. 等待 Receiver 队列清空
3. 等待 Worker 队列清空
4. 停止分片调度器
5. 刷写 BatchDBExecutor 缓冲区
6. 关闭数据库连接
7. 完成停机
```

---

*文档版本：1.0 | 最后更新：2026-03-30*
