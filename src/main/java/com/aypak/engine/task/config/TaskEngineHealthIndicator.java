package com.aypak.engine.task.config;

import com.aypak.engine.task.executor.TaskEngineImpl;
import com.aypak.engine.task.executor.TaskExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Map;

/**
 * 任务引擎的 Spring Boot Actuator 健康指示器。
 * Spring Boot Actuator health indicator for Task Engine.
 */
@RequiredArgsConstructor
public class TaskEngineHealthIndicator implements HealthIndicator {

    private final TaskEngineImpl taskEngine;
    private static final double QUEUE_WARNING_THRESHOLD = 90.0;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        Map<String, TaskExecutor> executors = taskEngine.getExecutors();
        int totalTasks = executors.size();
        int healthyTasks = 0;
        int warningTasks = 0;
        int failedTasks = 0;

        for (Map.Entry<String, TaskExecutor> entry : executors.entrySet()) {
            String taskName = entry.getKey();
            TaskExecutor executor = entry.getValue();

            int queueDepth = executor.getQueueSize();
            int queueCapacity = executor.getQueueCapacity();

            double utilization = queueCapacity > 0 ? (queueDepth * 100.0) / queueCapacity : 0;

            builder.withDetail(taskName + ".queueDepth", queueDepth);
            builder.withDetail(taskName + ".queueCapacity", queueCapacity);
            builder.withDetail(taskName + ".activeThreads", executor.getActiveThreads());

            if (executor.isTerminated()) {
                failedTasks++;
                builder.withDetail(taskName + ".status", "DOWN");
            } else if (utilization >= QUEUE_WARNING_THRESHOLD) {
                warningTasks++;
                builder.withDetail(taskName + ".status", "WARNING");
            } else {
                healthyTasks++;
                builder.withDetail(taskName + ".status", "UP");
            }
        }

        builder.withDetail("totalTasks", totalTasks);
        builder.withDetail("healthyTasks", healthyTasks);
        builder.withDetail("warningTasks", warningTasks);
        builder.withDetail("failedTasks", failedTasks);

        // 整体状态 / Overall status
        if (failedTasks > 0) {
            builder = Health.down().withDetail("reason", "Some pools are terminated");
        } else if (warningTasks > 0) {
            builder = Health.status("WARNING").withDetail("reason", "Some pools have queue overflow");
        }

        return builder.build();
    }
}