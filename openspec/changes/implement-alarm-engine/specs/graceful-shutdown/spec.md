# Spec: Graceful Shutdown

## ADDED Requirements

### Requirement: Shutdown Hook Registration
The shutdown hook SHALL ensure that the engine performs graceful shutdown when the JVM exits.

#### Scenario: JVM Shutdown Hook
**Given** AlarmEngine 启动完成
**When** 引擎初始化时
**Then** 注册 Runtime.getRuntime().addShutdownHook()
**And** 钩子线程中调用优雅停机逻辑

### Requirement: Stop Receiving New Alarms
Stopping the receiver SHALL prevent new alarms from entering the system during shutdown.

#### Scenario: Receiver Shutdown
**Given** 优雅停机流程启动
**When** shutdown() 方法被调用
**Then** AlarmReceiver 停止接收新告警
**And** 设置 running = false 标志

### Requirement: Wait for Worker Queue Drain
The engine SHALL wait for Worker threads to complete processing queued alarms before exiting.

#### Scenario: Worker Shutdown Signal
**Given** 优雅停机流程启动
**When** 接收器已停止
**Then** 向所有 Worker 发送停机信号
**And** Worker 停止从队列消费新数据

#### Scenario: Worker Completion Wait
**Given** Worker 正在处理队列中的告警
**When** Worker 收到停机信号
**Then** Worker 处理完当前告警后退出
**And** 调用 shutdownLatch.countDown()

### Requirement: Buffer Flush Before Exit
The buffer flush SHALL ensure that all buffered alarm data is persisted before exit.

#### Scenario: Batch Buffer Flush
**Given** 优雅停机流程中
**When** 所有 Worker 已完成
**Then** 调用 BatchDBExecutor.flush() 刷新缓冲区
**And** 确保双缓冲中的数据全部入库

#### Scenario: Remaining Queue Warning
**Given** Worker 队列中仍有告警未处理
**When** 优雅停机等待超时
**Then** 记录剩余告警数量
**And** 可选择是否强制退出

### Requirement: Shutdown Timeout
The shutdown timeout SHALL prevent indefinite blocking during graceful shutdown.

#### Scenario: 30-Second Timeout
**Given** 优雅停机流程启动
**When** 等待 Worker 完成
**Then** 最多等待 30 秒（可配置）
**And** 超时后强制关闭

#### Scenario: Timeout Exceeded Handling
**Given** 等待超过超时时间
**When** 仍有 Worker 未完成
**Then** 记录警告日志
**And** 强制执行缓冲区刷新

### Requirement: Exception Isolation During Shutdown
Exception isolation SHALL ensure that shutdown continues even if individual steps fail.

#### Scenario: Shutdown Exception Handling
**Given** 优雅停机过程中发生异常
**When** 异常在 shutdown() 方法中抛出
**Then** 异常被捕获并记录
**And** 继续执行后续停机步骤

### Requirement: Shutdown State Reporting
Shutdown state reporting SHALL provide visibility into the shutdown progress via logging.

#### Scenario: Shutdown Progress Logging
**Given** 优雅停机流程执行中
**When** 各阶段完成时
**Then** 输出日志：Starting → Receiver Stopped → Workers Completed → Buffer Flushed → Complete
**And** 便于追踪停机进度

## MODIFIED Requirements

### Requirement: Worker Shutdown Integration
The Worker Runnable SHALL integrate with the shutdown mechanism to signal completion.

#### Scenario: Worker Runnable Shutdown Logic
**Given** Worker 正在运行
**When** 收到停机信号
**Then** Worker 退出 while(running) 循环
**And** 调用 shutdownLatch.countDown()

## Related Capabilities

- `sharded-processing` - Worker 线程的停机信号处理
- `batch-persistence` - 缓冲区刷新逻辑
