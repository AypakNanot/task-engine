# Design: AlarmEngine Architecture

## 1. 系统架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          AlarmEngine                                     │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                     AlarmReceiver (接收端)                        │   │
│  │                    (ArrayBlockingQueue)                          │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                   │                                     │
│                                   ▼                                     │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │              ShardDispatcher (哈希分片调度器)                      │   │
│  │         DeviceID → Hash → Mod N → WorkerThread                   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                   │                                     │
│          ┌────────────────────────┼────────────────────────┐            │
│          ▼                        ▼                        ▼            │
│  ┌───────────────┐        ┌───────────────┐        ┌───────────────┐   │
│  │  Worker #0    │        │  Worker #1    │        │  Worker #N    │   │
│  │  ┌─────────┐  │        │  ┌─────────┐  │        │  ┌─────────┐  │   │
│  │  │Pipeline │  │        │  │Pipeline │  │        │  │Pipeline │  │   │
│  │  │  1→2→3  │  │        │  │  1→2→3  │  │        │  │  1→2→3  │  │   │
│  │  │  4→5→6  │  │   ...  │  │  4→5→6  │  │   ...  │  │  4→5→6  │  │   │
│  │  │  7→8→9  │  │        │  │  7→8→9  │  │        │  │  7→8→9  │  │   │
│  │  └─────────┘  │        │  └─────────┘  │        │  └─────────┘  │   │
│  └───────────────┘        └───────────────┘        └───────────────┘   │
│                                   │                                     │
│                                   ▼                                     │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                    BatchDBExecutor (批量入库)                      │   │
│  │              (DoubleBuffer + 1000 条/500ms触发)                    │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                   │                                     │
│                                   ▼                                     │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                  NB-Notify / NB-Push (北向推送)                    │   │
│  └──────────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│                    MonitorTask (每秒快照输出)                            │
│         QPS | QueueDepth | RT | DB Latency | Success/Error             │
└─────────────────────────────────────────────────────────────────────────┘
```

## 2. 分片策略设计

### 为什么使用 Hash 取模？
- **同一设备有序**：同一 DeviceID 永远路由到同一 Worker 线程，天然串行
- **消除锁竞争**：不同设备分散到不同线程，无共享状态
- **最大化并行**：N 个 Worker 线程 = N 个并行处理流

### 分片计算公式
```java
int shardIndex = Math.abs(deviceId.hashCode()) % workerCount;
```

### Worker 线程配置
```yaml
alarm-engine:
  worker-count: 32          # Worker 线程数（默认 CPU 核心数 * 2）
  queue-capacity: 10000     # 每 Worker 队列容量
```

## 3. 处理节点接口设计

### PipelineNode 接口
```java
public interface PipelineNode {
    /**
     * 节点名称
     */
    String getNodeName();

    /**
     * 处理告警
     * @param event 告警事件
     * @param context 上下文
     * @return 是否继续传递到下一节点
     * @throws Exception 处理异常
     */
    boolean process(AlarmEvent event, PipelineContext context) throws Exception;

    /**
     * 节点失败回调
     */
    default void onFailure(AlarmEvent event, Throwable error) {
        log.error("Node {} failed for alarm: {}", getNodeName(), event.getId(), error);
    }
}
```

### 9 个处理节点

| 序号 | 节点名称 | 职责 | 是否可跳过 |
|------|----------|------|------------|
| 1 | Receive | 告警接收、格式校验 | 否 |
| 2 | Filter | 本地过滤（无效告警过滤） | 是 |
| 3 | Masking | 本地屏蔽（屏蔽规则匹配） | 是 |
| 4 | Analysis | 业务分析（严重度计算、关联分析） | 否 |
| 5 | Persistence | 批量入库 | 否 |
| 6 | NB-Notify | 北向通知准备（格式转换） | 是 |
| 7 | NB-Filter | 北向过滤（客户订阅过滤） | 是 |
| 8 | NB-Masking | 北向屏蔽（客户屏蔽规则） | 是 |
| 9 | NB-Push | 北向推送（HTTP/MQTT） | 是 |

## 4. 批量入库设计

### 双缓冲队列机制
```
┌─────────────────────────────────────────────────────┐
│              DoubleBufferQueue                       │
│  ┌─────────────────┐     ┌─────────────────┐        │
│  │  ActiveBuffer   │     │  StandbyBuffer  │        │
│  │  (写入中)       │     │  (等待刷写)      │        │
│  │  capacity=1000  │     │  capacity=1000  │        │
│  └─────────────────┘     └─────────────────┘        │
│           │                        │                 │
│           ▼                        ▼                 │
│      新数据写入               定时器触发→批量入库      │
└─────────────────────────────────────────────────────┘
```

### 批量插入实现（纯 JDBC）
```java
public class BatchDBExecutor {
    private static final int BATCH_SIZE = 1000;
    private static final long BATCH_TIMEOUT_MS = 500;

    private final DataSource dataSource;
    private final String insertSql;

    public void batchInsert(List<AlarmEvent> events) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {

            for (int i = 0; i < events.size(); i++) {
                AlarmEvent event = events.get(i);
                ps.setString(1, event.getDeviceId());
                ps.setString(2, event.getAlarmType());
                ps.setTimestamp(3, Timestamp.valueOf(event.getOccurTime()));
                // ... 其他字段
                ps.addBatch();

                if ((i + 1) % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    ps.clearBatch();
                }
            }
            ps.executeBatch(); // 执行剩余批次
        } catch (SQLException e) {
            log.error("Batch insert failed", e);
            throw new RuntimeException(e);
        }
    }
}
```

## 5. 背压保护设计

### 三级缓冲架构
```
AlarmReceiver (ArrayBlockingQueue, capacity=50000)
       │
       ▼
ShardDispatcher (快速路径，无缓冲)
       │
       ▼
Worker Queue (ArrayBlockingQueue, capacity=10000) × N
```

### 拒绝策略
```java
public enum RejectPolicy {
    DROP,           // 丢弃新数据
    DROP_OLDEST,    // 丢弃最旧数据
    BLOCK,          // 阻塞等待
    CALLER_RUNS     // 调用者线程处理
}
```

### 丢弃日志
```java
public class DropLogger {
    private final AtomicLong dropCount = new AtomicLong(0);
    private final AtomicLong lastAlertTime = new AtomicLong(0);

    public void recordDrop(AlarmEvent event) {
        dropCount.incrementAndGet();
        long now = System.currentTimeMillis();
        if (now - lastAlertTime.get() > 1000) {
            log.warn("High drop rate detected: {} drops/sec", dropCount.get());
            dropCount.set(0);
            lastAlertTime.set(now);
        }
    }
}
```

## 6. 可观测性设计

### 监控指标
```java
public class AlarmMetrics {
    // 吞吐指标
    private final LongAdder incomingCount = new LongAdder();
    private final LongAdder successCount = new LongAdder();
    private final LongAdder failureCount = new LongAdder();
    private final LongAdder droppedCount = new LongAdder();

    // 时延指标（EWMA）
    private final AtomicLong avgProcessingRT = new AtomicLong(0);
    private final AtomicLong avgDBLatency = new AtomicLong(0);

    // 队列指标
    private final AtomicInteger receiverQueueDepth = new AtomicInteger(0);
    private final AtomicInteger[] workerQueueDepths;

    // QPS 计算
    private final AtomicLong qps = new AtomicLong(0);
    private final AtomicLong windowStartTime = new AtomicLong(0);
    private final AtomicLong windowCount = new AtomicLong(0);
}
```

### 监控快照输出
```
[MonitorTask] 2026-03-29 10:15:30
================================================================================
Incoming QPS    : 12,500  | Success/Error : 12,480 / 15  | Dropped : 5
Queue Depth     : R[1,234] W0[45] W1[32] ... W31[67]     | Total: 2,890
Processing RT   : 2.3 ms (avg) | DB Latency: 45.6 ms (avg)
Worker Status   : 32 active, 0 idle, 0 blocked
================================================================================
```

## 7. 优雅停机设计

```java
public class AlarmEngine {
    private volatile boolean running = true;
    private final CountDownLatch shutdownLatch = new CountDownLatch(workerCount);

    @PreDestroy
    public void shutdown() {
        log.info("Starting graceful shutdown...");
        running = false;

        // 1. 停止接收新告警
        alarmReceiver.stop();

        // 2. 等待 Worker 队列清空
        for (Worker worker : workers) {
            worker.signalShutdown();
        }

        // 3. 等待所有 Worker 完成（最多 30 秒）
        try {
            if (!shutdownLatch.await(30, TimeUnit.SECONDS)) {
                log.warn("Shutdown timeout, forcing...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 4. 刷新缓冲区剩余数据
        batchDBExecutor.flush();

        log.info("Shutdown complete");
    }
}
```

## 8. 异常隔离设计

```java
public class Worker implements Runnable {
    @Override
    public void run() {
        while (running) {
            try {
                AlarmEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) continue;

                processPipeline(event);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 单个告警处理失败不影响 Worker 线程
                log.error("Worker error processing alarm", e);
                metrics.recordFailure();
            }
        }
        shutdownLatch.countDown();
    }

    private void processPipeline(AlarmEvent event) {
        PipelineContext context = new PipelineContext();
        for (PipelineNode node : nodes) {
            try {
                boolean shouldContinue = node.process(event, context);
                if (!shouldContinue) break;
            } catch (Exception e) {
                node.onFailure(event, e);
                // 根据节点重要性决定是否继续
                if (node.isCritical()) {
                    throw e;
                }
            }
        }
    }
}
```

## 9. 屏蔽规则快速查找

### 使用 ConcurrentHashMap 实现 O(1) 查找
```java
public class MaskingRuleCache {
    // DeviceID → 屏蔽规则列表
    private final ConcurrentHashMap<String, List<MaskingRule>> deviceRules =
        new ConcurrentHashMap<>();

    // 告警类型 → 屏蔽规则列表
    private final ConcurrentHashMap<String, List<MaskingRule>> typeRules =
        new ConcurrentHashMap<>();

    // 全局屏蔽规则
    private final CopyOnWriteArrayList<MaskingRule> globalRules =
        new CopyOnWriteArrayList<>();

    public boolean isMasked(AlarmEvent event) {
        // 1. 检查全局规则
        for (MaskingRule rule : globalRules) {
            if (rule.matches(event)) return true;
        }

        // 2. 检查设备规则
        List<MaskingRule> rules = deviceRules.get(event.getDeviceId());
        if (rules != null) {
            for (MaskingRule rule : rules) {
                if (rule.matches(event)) return true;
            }
        }

        // 3. 检查类型规则
        rules = typeRules.get(event.getAlarmType());
        if (rules != null) {
            for (MaskingRule rule : rules) {
                if (rule.matches(event)) return true;
            }
        }

        return false;
    }

    public void addRule(MaskingRule rule) {
        if (rule.isGlobal()) {
            globalRules.add(rule);
        } else if (rule.getTargetType() == TargetType.DEVICE) {
            deviceRules.computeIfAbsent(rule.getTargetId(), k -> new CopyOnWriteArrayList<>())
                       .add(rule);
        } else {
            typeRules.computeIfAbsent(rule.getTargetId(), k -> new CopyOnWriteArrayList<>())
                     .add(rule);
        }
    }
}
```

## 10. 包结构

```
com.aypak.taskengine.alarm
├── AlarmEngine.java              # 对外接口
├── AlarmEngineImpl.java          # 主实现
├── core/
│   ├── AlarmEvent.java           # 告警数据结构
│   ├── PipelineContext.java      # 流水线上下文
│   ├── PipelineNode.java         # 处理节点接口
│   ├── RejectPolicy.java         # 拒绝策略
│   └── MaskingRule.java          # 屏蔽规则
├── receiver/
│   ├── AlarmReceiver.java        # 告警接收器
│   └── DropLogger.java           # 丢弃日志
├── dispatcher/
│   ├── ShardDispatcher.java      # 分片调度器
│   └── Worker.java               # Worker 线程
├── nodes/
│   ├── ReceiveNode.java          # 接收节点
│   ├── FilterNode.java           # 过滤节点
│   ├── MaskingNode.java          # 屏蔽节点
│   ├── AnalysisNode.java         # 分析节点
│   ├── PersistenceNode.java      # 持久化节点
│   ├── NbNotifyNode.java         # 北向通知节点
│   ├── NbFilterNode.java         # 北向过滤节点
│   ├── NbMaskingNode.java        # 北向屏蔽节点
│   └── NbPushNode.java           # 北向推送节点
├── batch/
│   ├── BatchDBExecutor.java      # 批量执行器
│   └── DoubleBufferQueue.java    # 双缓冲队列
├── monitor/
│   ├── AlarmMetrics.java         # 告警指标
│   ├── MonitorTask.java          # 监控任务
│   └── MetricsCollector.java     # 指标收集器
└── config/
    ├── AlarmEngineProperties.java # 配置属性
    └── AlarmEngineAutoConfiguration.java # 自动配置
```

## 11. 配置属性

```yaml
alarm-engine:
  enabled: true

  # 分片配置
  worker-count: 32              # Worker 线程数
  worker-queue-capacity: 10000  # 每 Worker 队列容量

  # 接收器配置
  receiver-queue-capacity: 50000  # 接收队列容量
  reject-policy: DROP             # 拒绝策略

  # 批量入库配置
  batch-size: 1000              # 批量阈值
  batch-timeout-ms: 500         # 批量超时
  db-jdbc-url: jdbc:mysql://localhost:3306/alarm
  db-username: root
  db-password: secret

  # 监控配置
  monitor-interval-sec: 1       # 监控输出间隔
  qps-window-size-ms: 60000     # QPS 计算窗口

  # 停机配置
  shutdown-timeout-sec: 30      # 优雅停机超时
```

## 12. 性能目标

| 指标 | 目标值 | 测量方式 |
|------|--------|----------|
| 吞吐量 | ≥ 10,000 条/秒 | 16 核环境，持续 15 分钟 |
| 有序性 | 100% | 同一设备告警顺序验证 |
| 数据丢失率 | 0% | 进入处理链的数据不丢失 |
| 平均处理时延 | < 5ms | 单条告警从接收到入库 |
| DB 写入时延 | < 100ms | 批量插入单次耗时 |
| 内存占用 | < 2GB | 10 万条告警缓冲 |
