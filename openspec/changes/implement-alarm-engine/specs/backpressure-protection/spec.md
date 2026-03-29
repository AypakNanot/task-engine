# Spec: Backpressure Protection

## ADDED Requirements

### Requirement: Bounded Blocking Queue
Bounded blocking queues SHALL prevent unbounded memory growth and MUST protect against OOM errors.

#### Scenario: Receiver Queue Capacity Limit
**Given** AlarmReceiver 使用 ArrayBlockingQueue
**When** 队列已达到容量上限（默认 50000）
**Then** 新告警无法进入队列
**And** 触发拒绝策略

#### Scenario: Worker Queue Capacity Limit
**Given** 每个 Worker 拥有独立的 ArrayBlockingQueue
**When** Worker 队列已达到容量上限（默认 10000）
**Then** 新告警无法进入该 Worker 队列
**And** 触发拒绝策略

### Requirement: Reject Policy Execution
Multiple rejection policies SHALL handle overflow scenarios when queues reach capacity.

#### Scenario: DROP Policy
**Given** 配置了 DROP 拒绝策略
**When** 队列满无法接收新告警
**Then** 新告警被丢弃
**And** 记录丢弃计数

#### Scenario: DROP_OLDEST Policy
**Given** 配置了 DROP_OLDEST 拒绝策略
**When** 队列满无法接收新告警
**Then** 队列中最旧的告警被丢弃
**And** 新告警进入队列

#### Scenario: BLOCK Policy
**Given** 配置了 BLOCK 拒绝策略
**When** 队列满无法接收新告警
**Then** 调用者线程阻塞等待空间
**And** 最多等待配置的超时时间

#### Scenario: CALLER_RUNS Policy
**Given** 配置了 CALLER_RUNS 拒绝策略
**When** 队列满无法接收新告警
**Then** 调用者线程直接执行告警处理逻辑
**And** 告警不进入队列

### Requirement: Drop Logger
The drop logger SHALL record discarded alarms with rate-limited alerting to prevent log storms.

#### Scenario: Drop Counting
**Given** 告警被拒绝丢弃
**When** 丢弃发生时
**Then** 丢弃计数原子递增（AtomicLong）
**And** 丢弃计数可查询

#### Scenario: Drop Alert Rate Limiting
**Given** 高丢弃率场景（> 1000 条/秒）
**When** 持续丢弃告警
**Then** 每秒最多输出一次告警日志
**And** 避免日志风暴

### Requirement: Backpressure Propagation
Backpressure propagation SHALL ensure that downstream congestion is visible and actionable.

#### Scenario: Worker Queue Full Feedback
**Given** Worker 队列深度超过阈值（如 80%）
**When** MonitorTask 检测到队列深度
**Then** 输出告警日志提示背压状态
**And** 可选择性降低接收速率

#### Scenario: End-to-End Flow Control
**Given** 处理链路后端节点处理缓慢
**When** 队列深度持续增加
**Then** 背压传递到接收端
**And** 接收端触发拒绝策略

### Requirement: Memory Protection
Memory protection mechanisms SHALL ensure predictable memory usage under all load conditions.

#### Scenario: OOM Prevention
**Given** 所有队列使用有界容量
**When** 系统处理告警时
**Then** 内存占用有上限
**And** 不会因队列无限增长导致 OOM

#### Scenario: Memory Usage Estimate
**Given** 配置了队列容量
**When** 计算最大内存占用
**Then** 最大内存 = (ReceiverCapacity + WorkerCount × WorkerQueueCapacity) × AlarmEventSize
**And** 可通过配置限制内存占用

## Related Capabilities

- `sharded-processing` - Worker 队列容量配置
- `realtime-monitoring` - 队列深度监控指标
