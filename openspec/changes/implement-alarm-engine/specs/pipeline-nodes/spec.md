# Spec: Pipeline Nodes

## ADDED Requirements

### Requirement: 9-Node Pipeline Architecture
The alarm processing pipeline SHALL consist of 9 nodes that MUST process each alarm sequentially.

#### Scenario: Complete Pipeline Execution
**Given** 一个合法告警进入处理引擎
**When** 告警通过流水线处理
**Then** 必须依次通过 9 个节点：Receive → Filter → Masking → Analysis → Persistence → NB-Notify → NB-Filter → NB-Masking → NB-Push
**And** 节点执行顺序不可跳过或颠倒

#### Scenario: Node Interface Compliance
**Given** 所有处理节点实现 PipelineNode 接口
**When** 节点被调用时
**Then** 必须实现 getNodeName() 和 process(AlarmEvent, PipelineContext) 方法
**And** 返回 boolean 表示是否继续传递到下一节点

### Requirement: Receive Node (接收节点)
The Receive node SHALL validate incoming alarm data and MUST record the reception timestamp.

#### Scenario: Alarm Validation
**Given** 告警数据进入接收节点
**When** 告警包含必填字段（deviceId, alarmType, occurTime）
**Then** 告警通过校验进入下一节点
**And** 记录接收时间戳

#### Scenario: Invalid Alarm Rejection
**Given** 告警数据缺少必填字段或格式错误
**When** 告警进入接收节点
**Then** 告警被拒绝，不进入后续节点
**And** 记录失败指标

### Requirement: Filter Node (本地过滤节点)
The Filter node SHALL filter out invalid or unwanted alarms based on configurable rules.

#### Scenario: Invalid Alarm Filtering
**Given** 配置了本地过滤规则
**When** 告警匹配过滤规则（如测试告警、已恢复告警）
**Then** 告警被过滤，不进入后续节点
**And** 记录过滤计数

### Requirement: Masking Node (本地屏蔽节点)
The Masking node SHALL suppress alarms based on device-level, type-level, or global masking rules.

#### Scenario: Device-Level Masking
**Given** 存在设备级屏蔽规则
**When** 告警的 DeviceID 匹配屏蔽规则
**Then** 告警被屏蔽，不进入后续节点
**And** 记录屏蔽计数

#### Scenario: Global Masking Rule
**Given** 存在全局屏蔽规则
**When** 告警匹配全局规则（如特定告警类型）
**Then** 告警被屏蔽，不进入后续节点

### Requirement: Analysis Node (业务分析节点)
The Analysis node SHALL perform business logic analysis such as severity calculation.

#### Scenario: Severity Calculation
**Given** 告警进入分析节点
**When** 根据告警内容计算严重度
**Then** 严重度结果存入 PipelineContext
**And** 传递给后续节点

### Requirement: Persistence Node (持久化节点)
The Persistence node SHALL submit alarm data to the BatchDBExecutor for batch database insertion.

#### Scenario: Batch Submission
**Given** 告警通过前序节点
**When** 告警进入持久化节点
**Then** 告警被提交到 BatchDBExecutor
**And** 标记为"待入库"状态

### Requirement: NB-Notify Node (北向通知准备)
The NB-Notify node SHALL prepare alarm notifications for northbound systems by converting formats.

#### Scenario: Notification Format Conversion
**Given** 告警需要推送到北向系统
**When** 告警进入 NB-Notify 节点
**Then** 告警转换为北向接口格式
**And** 通知内容存入上下文

### Requirement: NB-Filter Node (北向过滤)
The NB-Filter node SHALL filter alarms based on customer subscription rules.

#### Scenario: Subscription-Based Filtering
**Given** 配置了客户订阅规则
**When** 告警类型不在客户订阅列表中
**Then** 告警被过滤，不推送
**And** 记录过滤原因

### Requirement: NB-Masking Node (北向屏蔽)
The NB-Masking node SHALL suppress alarms based on customer-specific masking rules.

#### Scenario: Customer Masking Rule
**Given** 客户配置了屏蔽规则
**When** 告警匹配客户屏蔽规则
**Then** 告警被屏蔽，不推送

### Requirement: NB-Push Node (北向推送)
The NB-Push node SHALL push alarms to northbound systems via HTTP with retry support.

#### Scenario: HTTP Push Execution
**Given** 告警通过所有前序节点
**When** 告警进入 NB-Push 节点
**Then** 通过 HTTP 推送告警到北向系统
**And** 记录推送结果（成功/失败）

#### Scenario: Push Retry Mechanism
**Given** 推送失败且配置了重试次数
**When** 推送失败
**Then** 按配置次数重试
**And** 重试失败后记录日志

### Requirement: Node Exception Handling
Exception handling SHALL ensure that node failures MUST be properly contained based on node criticality.

#### Scenario: Critical Node Failure
**Given** 关键节点（Receive, Analysis, Persistence）处理失败
**When** 节点抛出异常
**Then** 异常向上传播，告警处理终止
**And** 记录失败指标

#### Scenario: Non-Critical Node Failure
**Given** 非关键节点（Filter, Masking, NB-*）处理失败
**When** 节点抛出异常
**Then** 异常被捕获，告警继续传递
**And** 记录节点失败日志

## Related Capabilities

- `sharded-processing` - 流水线在 Worker 线程中执行
- `batch-persistence` - Persistence 节点使用批量入库
