# Spec: Sharded Processing

## ADDED Requirements

### Requirement: DeviceID-Based Hash Sharding
DeviceID-based hash sharding ensures that all alarms from the same device MUST be processed by the same Worker thread, guaranteeing ordering.

#### Scenario: Same Device Ordered Processing
**Given** 多个告警来自同一设备（DeviceID 相同）
**When** 告警被提交到处理引擎
**Then** 所有告警必须路由到同一个 Worker 线程
**And** 告警处理顺序与提交顺序完全一致

#### Scenario: Different Device Parallel Processing
**Given** 告警来自不同设备（DeviceID 不同）
**When** 告警被提交到处理引擎
**Then** 告警应尽可能分散到不同 Worker 线程并行处理

### Requirement: Configurable Worker Count
The number of Worker threads SHALL be configurable, with sensible defaults based on CPU core count.

#### Scenario: Worker Count Configuration
**Given** 系统配置文件中设置了 worker-count
**When** AlarmEngine 启动时
**Then** 应创建指定数量的 Worker 线程
**And** 默认值为 CPU 核心数 * 2

#### Scenario: Hash Distribution Consistency
**Given** Worker 数量已配置
**When** 使用相同 DeviceID 计算哈希
**Then** Math.abs(deviceId.hashCode()) % workerCount 结果必须一致
**And** 不随启动时间、JVM 重启而变化

### Requirement: Worker Thread Isolation
Each Worker thread SHALL operate independently with its own queue, eliminating lock contention and containing exceptions.

#### Scenario: Worker Queue Independence
**Given** 多个 Worker 线程运行
**When** Worker 处理各自队列中的告警
**Then** Worker 之间无共享队列，无锁竞争
**And** 每 Worker 拥有独立的 ArrayBlockingQueue

#### Scenario: Worker Exception Containment
**Given** 某 Worker 处理告警时抛出异常
**When** 异常被捕获并记录
**Then** 不影响其他 Worker 线程运行
**And** 该 Worker 线程继续处理后续告警

## Related Capabilities

- `pipeline-nodes` - Worker 线程中执行的 9 节点流水线
- `backpressure-protection` - Worker 队列的背压保护机制
