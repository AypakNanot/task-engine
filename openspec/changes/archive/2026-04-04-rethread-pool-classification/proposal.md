# Change: 线程池分类优化与统一管控

## Why

当前 Task Engine 存在以下问题：

1. **TaskType 分类不够合理**：现有 4 类（INIT/CRON/HIGH_FREQ/BACKGROUND）主要基于触发方式和频率，未考虑任务的资源特性（CPU 密集型 vs IO 密集型），导致线程池配置与任务特点不匹配。

2. **线程池创建混乱**：开发人员通过 `taskEngine.register(config, processor)` 创建任务时，通常不关注线程池配置，容易导致多个相似线程池被重复创建，造成资源浪费。

3. **缺少统一管控**：缺少全局统一的线程池策略，无法实现资源的最优配置和监控。

## What Changes

### 1. TaskType 重新分类

| 新类型 | 前缀 | 资源特性 | 典型场景 |
|--------|------|----------|----------|
| `CPU_BOUND` | `CPU` | CPU 密集型，计算密集 | 数据加密、图像压缩、复杂计算 |
| `IO_BOUND` | `IO` | IO 密集型，等待密集 | 网络请求、文件读写、数据库操作 |
| `HYBRID` | `HYBRID` | 混合型（默认） | 同时涉及计算和 IO 的业务逻辑 |
| `SCHEDULED` | `CRON` | 定时触发，轻量执行 | 定时采集、周期性检查 |
| `BATCH` | `BATCH` | 批量处理，大任务量 | 数据同步、批量导入导出 |

**移除现有类型**：`INIT`、`HIGH_FREQ`、`BACKGROUND`

### 2. 混合任务拆分策略

对于同时涉及 CPU 和 IO 的任务，要求开发者**自行拆分**为子任务：
- **CPU 子任务**：提交到 `CPU_BOUND` 线程池
- **IO 子任务**：提交到 `IO_BOUND` 线程池
- 通过组合模式在应用层编排

**示例**：
```java
// 错误：在 CPU 密集型任务中执行 IO 操作
@TaskType(CPU_BOUND)
public void process(ImageData data) {
    // 计算 + 网络上传 = 混合任务，不应使用 CPU_BOUND
}

// 正确：拆分为独立子任务
@TaskType(CPU_BOUND)
public void compress(ImageData data) { /* 纯计算 */ }

@TaskType(IO_BOUND)
public void upload(CompressedData data) { /* 纯 IO */ }
```

### 3. 统一线程池管理

- **预配置模式**：所有线程池在应用启动时统一创建，配置来自 `application.yml`
- **注册制**：任务注册时只能选择预配置的线程池类型，不允许自行创建
- **全局限制**：通过 `globalMaxThreads` 限制总线程数，防止资源耗尽

### 4. 配置结构变更

```yaml
task-engine:
  global-max-threads: 200          # 全局最大线程数（硬限制）

  pools:
    cpu-bound:
      core-size: 4                 # CPU 核数（可配置）
      max-size: 8
      queue-capacity: 100
    io-bound:
      core-size: 16                # IO 密集型，更多线程
      max-size: 64
      queue-capacity: 1000
    hybrid:
      core-size: 8
      max-size: 16
      queue-capacity: 500
    scheduled:
      core-size: 4
      max-size: 4
      queue-capacity: 0
    batch:
      core-size: 2
      max-size: 4
      queue-capacity: 10000
```

## Impact

### Affected Specs
- `task-type-classification` - 任务类型分类规范（新增）
- `thread-pool-management` - 线程池管理规范（新增）
- `task-registration` - 任务注册接口（修改）

### Affected Code
| 文件 | 变更类型 |
|------|----------|
| `TaskType.java` | 枚举值重定义 |
| `TaskConfig.java` | 移除 priority，添加 pool 选择 |
| `TaskThreadPoolFactory.java` | 工厂逻辑重构 |
| `TaskEngineProperties.java` | 配置结构变更 |
| `ITaskProcessor.java` | 接口简化 |
| 所有测试文件 | 适配新类型 |

### Breaking Changes

1. **现有 TaskType 迁移**：
   - `INIT` → 迁移到 `CPU_BOUND` 或 `SCHEDULED`
   - `HIGH_FREQ` → 根据任务特性选择 `CPU_BOUND`/`IO_BOUND`/`HYBRID`
   - `BACKGROUND` → 迁移到 `BATCH`

2. **配置迁移**：
   - `type1Init` → `cpu-bound`
   - `type2Cron` → `scheduled`
   - `type3HighFreq` → `hybrid` 或 `io-bound`
   - `type4Background` → `batch`

3. **API 变更**：
   - `ITaskProcessor.getPriority()` 已移除（已完成）
   - `ITaskProcessor.getTaskType()` 返回新枚举值

## Migration Guide

### 代码迁移
```java
// Before
public TaskType getTaskType() { return TaskType.HIGH_FREQ; }

// After - 根据任务特性选择
public TaskType getTaskType() {
    // IO 密集型：网络请求、文件读写
    return TaskType.IO_BOUND;
    // 或 CPU 密集型：复杂计算
    // return TaskType.CPU_BOUND;
}
```

### 配置迁移
```yaml
# Before
task-engine:
  pools:
    type3-high-freq:
      core-size: 16
      max-size: 32
      queue-capacity: 10000

# After
task-engine:
  pools:
    io-bound:
      core-size: 16
      max-size: 64
      queue-capacity: 1000
```
