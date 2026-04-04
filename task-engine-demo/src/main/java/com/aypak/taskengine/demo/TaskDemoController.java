package com.aypak.taskengine.demo;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.core.TaskConfig;
import com.aypak.taskengine.core.TaskType;
import com.aypak.taskengine.executor.TaskEngineImpl;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 任务引擎演示控制器。
 * 提供 4 种任务类型的测试 API 接口。
 *
 * Task Engine Demo Controller.
 * Provides test API endpoints for 4 task types.
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class TaskDemoController {

    private final TaskEngineImpl taskEngine;
    private final TaskEngineProperties properties;

    /**
     * 启动时注册任务处理器。
     * Register task processors on startup.
     */
    @PostConstruct
    public void init() {
        // 注册 CPU 密集型任务
        taskEngine.register(
            TaskConfig.builder()
                .taskName("Demo-CpuBound")
                .taskType(TaskType.CPU_BOUND)
                .build(),
            new CpuBoundTaskProcessor()
        );

        // 注册 IO 密集型任务
        taskEngine.register(
            TaskConfig.builder()
                .taskName("Demo-IoBound")
                .taskType(TaskType.IO_BOUND)
                .build(),
            new IoBoundTaskProcessor()
        );

        // 注册混合型任务
        taskEngine.register(
            TaskConfig.builder()
                .taskName("Demo-Hybrid")
                .taskType(TaskType.HYBRID)
                .build(),
            new HybridTaskProcessor()
        );

        // 注册批量处理任务
        taskEngine.register(
            TaskConfig.builder()
                .taskName("Demo-Batch")
                .taskType(TaskType.BATCH)
                .build(),
            new BatchTaskProcessor()
        );

        log.info("Demo task processors registered");
    }

    /**
     * 执行 CPU 密集型任务。
     * Execute CPU-bound tasks.
     *
     * @param request 任务执行请求 / task execution request
     * @return 执行响应 / execution response
     */
    @PostMapping("/cpu")
    public ResponseEntity<TaskExecutionResponse> executeCpuTask(@RequestBody(required = false) TaskExecutionRequest request) {
        if (request == null) {
            request = new TaskExecutionRequest();
        }

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        log.info("Executing CPU-bound task: {}, count: {}, duration: {}ms",
                taskId, request.getCount(), request.getDurationMs());

        // 执行指定次数的任务
        for (int i = 0; i < request.getCount(); i++) {
            CpuBoundTaskProcessor.CpuTask payload = new CpuBoundTaskProcessor.CpuTask(
                    request.getDurationMs(), i);
            taskEngine.execute("Demo-CpuBound", payload);
        }

        return ResponseEntity.ok(TaskExecutionResponse.builder()
                .taskType("CPU_BOUND")
                .taskName("Demo-CpuBound")
                .count(request.getCount())
                .status("submitted")
                .message(String.format("Submitted %d CPU-bound task(s)", request.getCount()))
                .build());
    }

    /**
     * 执行 IO 密集型任务。
     * Execute IO-bound tasks.
     *
     * @param request 任务执行请求 / task execution request
     * @return 执行响应 / execution response
     */
    @PostMapping("/io")
    public ResponseEntity<TaskExecutionResponse> executeIoTask(@RequestBody(required = false) TaskExecutionRequest request) {
        if (request == null) {
            request = new TaskExecutionRequest();
        }

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        log.info("Executing IO-bound task: {}, count: {}, duration: {}ms",
                taskId, request.getCount(), request.getDurationMs());

        for (int i = 0; i < request.getCount(); i++) {
            IoBoundTaskProcessor.IoTask payload = new IoBoundTaskProcessor.IoTask(
                    request.getDurationMs(), i);
            taskEngine.execute("Demo-IoBound", payload);
        }

        return ResponseEntity.ok(TaskExecutionResponse.builder()
                .taskType("IO_BOUND")
                .taskName("Demo-IoBound")
                .count(request.getCount())
                .status("submitted")
                .message(String.format("Submitted %d IO-bound task(s)", request.getCount()))
                .build());
    }

    /**
     * 执行混合型任务。
     * Execute hybrid tasks.
     *
     * @param request 任务执行请求 / task execution request
     * @return 执行响应 / execution response
     */
    @PostMapping("/hybrid")
    public ResponseEntity<TaskExecutionResponse> executeHybridTask(@RequestBody(required = false) TaskExecutionRequest request) {
        if (request == null) {
            request = new TaskExecutionRequest();
        }

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        log.info("Executing Hybrid task: {}, count: {}, duration: {}ms",
                taskId, request.getCount(), request.getDurationMs());

        for (int i = 0; i < request.getCount(); i++) {
            HybridTaskProcessor.HybridTask payload = new HybridTaskProcessor.HybridTask(
                    request.getDurationMs(), i);
            taskEngine.execute("Demo-Hybrid", payload);
        }

        return ResponseEntity.ok(TaskExecutionResponse.builder()
                .taskType("HYBRID")
                .taskName("Demo-Hybrid")
                .count(request.getCount())
                .status("submitted")
                .message(String.format("Submitted %d hybrid task(s)", request.getCount()))
                .build());
    }

    /**
     * 执行批量处理任务。
     * Execute batch tasks.
     *
     * @param recordCount 记录数量 / record count
     * @param count 任务执行次数 / task execution count
     * @return 执行响应 / execution response
     */
    @PostMapping("/batch")
    public ResponseEntity<TaskExecutionResponse> executeBatchTask(
            @RequestParam(defaultValue = "1000") int recordCount,
            @RequestParam(defaultValue = "1") int count) {

        String taskId = UUID.randomUUID().toString().substring(0, 8);
        log.info("Executing Batch task: {}, count: {}, records: {}",
                taskId, count, recordCount);

        for (int i = 0; i < count; i++) {
            BatchTaskProcessor.BatchTask payload = new BatchTaskProcessor.BatchTask(
                    recordCount, i);
            taskEngine.execute("Demo-Batch", payload);
        }

        return ResponseEntity.ok(TaskExecutionResponse.builder()
                .taskType("BATCH")
                .taskName("Demo-Batch")
                .count(count)
                .status("submitted")
                .message(String.format("Submitted %d batch task(s) with %d records each", count, recordCount))
                .build());
    }

    /**
     * 获取演示 API 使用说明。
     * Get demo API usage instructions.
     *
     * @return API 使用说明 / API usage instructions
     */
    @GetMapping("/help")
    public ResponseEntity<String> getHelp() {
        String help = """
                Task Engine Demo API

                Available endpoints:

                1. POST /api/demo/cpu
                   Execute CPU-bound tasks
                   Body: {"count": 1, "durationMs": 100}

                2. POST /api/demo/io
                   Execute IO-bound tasks
                   Body: {"count": 1, "durationMs": 100}

                3. POST /api/demo/hybrid
                   Execute hybrid tasks
                   Body: {"count": 1, "durationMs": 100}

                4. POST /api/demo/batch
                   Execute batch tasks
                   Params: recordCount=1000, count=1

                Monitor task status:
                - GET /monitor/task/status - All tasks
                - GET /monitor/task/pools - Thread pools
                """;
        return ResponseEntity.ok(help);
    }
}
