# Spec: Dynamic Scaling

## ADDED Requirements

### Requirement: Threshold-Based Scale-Up
Thread pools must automatically scale up when queue pressure detected.

#### Scenario: Scale-up trigger conditions
**Given** a pool with `queueCapacity=1000` and `scaleUpThreshold=80%`
**And** `queueDepth > 800` (threshold exceeded)
**And** `activeThreads == maxPoolSize` (pool fully utilized)
**And** `currentMaxPoolSize < globalMaxLimit`
**When** scaling monitor evaluates conditions
**Then** `maxPoolSize` increases by `scaleFactor`
**And** scaling event is logged

#### Scenario: Scale-up blocked by global limit
**Given** pool wants to scale up
**And** `globalMaxThreads=200` is already reached across all pools
**When** scaling monitor evaluates
**Then** no scale-up occurs
**And** warning log indicates global limit reached

---

### Requirement: Idle-Based Scale-Down
Thread pools must automatically scale down when idle for configured duration.

#### Scenario: Scale-down trigger conditions
**Given** pool was scaled up to 16 threads during spike
**And** spike has passed
**And** `idleTime > idleTimeout` (default 60 seconds)
**And** `currentMaxPoolSize > originalMaxPoolSize`
**When** scaling monitor evaluates conditions
**Then** `maxPoolSize` decreases by `scaleFactor`
**And** excess threads are recycled
**And** scaling event is logged

#### Scenario: Scale-down preserves minimum
**Given** pool is at `originalMaxPoolSize=4`
**And** idle timeout has passed
**When** scaling monitor evaluates
**Then** no scale-down occurs
**And** pool maintains original configuration

---

### Requirement: Dynamic Configuration Update
Thread pool parameters must be updatable at runtime via API.

#### Scenario: Update core pool size
**Given** task "AlertProcessor" has `corePoolSize=8`
**When** `PUT /task/config/AlertProcessor` with `{"corePoolSize": 16}`
**Then** pool core size updates to 16
**And** existing threads below new core size are retained
**And** success response returned

#### Scenario: Update queue capacity
**Given** task has `queueCapacity=1000`
**When** update request changes `queueCapacity=5000`
**Then** queue capacity updates
**And** existing queued tasks preserved

#### Scenario: Invalid configuration update
**Given** update request has `corePoolSize < 0`
**When** API processes request
**Then** 400 error returned
**And** error message indicates invalid value

---

### Requirement: Rejection Policy Control
Different tasks must support configurable rejection policies.

#### Scenario: ABORT_WITH_ALERT policy
**Given** task "CriticalAlert" has rejection policy `ABORT_WITH_ALERT`
**And** queue is full
**When** new task submitted
**Then** task is dropped
**And** error log with alert generated
**And** failure metric incremented

#### Scenario: CALLER_RUNS policy
**Given** task "DataCleanup" has rejection policy `CALLER_RUNS`
**And** queue is full
**When** new task submitted
**Then** task executes in caller thread
**And** no task dropped

#### Scenario: DISCARD_OLDEST policy
**Given** task has rejection policy `DISCARD_OLDEST`
**And** queue is full
**When** new task submitted
**Then** oldest task in queue is removed
**And** new task is queued

#### Scenario: BLOCK_WAIT policy
**Given** task has rejection policy `BLOCK_WAIT`
**And** queue is full
**When** new task submitted
**Then** caller thread blocks until space available
**And** task eventually queued (bounded wait time configurable)

---

### Requirement: Global Thread Limit
System-wide thread limit prevents memory exhaustion.

#### Scenario: Global limit enforcement
**Given** `globalMaxThreads=200`
**And** current total threads = 195
**When** new pool wants to add 10 threads
**Then** only 5 threads are added
**And** warning log indicates limit constraint

#### Scenario: Global limit configuration
**Given** system starts with default `globalMaxThreads=200`
**When** `application.yml` sets `task-engine.global-max-threads=100`
**Then** global limit becomes 100
**And** scaling respects this limit