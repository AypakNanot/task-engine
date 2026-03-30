# ShardedFlowEngine 使用文档

## 1. 概述

ShardedFlowEngine 是一个**分片流式处理引擎**，基于 Pipeline + Sharding 模式设计，适用于需要**保证同一关键属性数据有序处理**的场景。

### 1.1 核心特性

- **分片并发**：基于分片键（如 DeviceID、UserID）哈希取模，保证同一分片的事件有序处理
- **9 节点流水线**：事件依次通过多个处理节点，每个节点可独立失败/成功
- **背压保护**：ArrayBlockingQueue 有界队列 + 四种拒绝策略防止 OOM
- **优雅停机**：JVM ShutdownHook 确保数据不丢失

### 1.2 性能指标

- **吞吐量**：设计目标 10K+/秒
- **有序性**：同一分片键严格有序
- **零外部依赖**：无 Kafka/Redis/MQ 要求

---

## 2. 快速开始

### 2.1 添加依赖

```xml
<dependency>
    <groupId>com.aypak</groupId>
    <artifactId>flow-engine</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2.2 基本使用

```java
import com.aypak.engine.flow.ShardedFlowEngine;
import com.aypak.engine.flow.core.FlowEvent;
import com.aypak.engine.flow.core.FlowNode;
import com.aypak.engine.flow.core.FlowContext;

// 1. 定义数据类
public class OrderData {
    private String orderId;
    private String userId;
    private Double amount;
    // getters/setters
}

// 2. 创建处理节点
public class ValidateNode implements FlowNode<String, OrderData> {
    @Override
    public String getNodeName() { return "Validate"; }

    @Override
    public boolean process(FlowEvent<String, OrderData> event, FlowContext context) {
        OrderData order = event.getPayload();
        if (order.getAmount() <= 0) {
            context.stop();
            return false;
        }
        return true;
    }
}

public class SaveNode implements FlowNode<String, OrderData> {
    @Override
    public String getNodeName() { return "Save"; }

    @Override
    public boolean process(FlowEvent<String, OrderData> event, FlowContext context) {
        // 保存订单到数据库
        saveToDatabase(event.getPayload());
        context.markPersisted();
        return true;
    }
}

// 3. 构建并启动引擎
ShardedFlowEngine<String, OrderData> engine = ShardedFlowEngine.<String, OrderData>builder()
    .name("OrderProcessor")
    .shardCount(16)           // 16 个分片
    .queueCapacity(5000)      // 每个分片队列容量 5000
    .rejectPolicy(RejectPolicy.BLOCK)  // 队列满时阻塞
    .addNode(new ValidateNode())
    .addNode(new SaveNode())
    .build();

// 4. 提交事件
engine.submit(new FlowEvent<>("user-123", orderData));
```

---

## 3. 核心概念

### 3.1 分片键（Shard Key）

分片键是用于路由事件的关键字段，**同一分片键的事件始终由同一个 Worker 处理**，确保顺序性。

**常见选择**：
- DeviceID - 设备 ID
- UserID - 用户 ID
- OrderID - 订单 ID
- SessionID - 会话 ID

### 3.2 流水线节点（FlowNode）

节点是处理逻辑的基本单元，事件依次通过所有节点。

**节点类型**：
| 类型 | 说明 | 示例 |
|------|------|------|
| **关键节点** | 失败会导致整个事件处理失败 | 数据验证、持久化 |
| **非关键节点** | 失败会被捕获并记录，事件继续传递 | 日志记录、通知发送 |

### 3.3 拒绝策略（RejectPolicy）

当 Worker 队列满时，决定如何处理新提交的事件：

| 策略 | 说明 | 使用场景 |
|------|------|----------|
| **DROP** | 直接丢弃新事件 | 允许丢失的场景 |
| **DROP_OLDEST** | 丢弃队列中最旧的事件 | 新数据更重要的场景 |
| **BLOCK** | 阻塞调用者线程直到有空间 | 不允许丢失的场景 |
| **CALLER_RUNS** | 在调用者线程中直接处理 | 临时流量高峰 |

---

## 4. 使用场景

### 4.1 告警处理（原始场景）

```java
public class AlarmData {
    private String deviceId;
    private String alarmType;
    private Long occurTime;
    private String severity;
}

ShardedFlowEngine<String, AlarmData> engine = ShardedFlowEngine.<String, AlarmData>builder()
    .name("AlarmProcessor")
    .shardCount(32)
    .queueCapacity(10000)
    .addNode(new FilterNode())      // 过滤
    .addNode(new MaskingNode())     // 屏蔽
    .addNode(new AnalysisNode())    // 分析
    .addNode(new PersistenceNode()) // 持久化
    .addNode(new NotifyNode())      // 通知
    .build();
```

### 4.2 订单处理

```java
public class OrderData {
    private String userId;
    private String orderId;
    private Double amount;
}

ShardedFlowEngine<String, OrderData> engine = ShardedFlowEngine.<String, OrderData>builder()
    .name("OrderProcessor")
    .shardCount(16)
    .queueCapacity(5000)
    .addNode(new ValidateOrderNode())     // 验证订单
    .addNode(new CheckInventoryNode())    // 检查库存
    .addNode(new CalculateAmountNode())   // 计算金额
    .addNode(new SaveOrderNode())         // 保存订单
    .addNode(new SendNotificationNode())  // 发送通知
    .build();
```

### 4.3 日志分析

```java
public class LogData {
    private String sessionId;
    private String logLevel;
    private String message;
    private Long timestamp;
}

ShardedFlowEngine<String, LogData> engine = ShardedFlowEngine.<String, LogData>builder()
    .name("LogProcessor")
    .shardCount(8)
    .queueCapacity(20000)
    .addNode(new ParseNode())      // 解析日志
    .addNode(new FilterNode())     // 过滤无效日志
    .addNode(new EnrichNode())     // 丰富数据
    .addNode(new AggregateNode())  // 聚合统计
    .addNode(new SaveNode())       // 保存到 ES
    .build();
```

---

## 5. 配置说明

### 5.1 完整配置示例

```java
ShardedFlowEngine<String, MyData> engine = ShardedFlowEngine.<String, MyData>builder()
    // 基本配置
    .name("MyProcessor")
    .shardCount(16)           // 分片数量（Worker 数量）
    .queueCapacity(5000)      // 每个分片的队列容量

    // 拒绝策略
    .rejectPolicy(RejectPolicy.BLOCK)

    // 分片策略（可选，默认哈希取模）
    .shardingStrategy((key, count) -> Math.abs(key.hashCode()) % count)

    // 节点配置
    .addNode(new Node1())
    .addNode(new Node2())
    .addNode(new Node3())

    // 指标配置
    .metricsEnabled(true)

    // 批处理配置（如果需要批量入库）
    .batchSize(1000)
    .batchTimeoutMs(500)

    .build();
```

### 5.2 参数调优建议

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| **shardCount** | CPU 核心数 * 2 | 更多分片 = 更高并发，但上下文切换增加 |
| **queueCapacity** | 5000-10000 | 根据内存和延迟要求调整 |
| **rejectPolicy** | BLOCK（不允许丢失）<br>DROP（允许丢失） | 根据业务需求选择 |
| **batchSize** | 500-1000 | 批量入库时调优 |
| **batchTimeoutMs** | 500-1000 | 避免长时间等待 |

---

## 6. 监控与指标

### 6.1 获取指标

```java
FlowMetrics metrics = engine.getMetrics();

// 成功数
long success = metrics.getTotalSuccess();

// 失败数
long failure = metrics.getTotalFailure();

// 丢弃数
long dropped = metrics.getTotalDropped();

// 成功率
double successRate = metrics.getSuccessRate();

// QPS
double qps = metrics.getQps();

// 平均响应时间
long avgResponseTime = metrics.getAvgResponseTime();

// 队列深度
int queueDepth = metrics.getQueueDepth().get();
```

### 6.2 指标说明

| 指标 | 类型 | 说明 |
|------|------|------|
| `successCount` | LongAdder | 成功处理的事件数 |
| `failureCount` | LongAdder | 处理失败的事件数 |
| `droppedCount` | LongAdder | 被丢弃的事件数 |
| `receivedCount` | LongAdder | 接收的总事件数 |
| `qps` | double | 每秒处理事件数 |
| `avgResponseTime` | long | 平均处理时延（EWMA） |
| `queueDepth` | AtomicInteger | 当前队列深度 |

---

## 7. 最佳实践

### 7.1 节点设计原则

1. **单一职责**：每个节点只做一件事
2. **无状态**：节点不应保存状态，状态应在 FlowContext 中
3. **快速失败**：节点应尽快返回结果
4. **异常处理**：明确哪些异常是关键，哪些是非关键

### 7.2 分片键选择

- 选择**高基数**字段（如 UserID 比 OrderType 好）
- 选择**业务顺序敏感**字段
- 避免选择**热点**字段（如默认值）

### 7.3 优雅停机

```java
// 注册 JVM 关闭钩子
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    log.info("Shutting down engine...");
    engine.shutdown(30, TimeUnit.SECONDS);
    log.info("Engine shut down completed");
}));
```

---

## 8. 常见问题

### Q1: 如何保证同一用户的事件有序处理？

**A**: 使用 UserID 作为分片键：
```java
engine.submit(new FlowEvent<>(userId, eventData));
```

### Q2: 队列满了会发生什么？

**A**: 根据拒绝策略处理：
- DROP: 返回 false，事件被丢弃
- BLOCK: 阻塞调用者线程直到有空间
- CALLER_RUNS: 在调用者线程中处理

### Q3: 如何处理节点异常？

**A**: 实现 `onFailure()` 方法：
```java
public class MyNode implements FlowNode<String, Data> {
    @Override
    public void onFailure(FlowEvent<String, Data> event, Throwable error) {
        log.error("Node failed", error);
        // 自定义处理逻辑
    }
}
```

### Q4: 如何在节点间共享数据？

**A**: 使用 FlowContext：
```java
public class Node1 implements FlowNode<String, Data> {
    public boolean process(FlowEvent<String, Data> event, FlowContext context) {
        context.set("validated", true);
        return true;
    }
}

public class Node2 implements FlowNode<String, Data> {
    public boolean process(FlowEvent<String, Data> event, FlowContext context) {
        Boolean validated = context.get("validated");
        if (validated == null || !validated) {
            context.stop();
            return false;
        }
        return true;
    }
}
```

---

## 9. 附录

### 9.1 FlowEvent API

```java
// 创建事件
FlowEvent<String, Data> event = new FlowEvent<>("shard-key-123", data);

// 获取信息
String id = event.getId();
String shardKey = event.getShardKey();
Data payload = event.getPayload();
long latency = event.getLatency();

// 设置状态
event.setStatus(FlowEvent.ProcessingStatus.COMPLETED);
event.setErrorMessage("Error message");
```

### 9.2 FlowContext API

```java
FlowContext context = new FlowContext();

// 设置/获取值
context.set("key", value);
Object value = context.get("key");
String strValue = context.get("key", "default");

// 控制流程
context.stop();           // 停止处理
context.markDropped();    // 标记为丢弃
context.markPersisted();  // 标记为已持久化
context.markNotified();   // 标记为已通知

// 检查状态
boolean shouldContinue = context.shouldContinue();
boolean isDropped = context.isDropped();
boolean isPersisted = context.isPersisted();
```
