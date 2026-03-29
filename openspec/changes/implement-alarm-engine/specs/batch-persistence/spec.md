# Spec: Batch Persistence

## ADDED Requirements

### Requirement: Double Buffer Queue Mechanism
The double buffer queue mechanism SHALL enable high-concurrency writes with efficient batch flushing.

#### Scenario: Buffer Switching
**Given** 双缓冲队列（ActiveBuffer + StandbyBuffer）已初始化
**When** ActiveBuffer 写入数据达到阈值或超时
**Then** ActiveBuffer 和 StandbyBuffer 角色切换
**And** 原 StandbyBuffer 中的数据被批量写入数据库

#### Scenario: Concurrent Write Safety
**Given** 多个 Worker 线程同时提交告警到批量队列
**When** 告警被写入双缓冲队列
**Then** 写入操作线程安全，无数据竞争
**And** 数据不丢失、不重复

### Requirement: Batch Size Threshold
Batch insertion SHALL be triggered when the accumulated count reaches the configured threshold.

#### Scenario: Batch Trigger at 1000 Records
**Given** 批量队积累积了 1000 条告警
**When** 第 1000 条告警被写入
**Then** 立即触发批量插入
**And** 批量大小可配置（默认 1000 条）

### Requirement: Batch Timeout Trigger
Batch insertion SHALL be triggered by timeout to ensure low-latency persistence even under low load.

#### Scenario: Timeout Trigger at 500ms
**Given** 批量队列中有告警但未达到 1000 条
**When** 距离上次批量写入已超过 500ms
**Then** 触发批量插入，无论数量多少
**And** 超时间隔可配置（默认 500ms）

### Requirement: Pure JDBC Batch Insert
Batch insertion SHALL use pure JDBC without any ORM frameworks or third-party libraries.

#### Scenario: PreparedStatement Batch Execution
**Given** 批量触发条件满足
**When** 执行批量插入
**Then** 使用 PreparedStatement.addBatch() 累积 SQL
**And** 使用 PreparedStatement.executeBatch() 执行批量
**And** 不使用任何 ORM 框架或第三方库

#### Scenario: DB Latency Measurement
**Given** 批量插入开始执行
**When** 批量插入完成
**Then** 记录插入耗时（毫秒）
**And** 更新平均 DB 延迟指标（EWMA）

### Requirement: Batch Failure Handling
Batch insertion failures SHALL be handled with retry and fallback mechanisms to prevent data loss.

#### Scenario: Batch Insert Failure Recovery
**Given** 批量插入过程中发生 SQL 异常
**When** 异常被捕获
**Then** 重试一次（可配置次数）
**And** 重试失败后降级为单条插入
**And** 记录失败告警 ID 和错误信息

### Requirement: Batch Executor Configuration
Batch executor parameters SHALL be loaded from configuration with sensible defaults.

#### Scenario: Configuration Loading
**Given** 配置文件中设置了 batch-size 和 batch-timeout-ms
**When** BatchDBExecutor 初始化
**Then** 从配置加载批量参数
**And** 使用默认值作为后备

## MODIFIED Requirements

### Requirement: Persistence Node Integration
The Persistence node SHALL submit alarms to the BatchDBExecutor rather than executing SQL directly.

#### Scenario: Submit to Batch Executor
**Given** 告警通过前序节点处理
**When** 告警到达 Persistence 节点
**Then** 告警被提交到 BatchDBExecutor 队列
**And** 不直接执行 SQL 插入

## Related Capabilities

- `pipeline-nodes` - PersistenceNode 使用 BatchDBExecutor
- `realtime-monitoring` - DB 写入延迟指标收集
