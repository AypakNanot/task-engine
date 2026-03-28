# Spec: Task Monitoring

## ADDED Requirements

### Requirement: Real-Time Metrics Collection
Task execution metrics must be collected and updated atomically for each task.

#### Scenario: Success metrics update
**Given** a task execution completes successfully
**When** execution finishes
**Then** `successCount` increments atomically
**And** `avgResponseTime` updates with EWMA algorithm
**And** execution time is recorded in milliseconds

#### Scenario: Failure metrics update
**Given** a task execution fails with exception
**When** exception is caught
**Then** `failureCount` increments atomically
**And** failure callback is invoked if defined

#### Scenario: QPS calculation
**Given** tasks are executing continuously
**When** metrics are queried
**Then** `currentQps` returns sliding window average
**And** window size is configurable (default 60 seconds)

---

### Requirement: Queue Depth Monitoring
System must monitor queue depth and trigger alerts when threshold exceeded.

#### Scenario: Queue depth below threshold
**Given** queue has 500 tasks and threshold is 80% of 1000 capacity
**When** queue depth monitor polls
**Then** no alert is triggered
**And** queue depth metric is updated to 500

#### Scenario: Queue depth exceeds threshold
**Given** queue has 850 tasks and threshold is 80% of 1000 capacity
**When** queue depth monitor polls
**Then** warning alert is logged
**And** alert contains task name and queue depth
**And** alert format: `[TaskName] Queue depth: 850/1000 (85% > threshold 80%)`

---

### Requirement: Resource Monitoring
Thread pool resource usage must be tracked and reported.

#### Scenario: Active thread count tracking
**Given** a pool has 10 threads executing tasks
**When** metrics are queried
**Then** `activeThreads` returns 10
**And** count is accurate at query time

#### Scenario: Peak thread count tracking
**Given** pool scaled from 8 to 16 threads during load spike
**When** metrics are queried
**Then** `peakThreads` returns 16
**And** peak value persists until manually reset

---

### Requirement: Monitoring REST API
Metrics must be accessible via REST endpoints.

#### Scenario: Get all task status
**Given** multiple tasks are registered and executing
**When** `GET /monitor/task/status` is called
**Then** response returns JSON array of all task metrics
**And** each task includes: name, type, QPS, avgRT, successCount, failureCount, queueDepth, activeThreads

#### Scenario: Get specific task status
**Given** task "AlertProcessor" is registered
**When** `GET /monitor/task/status/AlertProcessor` is called
**Then** response returns JSON for that specific task
**And** 404 returned if task not found

#### Scenario: Reset task metrics
**Given** task has accumulated metrics
**When** `DELETE /monitor/task/metrics/{taskName}` is called
**Then** that task's metrics reset to zero
**And** peak values reset to current state

#### Scenario: Reset all metrics
**Given** all tasks have accumulated metrics
**When** `DELETE /monitor/task/metrics` is called
**Then** all task metrics reset to zero