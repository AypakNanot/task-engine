# Task Engine 操作规范

## 目的

本规范旨在确保 Task Engine 项目的线程池使用一致性，防止资源浪费和管理混乱。

## 核心原则

1. **统一管理**：所有线程池必须通过 Task Engine 统一创建和管理
2. **禁止私建**：严禁在业务代码中直接创建线程或线程池
3. **类型隔离**：不同任务类型必须使用对应的隔离线程池
4. **配置先行**：线程池参数必须通过配置文件统一管理

---

## 一、禁止行为（RED LINES）

### 1. 禁止直接创建线程

```java
// ❌ 严禁：直接创建 Thread
new Thread(() -> {
    // 业务逻辑
});

// ❌ 严禁：使用 Executors 工具类创建线程池
ExecutorService executor = Executors.newFixedThreadPool(10);
ExecutorService executor = Executors.newCachedThreadPool();
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

// ❌ 严禁：使用 CompletableFuture 的默认线程池
CompletableFuture.runAsync(() -> {
    // 使用 ForkJoinPool.commonPool()，不受控
});
```

### 2. 禁止绕过 TaskEngine 注册

```java
// ❌ 严禁：在业务代码中直接创建 ThreadPoolTaskExecutor
@Autowired
private ThreadPoolTaskExecutor executor;  // 错误！

// ❌ 严禁：手动创建线程池并执行
ThreadPoolTaskExecutor customExecutor = new ThreadPoolTaskExecutor();
customExecutor.initialize();
customExecutor.execute(() -> { ... });
```

### 3. 禁止在配置类外使用 @Bean 创建线程池

```java
// ❌ 严禁：在 @Configuration 类中创建线程池 Bean
@Configuration
public class MyConfig {
    @Bean
    public ThreadPoolTaskExecutor myExecutor() {
        return new ThreadPoolTaskExecutor();  // 错误！
    }
}
```

---

## 二、推荐做法（BEST PRACTICES）

### 1. 任务注册标准流程

**步骤 1：实现 ITaskProcessor 接口**

```java
@Component
public class OrderProcessor implements ITaskProcessor<OrderEvent> {

    @Override
    public String getTaskName() {
        return "OrderProcessor";
    }

    @Override
    public TaskType getTaskType() {
        // 根据任务特性选择类型（见下方选型指南）
        return TaskType.IO_BOUND;
    }

    @Override
    public void process(OrderEvent event) {
        // 业务逻辑
        orderService.process(event);
    }

    @Override
    public void onFailure(OrderEvent event, Throwable error) {
        log.error("订单处理失败：orderId={}", event.getOrderId(), error);
    }
}
```

**步骤 2：在 @PostConstruct 中注册**

```java
@Service
public class OrderService {

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private OrderProcessor orderProcessor;

    @PostConstruct
    public void init() {
        // 通过 TaskConfig 配置参数
        TaskConfig config = TaskConfig.builder()
            .taskName("OrderProcessor")
            .taskType(TaskType.IO_BOUND)
            // 可选：覆盖默认配置
            // .corePoolSize(16)
            // .maxPoolSize(64)
            // .queueCapacity(1000)
            .build();

        taskEngine.register(config, orderProcessor);
    }

    public void submitOrder(OrderEvent event) {
        // 通过 TaskEngine 执行任务
        taskEngine.execute("OrderProcessor", event);
    }
}
```

### 2. 使用 TaskEngine 执行任务

```java
@Service
public class NotificationService {

    @Autowired
    private TaskEngine taskEngine;

    public void sendNotification(String userId, String message) {
        // ✅ 正确：通过 TaskEngine 执行
        taskEngine.execute("NotificationProcessor", new NotificationEvent(userId, message));
    }
}
```

### 3. 定时任务使用方式

```java
@Service
public class DataCollectionScheduler {

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private ThreadPoolTaskScheduler scheduler;  // 仅限定时任务调度器

    @PostConstruct
    public void init() {
        // 注册数据处理任务
        taskEngine.register(TaskConfig.builder()
            .taskName("DataProcessor")
            .taskType(TaskType.SCHEDULED)
            .build(), new DataProcessor());

        // 使用调度器触发
        scheduler.scheduleAtFixedRate(
            () -> taskEngine.execute("DataProcessor", new DataEvent()),
            60000  // 每分钟
        );
    }
}
```

---

## 三、线程池选型指南

### TaskType 选择决策树

```
1. 是否是启动时一次性任务？
   └─ 是 → CPU_BOUND（原 INIT）

2. 是否由 Cron/定时触发？
   └─ 是 → SCHEDULED（原 CRON）

3. 是否是大批量数据处理？
   └─ 是 → BATCH（原 BACKGROUND）

4. 任务主要消耗是什么？
   ├─ CPU 计算（加密、压缩、复杂算法） → CPU_BOUND
   ├─ IO 等待（网络、数据库、文件） → IO_BOUND
   └─ 混合且无法拆分 → HYBRID
```

### 各类型默认配置

| 类型 | Core | Max | Queue | 说明 |
|------|------|-----|-------|------|
| CPU_BOUND | CPU 核数 | CPU*2 | 100 | 少线程，减少上下文切换 |
| IO_BOUND | 16 | 64 | 1000 | 多线程吸收 IO 等待 |
| HYBRID | 8 | 16 | 500 | 平衡配置 |
| SCHEDULED | 4 | 4 | 0 | 定时任务不排队 |
| BATCH | 2 | 4 | 10000 | 低优先级，大队列 |

---

## 四、配置管理

### application.yml 标准配置

```yaml
task-engine:
  # 全局最大线程数（硬限制）
  global-max-threads: 200

  # 动态扩展配置
  scale-factor: 2
  scale-up-threshold: 80
  idle-timeout: 60000

  # 各类型线程池配置
  pools:
    cpu-bound:
      core-size: 4
      max-size: 8
      queue-capacity: 100
    io-bound:
      core-size: 16
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

---

## 五、违规检查清单

在代码审查时，检查以下项目：

- [ ] 是否存在 `new Thread(...)` 语句
- [ ] 是否使用 `Executors.newXXXThreadPool()` 方法
- [ ] 是否直接注入 `ThreadPoolTaskExecutor`（非 TaskEngine）
- [ ] 是否在 `@Configuration` 类中创建线程池 Bean
- [ ] 是否使用 `CompletableFuture.supplyAsync()`（无自定义线程池）
- [ ] 是否在测试类外使用 `ForkJoinPool.commonPool()`

### 自动化检查（IDE 插件）

在 IDEA 中配置 Inspection：

1. **Settings → Editor → Inspections**
2. 搜索 "Thread" 和 "Executor"
3. 添加自定义规则匹配上述模式

---

## 六、异常场景处理

### 场景 1：需要自定义线程池参数

**错误做法**：
```java
// ❌ 自行创建
ThreadPoolTaskExecutor custom = new ThreadPoolTaskExecutor();
```

**正确做法**：
```java
// ✅ 通过配置文件或 API 调整
taskEngine.updateConfig("MyTask", DynamicConfig.builder()
    .maxPoolSize(100)
    .build());
```

### 场景 2：第三方库需要传入 Executor

**错误做法**：
```java
// ❌ 传入自定义线程池
library.process(customExecutor);
```

**正确做法**：
```java
// ✅ 使用 TaskEngine 管理的线程池
ThreadPoolTaskExecutor managedExecutor = taskEngine.getExecutor("MyTask");
library.process(managedExecutor);
```

### 场景 3：定时任务调度

**错误做法**：
```java
// ❌ 使用 Executors 创建调度器
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
```

**正确做法**：
```java
// ✅ 使用 Spring 的 ThreadPoolTaskScheduler（由框架管理）
@Autowired
private ThreadPoolTaskScheduler scheduler;
```

---

## 七、性能与监控

### 监控指标

通过以下 REST API 监控线程池状态：

```bash
# 查看所有任务状态
GET /monitor/task/status

# 查看特定任务状态
GET /monitor/task/status/{taskName}

# 健康检查
GET /actuator/health
```

### 告警阈值

建议在配置中设置：

```yaml
task-engine:
  queue-monitor-interval: 100  # 100ms 检查一次队列
```

当队列深度达到 80% 时，系统会自动记录告警日志。

---

## 八、违规后果

违反本规范可能导致：

1. **资源浪费**：线程泄漏、内存溢出
2. **监控盲区**：任务执行情况不可见
3. **故障难以定位**：线程命名不规范，日志分散
4. **代码审查不通过**：违反规范的 PR 将被拒绝

---

## 附录：快速参考卡

### ✅ 允许使用的 API

| API | 使用场景 |
|-----|----------|
| `taskEngine.register(config, processor)` | 注册任务 |
| `taskEngine.execute(taskName, payload)` | 执行任务 |
| `taskEngine.updateConfig(taskName, config)` | 更新配置 |
| `taskEngine.getStats()` | 查看状态 |
| `ThreadPoolTaskScheduler`（@Autowired） | 定时任务调度 |

### ❌ 禁止使用的 API

| API | 原因 |
|-----|------|
| `new Thread()` | 不受控，无监控 |
| `Executors.newXXX()` | 可能创建无界线程池 |
| `CompletableFuture.runAsync()` | 使用公共线程池 |
| `ForkJoinPool.commonPool()` | 全局共享，不可控 |

### 任务类型速查表

| 场景 | TaskType |
|------|----------|
| 启动初始化 | CPU_BOUND |
| 加密/解密 | CPU_BOUND |
| 图像压缩 | CPU_BOUND |
| HTTP 请求 | IO_BOUND |
| 数据库操作 | IO_BOUND |
| 文件读写 | IO_BOUND |
| 定时任务 | SCHEDULED |
| 批量导入导出 | BATCH |
| 日志清理 | BATCH |
