# Proposal: Implement AlarmEngine

## Why

大规模设备告警处理需要高性能、零依赖、严格有序的解决方案，现有 TaskEngine 无法满足告警流水线的特殊需求（如批量入库、设备有序性、9 节点处理链）。

## What Changes

### New Package Structure
- `com.aypak.taskengine.alarm` - New alarm engine package
- 6 new spec capabilities: sharded-processing, pipeline-nodes, batch-persistence, backpressure-protection, realtime-monitoring, graceful-shutdown

### New Capabilities
1. **Sharded Processing** - DeviceID-based hash sharding for ordered processing
2. **Pipeline Nodes** - 9-node processing pipeline (Receive → NB-Push)
3. **Batch Persistence** - Double buffer queue with 1000 records/500ms triggers
4. **Backpressure Protection** - Bounded queues with rejection policies
5. **Realtime Monitoring** - Per-second snapshots with QPS, RT, queue depth
6. **Graceful Shutdown** - 30-second timeout with buffer flush

## Summary

实现一个高性能原生告警处理引擎 (AlarmEngine)，作为 task-engine 项目的子模块。在零外部依赖（仅纯 Java + JDBC）的前提下，支撑大规模设备告警的实时处理、存储与推送。

## Motivation

**业务背景:**
- 需要处理大规模设备产生的告警数据流（目标 10,000+ 条/秒）
- 同一设备的告警必须严格有序处理
- 不能使用 Kafka、Redis 等第三方中间件
- 需要防止内存溢出，具备流量保护能力

**与 TaskEngine 的关系:**
- TaskEngine: 通用任务调度中心，侧重线程池管理和动态扩缩容
- AlarmEngine: 专用告警处理流水线，侧重顺序保证、批量入库、全链路监控
- 两者可共享部分基础设施（如监控指标收集、配置管理）

## Scope

### 核心能力
1. **分片处理模型** - 基于 DeviceID 哈希取模，保证同一设备有序性
2. **9 节点流水线** - Receive → Filter → Masking → Analysis → Persistence → NB-Notify → NB-Filter → NB-Masking → NB-Push
3. **批量入库** - 双缓冲队列，攒批到 1000 条或 500ms 触发一次批量插入
4. **背压保护** - 有界阻塞队列 + 丢弃策略，防止 OOM
5. **实时监控** - 每秒输出 QPS、队列深度、处理时延、DB 写入延迟等指标
6. **优雅停机** - 关闭前确保缓冲区数据全部入库
7. **异常隔离** - 单节点失败不影响分片线程和其他告警处理

### 配置化能力
- Worker 线程数可配置（默认 CPU 核心数 * 2）
- 批量阈值可配置（默认 1000 条）
- 批量超时可配置（默认 500ms）
- 队列容量可配置（有界）

### 不在范围内
- 分布式部署（单机版本）
- 持久化队列（纯内存）
- Web UI 仪表盘（控制台输出 + JMX 暴露）

## Approach

### Phase 1: 核心基础设施
- 定义告警数据结构 (AlarmEvent)
- 实现分片处理器 (ShardedProcessor)
- 实现 Worker 线程池 (WorkerPool)

### Phase 2: 流水线节点
- 定义处理节点接口 (PipelineNode)
- 实现 9 个具体节点逻辑
- 实现节点间异常隔离

### Phase 3: 批量入库
- 实现双缓冲队列 (DoubleBufferQueue)
- 实现 BatchDBExecutor（纯 JDBC 批量插入）
- 实现攒批触发逻辑（数量 + 时间双触发）

### Phase 4: 背压与保护
- 实现有界阻塞队列
- 实现拒绝策略和丢弃日志
- 实现流量统计

### Phase 5: 监控与可观测性
- 实现 MonitorTask（每秒快照）
- 实现指标收集（QPS、RT、队列深度）
- 实现优雅停机钩子

## Dependencies

- Java 17（与 task-engine 一致）
- Spring Boot 3.5.x（可选，支持自动配置）
- JDBC（数据库驱动由使用者提供）
- 无第三方中间件

## Risks

| 风险 | 缓解措施 |
|------|----------|
| 哈希冲突导致负载不均 | 使用高质量哈希算法，支持自定义分片器 |
| 批量入库失败影响大量数据 | 单批次失败重试 + 降级单条插入 |
| 内存泄漏风险 | 严格限制队列长度 + 定时清理 |
| 优雅停机时数据丢失 | 双缓冲切换时加锁保护 |

## Success Criteria

- [ ] 吞吐量 ≥ 10,000 条/秒（16 核环境）
- [ ] 同一设备告警 100% 有序
- [ ] 15 分钟高压测试无 OOM、无数据丢失
- [ ] 批量入库达到 1000 条/500ms 触发
- [ ] 每秒输出监控快照
- [ ] 优雅停机不丢失缓冲区数据
