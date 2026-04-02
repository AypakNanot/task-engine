# Task Engine Examples

This directory contains complete, runnable examples demonstrating various use cases of the Task Engine.

## Example Projects

### 1. task-engine-basic-example
A simple example showing basic task submission and processing with the TaskEngine.

**Features:**
- Basic task submission
- Task status monitoring
- Simple configuration

**How to run:**
```bash
cd task-engine-basic-example
mvn spring-boot:run
```

### 2. alarm-engine-stress-test
Demonstrates high-throughput alarm processing with the AlarmEngine.

**Features:**
- High-frequency alarm submission
- Real-time metrics monitoring
- Database persistence

**How to run:**
```bash
cd alarm-engine-stress-test
mvn spring-boot:run
```

### 3. sharded-flow-order-processing
Shows how to use ShardedFlowEngine for order processing pipeline.

**Features:**
- Multi-stage order processing
- Sharding by user ID
- Error handling and retries

**How to run:**
```bash
cd sharded-flow-order-processing
mvn spring-boot:run
```

### 4. dynamic-config-example
Demonstrates runtime configuration updates.

**Features:**
- Dynamic thread pool resizing
- Queue capacity adjustment
- REST API for config management

**How to run:**
```bash
cd dynamic-config-example
mvn spring-boot:run
```

### 5. docker-compose-example
Complete deployment example with Docker Compose.

**Features:**
- Containerized deployment
- Database integration
- Monitoring setup

**How to run:**
```bash
cd docker-compose-example
docker-compose up
```

## Getting Started

1. Clone this repository
2. Navigate to the example you want to try
3. Follow the specific example's README for detailed instructions
4. Run the example and observe the Task Engine in action

Each example includes:
- Complete source code
- Configuration files
- Sample requests
- Expected outputs

These examples are designed to help you understand how to integrate Task Engine into your own applications effectively.