package com.aypak.taskengine.api;

import com.aypak.taskengine.core.DynamicConfig;
import com.aypak.taskengine.executor.TaskEngine;
import com.aypak.taskengine.executor.TaskExecutor;
import com.aypak.taskengine.executor.TaskEngineImpl;
import com.aypak.taskengine.executor.TaskRegistry;
import com.aypak.taskengine.monitor.MetricsCollector;
import com.aypak.taskengine.monitor.TaskStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * REST API for task monitoring and management.
 */
@RestController
@RequestMapping("/monitor/task")
@RequiredArgsConstructor
public class TaskMonitorController {

    private final MetricsCollector metricsCollector;
    private final TaskEngineImpl taskEngine;

    @GetMapping("/status")
    public ResponseEntity<Map<String, TaskStatsResponse>> getAllStatus() {
        Map<String, TaskStatsResponse> snapshot = metricsCollector.collectSnapshot();
        return ResponseEntity.ok(snapshot);
    }

    @GetMapping("/status/{taskName}")
    public ResponseEntity<TaskStatsResponse> getTaskStatus(@PathVariable String taskName) {
        TaskStatsResponse response = metricsCollector.collectTaskSnapshot(taskName);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/registrations")
    public ResponseEntity<Collection<TaskRegistry.TaskRegistration<?>>> getRegistrations() {
        return ResponseEntity.ok(taskEngine.getAllRegistrations());
    }

    @PutMapping("/config/{taskName}")
    public ResponseEntity<Void> updateConfig(
            @PathVariable String taskName,
            @RequestBody DynamicConfig config) {
        try {
            config.validate();
            taskEngine.updateConfig(taskName, config);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/metrics/{taskName}")
    public ResponseEntity<Void> resetTaskMetrics(@PathVariable String taskName) {
        taskEngine.resetMetrics(taskName);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/metrics")
    public ResponseEntity<Void> resetAllMetrics() {
        taskEngine.resetAllMetrics();
        return ResponseEntity.noContent().build();
    }
}