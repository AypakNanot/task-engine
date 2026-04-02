# Task Engine API Documentation

## REST API Endpoints

### Task Engine APIs

#### Submit Task
- **POST** `/api/v1/tasks/submit`
- **Description**: Submit a new task for processing
- **Request Body**:
```json
{
  "taskId": "string",
  "taskType": "COMPUTATION|IO_BOUND|REALTIME|BATCH",
  "priority": "HIGH|MEDIUM|LOW",
  "payload": "object",
  "timeoutMs": "number"
}
```
- **Response**: `202 Accepted` with task ID

#### Get Task Status
- **GET** `/api/v1/tasks/{taskId}/status`
- **Description**: Get current status of a task
- **Response**:
```json
{
  "taskId": "string",
  "status": "PENDING|PROCESSING|COMPLETED|FAILED|CANCELLED",
  "progress": "number",
  "result": "object",
  "error": "string"
}
```

#### Cancel Task
- **POST** `/api/v1/tasks/{taskId}/cancel`
- **Description**: Cancel a running or pending task
- **Response**: `200 OK`

### Alarm Engine APIs

#### Submit Alarm
- **POST** `/api/v1/alarms/submit`
- **Description**: Submit alarm data for processing
- **Request Body**:
```json
{
  "deviceId": "string",
  "alarmType": "string",
  "severity": "CRITICAL|MAJOR|MINOR|WARNING",
  "occurTime": "timestamp",
  "data": "object"
}
```
- **Response**: `202 Accepted`

#### Get Alarm Metrics
- **GET** `/api/v1/alarms/metrics`
- **Description**: Get alarm processing metrics
- **Response**:
```json
{
  "totalProcessed": "number",
  "processingRate": "number",
  "queueDepth": "number",
  "successRate": "number",
  "avgProcessingTime": "number"
}
```

### Sharded Flow Engine APIs

#### Submit Flow Event
- **POST** `/api/v1/flows/{flowName}/submit`
- **Description**: Submit event to specific flow engine
- **Request Body**:
```json
{
  "shardKey": "string",
  "payload": "object",
  "metadata": "object"
}
```
- **Response**: `202 Accepted`

#### Get Flow Metrics
- **GET** `/api/v1/flows/{flowName}/metrics`
- **Description**: Get flow processing metrics
- **Response**:
```json
{
  "flowName": "string",
  "totalEvents": "number",
  "successCount": "number",
  "failureCount": "number",
  "droppedCount": "number",
  "qps": "number",
  "avgResponseTime": "number",
  "queueDepths": ["number"]
}
```

## Configuration API

### Update Dynamic Configuration
- **PUT** `/api/v1/config/dynamic`
- **Description**: Update engine configuration at runtime
- **Request Body**:
```json
{
  "engineType": "TASK|ALARM|FLOW",
  "configUpdates": {
    "threadPoolSize": "number",
    "queueCapacity": "number",
    "batchSize": "number"
  }
}
```
- **Response**: `200 OK` with updated config

### Get Current Configuration
- **GET** `/api/v1/config/current`
- **Description**: Get current engine configuration
- **Response**:
```json
{
  "taskEngine": {
    "corePoolSize": "number",
    "maxPoolSize": "number",
    "queueCapacity": "number"
  },
  "alarmEngine": {
    "shardCount": "number",
    "batchSize": "number",
    "batchTimeoutMs": "number"
  },
  "flowEngine": {
    "defaultShardCount": "number",
    "defaultQueueCapacity": "number"
  }
}
```

## Health Check APIs

### System Health
- **GET** `/actuator/health`
- **Description**: Standard Spring Boot health check
- **Response**: Health status with details

### Engine Health
- **GET** `/api/v1/health/engines`
- **Description**: Detailed engine health status
- **Response**:
```json
{
  "taskEngine": {
    "status": "UP|DOWN",
    "activeThreads": "number",
    "queueSize": "number"
  },
  "alarmEngine": {
    "status": "UP|DOWN",
    "activeWorkers": "number",
    "pendingAlarms": "number"
  },
  "flowEngine": {
    "status": "UP|DOWN",
    "activeFlows": "number",
    "totalWorkers": "number"
  }
}
```

## Metrics Endpoints

### Prometheus Metrics
- **GET** `/actuator/prometheus`
- **Description**: Prometheus format metrics
- **Response**: Prometheus text format metrics

### Custom Metrics
- **GET** `/api/v1/metrics/custom`
- **Description**: Custom formatted metrics for monitoring
- **Response**:
```json
{
  "timestamp": "timestamp",
  "engines": {
    "task": {
      "submitted": "counter",
      "completed": "counter",
      "failed": "counter",
      "active": "gauge"
    },
    "alarm": {
      "received": "counter",
      "processed": "counter",
      "dropped": "counter",
      "queueDepth": "gauge"
    },
    "flow": {
      "events": "counter",
      "success": "counter",
      "failure": "counter",
      "dropped": "counter"
    }
  }
}
```