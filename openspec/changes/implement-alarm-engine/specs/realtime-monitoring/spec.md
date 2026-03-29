# Spec: Realtime Monitoring

## ADDED Requirements

### Requirement: Per-Second Snapshot Output
The monitoring system SHALL output a formatted snapshot of key metrics every second.

#### Scenario: MonitorTask Scheduled Execution
**Given** AlarmEngine 正在运行
**When** 每秒定时任务触发
**Then** 输出全链路监控快照到控制台
**And** 输出间隔可配置（默认 1 秒）

#### Scenario: Snapshot Format
**Given** 监控快照输出
**When** 快照被打印
**Then** 格式包含：Incoming QPS | Success/Error | Dropped | Queue Depth | Processing RT | DB Latency
**And** 使用分隔线格式化输出

### Requirement: QPS Measurement
QPS measurement SHALL use a sliding window to calculate the incoming alarm rate.

#### Scenario: Incoming QPS Calculation
**Given** 告警持续进入系统
**When** QPS 计算窗口滑动（默认 60 秒窗口）
**Then** 每秒计算一次进入速率
**And** QPS = 窗口内进入告警数 / 窗口秒数

#### Scenario: Sliding Window Implementation
**Given** 使用滑动窗口计算 QPS
**When** 时间超过窗口大小
**Then** 旧数据被清除
**And** 窗口大小可配置（默认 60000ms）

### Requirement: Queue Depth Monitoring
Queue depth monitoring SHALL provide visibility into backlog at each stage of the pipeline.

#### Scenario: Receiver Queue Depth
**Given** AlarmReceiver 队列中有积压告警
**When** MonitorTask 采集指标
**Then** 显示接收队列当前深度
**And** 格式：R[depth]

#### Scenario: Worker Queue Depths
**Given** 多个 Worker 队列中有积压告警
**When** MonitorTask 采集指标
**Then** 显示每个 Worker 队列深度
**And** 格式：W0[depth] W1[depth] ... WN[depth]

#### Scenario: Total Queue Depth Alert
**Given** 总队列深度超过阈值
**When** MonitorTask 检测到深度超标
**Then** 输出告警标记（如 *ALERT*）
**And** 阈值可配置（如总容量 80%）

### Requirement: Processing Response Time
Processing response time SHALL be tracked using EWMA to provide a smoothed average.

#### Scenario: Average RT Calculation (EWMA)
**Given** 告警处理完成
**When** 处理时延被记录
**Then** 使用 EWMA 更新平均 RT（alpha=0.3）
**And** 公式：updated = 0.3 * newValue + 0.7 * current

#### Scenario: Per-Node RT Tracking
**Given** 需要追踪各节点处理时延
**When** 告警通过节点
**Then** 记录节点处理开始和结束时间
**And** 可查询各节点平均 RT

### Requirement: DB Write Latency
DB write latency SHALL measure the time taken for batch insert operations.

#### Scenario: Batch Insert Latency Measurement
**Given** BatchDBExecutor 执行批量插入
**When** 批量插入完成
**Then** 记录本次插入耗时（毫秒）
**And** 使用 EWMA 更新平均 DB 延迟

### Requirement: Success/Error Counting
Success and error counts SHALL use LongAdder for high-performance thread-safe counting.

#### Scenario: Thread-Safe Counting
**Given** 高并发场景（10,000+ QPS）
**When** 告警处理成功或失败
**Then** 使用 LongAdder 原子累加计数
**And** 计数可查询、可重置

#### Scenario: Error Rate Alert
**Given** 错误率超过阈值（如 1%）
**When** MonitorTask 检测到错误率超标
**Then** 输出告警标记
**And** 记录错误详情

### Requirement: Dropped Count Tracking
Dropped count tracking SHALL record alarms that were rejected due to queue capacity limits.

#### Scenario: Drop Count Recording
**Given** 告警因队列满被拒绝
**When** 拒绝策略执行丢弃
**Then** 丢弃计数原子递增
**And** 丢弃计数在监控快照中显示

### Requirement: Worker Status Monitoring
Worker status monitoring SHALL provide visibility into thread pool utilization.

#### Scenario: Worker Active Count
**Given** Worker 线程池运行中
**When** MonitorTask 采集指标
**Then** 显示活跃 Worker 数量
**And** 显示空闲、阻塞 Worker 数量

## MODIFIED Requirements

### Requirement: Metrics Data Structure
The metrics data structure SHALL use appropriate concurrent types for different metric categories.

#### Scenario: AlarmMetrics Class Design
**Given** 告警指标需要线程安全
**Then** 计数器使用 LongAdder 而非 AtomicLong
**And** EWMA 值使用 AtomicLong
**And** 队列深度使用 AtomicInteger

## Related Capabilities

- `sharded-processing` - Worker 状态监控
- `batch-persistence` - DB 写入延迟指标
- `backpressure-protection` - 队列深度告警
