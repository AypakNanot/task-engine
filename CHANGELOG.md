# Changelog

All notable changes to Task Engine will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **TaskEngineBuilder** - Fluent API for creating TaskEngine instances without Spring Boot auto-configuration
- **Micrometer Metrics Export** - Native support for exporting metrics to Prometheus, Graphite, and other monitoring systems
  - `task.engine.success.count` - Successful task executions
  - `task.engine.failure.count` - Failed task executions
  - `task.engine.qps` - Queries per second
  - `task.engine.response.time` - Average response time (ms)
  - `task.engine.queue.depth` - Current queue depth
  - `task.engine.active.threads` - Active thread count
  - `task.engine.pool.size` - Thread pool size
- **Task Execution Events** - Spring event publisher for task lifecycle events
  - `TaskRegisteredEvent` - Fired when a task is registered
  - `TaskSuccessEvent` - Fired when a task execution succeeds
  - `TaskFailureEvent` - Fired when a task execution fails
  - `TaskEventListener` - Sample listener for handling task events
- **Maven Publishing Support** - Configuration for publishing to Maven Central
  - `maven-source-plugin` - Generates source JAR
  - `maven-javadoc-plugin` - Generates Javadoc JAR
  - `maven-gpg-plugin` - Signs artifacts for Maven Central

### Changed

- Package structure refactored from `com.aypak.taskengine.*` to `com.aypak.engine.task.*` and `com.aypak.engine.alarm.*`
- `TaskEngineImpl` now implements `ApplicationEventPublisherAware` for event publishing

### Fixed

- Incorrect package imports in `TaskEngineImpl.java`
- Incorrect package imports in `TaskEngineAutoConfiguration.java`
- Incorrect package imports in all test classes

## [0.0.1] - 2026-03-30

### Added

- **TaskEngine** - High-performance unified task processing center
  - 250K+ QPS throughput
  - 100% success rate under sustained load
  - Four task types with physical isolation: `INIT`, `CRON`, `HIGH_FREQ`, `BACKGROUND`
  - Dynamic scaling based on queue depth
  - Four rejection policies: `ABORT_WITH_ALERT`, `CALLER_RUNS`, `BLOCK_WAIT`, `DISCARD_OLDEST`
  - Thread-safe metrics using `LongAdder` and `AtomicLong`
  - MDC context propagation for distributed tracing

- **AlarmEngine** - High-performance alarm processing engine
  - 10K+/second throughput
  - Zero external dependencies (no Kafka/Redis/MQ)
  - Strict ordering guarantee per device (DeviceID-based sharding)
  - 9-node pipeline processing:
    1. Receive - Alarm reception with backpressure protection
    2. Filter - Local filtering (duplicate/invalid alarms)
    3. Masking - Local masking rules
    4. Analysis - Business logic analysis (root cause/derivative)
    5. Persistence - Batch database insertion
    6. NB-Notify - Northbound notification preparation
    7. NB-Filter - Northbound filtering
    8. NB-Masking - Northbound masking
    9. NB-Push - HTTP push to northbound systems
  - Batch database executor with dual-buffer queue
  - Quantity (1000 records) and time (500ms) dual-trigger mechanism
  - Graceful shutdown with JVM shutdown hook

- **Spring Boot Auto-Configuration**
  - `TaskEngineAutoConfiguration` - Auto-configures TaskEngine beans
  - `TaskEngineProperties` - Configuration properties for `task-engine.*`
  - `AlarmEngineAutoConfiguration` - Auto-configures AlarmEngine beans
  - `AlarmEngineProperties` - Configuration properties for `alarm-engine.*`

- **REST API Endpoints**
  - `GET /monitor/task/status` - Get all task metrics
  - `GET /monitor/task/status/{name}` - Get specific task metrics
  - `PUT /monitor/task/config/{name}` - Update task configuration
  - `DELETE /monitor/task/metrics/{name}` - Reset specific task metrics
  - `DELETE /monitor/task/metrics` - Reset all task metrics

- **Health Check**
  - `/actuator/health` - Health indicator with task engine status

- **Documentation**
  - `TASKENGINE.md` - Comprehensive TaskEngine documentation
  - `ALARMENGINE.md` - Comprehensive AlarmEngine documentation

### Technical Details

- **Java 17** target JVM
- **Spring Boot 3.5.13** framework
- **Lombok** for boilerplate reduction
- **JUnit 5** for testing

---

## Version History

| Version | Date | Description |
|---------|------|-------------|
| 0.0.1 | 2026-03-30 | Initial release with TaskEngine and AlarmEngine |

## Contributors

- Aypak Team

## License

This project is licensed under the MIT License.
