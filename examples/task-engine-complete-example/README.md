# Task Engine - Complete End-to-End Example

This example demonstrates a complete implementation of all three engines (TaskEngine, AlarmEngine, and ShardedFlowEngine) working together.

## Project Structure

```
task-engine-complete-example/
├── pom.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/taskengine/
│       │       ├── TaskEngineCompleteExampleApplication.java
│       │       ├── config/
│       │       │   └── EngineConfiguration.java
│       │       ├── controller/
│       │       │   ├── TaskController.java
│       │       │   ├── AlarmController.java
│       │       │   └── FlowController.java
│       │       ├── service/
│       │       │   ├── TaskProcessingService.java
│       │       │   ├── AlarmProcessingService.java
│       │       │   └── OrderFlowService.java
│       │       └── model/
│       │           ├── TaskRequest.java
│       │           ├── AlarmData.java
│       │           └── OrderEvent.java
│       └── resources/
│           ├── application.yml
│           └── schema.sql
└── README.md
```

## Features Demonstrated

### TaskEngine Usage
- Submitting computational tasks
- Handling different task priorities
- Monitoring task completion status

### AlarmEngine Usage  
- Processing device alarms with strict ordering
- Batch database persistence
- Northbound notification system integration

### ShardedFlowEngine Usage
- Multi-stage order processing pipeline
- Sharding by customer ID for ordered processing
- Custom flow node implementations

### Integration Points
- Shared monitoring dashboard
- Unified configuration management
- Common error handling patterns

## How to Run

1. **Database Setup**
   ```bash
   # Create database (PostgreSQL example)
   createdb task_engine_example
   ```

2. **Build and Run**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

3. **Test Endpoints**

   **Submit Task:**
   ```bash
   curl -X POST http://localhost:8080/api/tasks/submit \
     -H "Content-Type: application/json" \
     -d '{"taskId":"test-001","taskType":"COMPUTATION","priority":"HIGH","payload":{"data":"test"}}'
   ```

   **Submit Alarm:**
   ```bash
   curl -X POST http://localhost:8080/api/alarms/submit \
     -H "Content-Type: application/json" \
     -d '{"deviceId":"device-123","alarmType":"TEMPERATURE_HIGH","severity":"CRITICAL","occurTime":1640995200000}'
   ```

   **Submit Flow Event:**
   ```bash
   curl -X POST http://localhost:8080/api/flows/order-processing/submit \
     -H "Content-Type: application/json" \
     -d '{"shardKey":"customer-456","payload":{"orderId":"order-789","amount":99.99}}'
   ```

4. **Monitor Metrics**
   ```bash
   # View Prometheus metrics
   curl http://localhost:8080/actuator/prometheus
   
   # View custom metrics
   curl http://localhost:8080/api/metrics/custom
   ```

## Configuration Highlights

### application.yml
```yaml
task-engine:
  task:
    core-pool-size: 10
    max-pool-size: 50
    queue-capacity: 1000
  alarm:
    shard-count: 8
    batch-size: 100
    batch-timeout-ms: 1000
  flow:
    default-shard-count: 4
    default-queue-capacity: 500

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

This complete example serves as a reference implementation showing how to integrate all Task Engine components into a production-ready application.