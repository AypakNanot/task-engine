package com.aypak.engine.task.monitor;

import com.aypak.engine.task.executor.TaskEngine;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

/**
 * Micrometer 指标绑定器，将 TaskEngine 指标导出到 Micrometer。
 * Micrometer metrics binder for exporting TaskEngine metrics.
 *
 * <p>使用示例 / Usage example:</p>
 * <pre>{@code
 * // Spring Boot 自动配置
 * @Bean
 * public MeterBinder taskEngineMetrics(TaskEngine taskEngine) {
 *     return new TaskEngineMetricsBinder(taskEngine);
 * }
 *
 * // 非 Spring 环境
 * MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
 * TaskEngineMetricsBinder binder = new TaskEngineMetricsBinder(taskEngine);
 * binder.bindTo(registry);
 * }</pre>
 *
 * <p>导出的指标 / Exported metrics:</p>
 * <ul>
 *     <li>{@code task.engine.success.count} - 成功执行的任务数</li>
 *     <li>{@code task.engine.failure.count} - 失败执行的任务数</li>
 *     <li>{@code task.engine.qps} - 每秒查询数</li>
 *     <li>{@code task.engine.response.time} - 平均响应时间 (ms)</li>
 *     <li>{@code task.engine.queue.depth} - 队列深度</li>
 *     <li>{@code task.engine.active.threads} - 活跃线程数</li>
 *     <li>{@code task.engine.pool.size} - 线程池大小</li>
 * </ul>
 */
public class TaskEngineMetricsBinder implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(TaskEngineMetricsBinder.class);

    private static final String METRIC_PREFIX = "task.engine";

    private final TaskEngine taskEngine;

    /**
     * 创建任务引擎指标绑定器。
     * Create task engine metrics binder.
     *
     * @param taskEngine 任务引擎实例 / task engine instance
     */
    public TaskEngineMetricsBinder(TaskEngine taskEngine) {
        this.taskEngine = taskEngine;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (taskEngine == null) {
            log.warn("TaskEngine is null, skipping metrics binding");
            return;
        }

        try {
            // 初始绑定时注册所有任务的指标
            // Register metrics for all tasks on initial binding
            refreshAllMetrics(registry);

            log.info("TaskEngine metrics binder registered");
        } catch (Exception e) {
            log.error("Failed to bind TaskEngine metrics", e);
        }
    }

    /**
     * 刷新所有任务的指标。
     * Refresh metrics for all tasks.
     */
    private void refreshAllMetrics(MeterRegistry registry) {
        try {
            Map<String, TaskMetrics> stats = taskEngine.getStats();
            for (Map.Entry<String, TaskMetrics> entry : stats.entrySet()) {
                String taskName = entry.getKey();
                TaskMetrics metrics = entry.getValue();
                registerTaskMetrics(registry, taskName, metrics);
            }
        } catch (Exception e) {
            log.debug("Error refreshing metrics", e);
        }
    }

    /**
     * 注册单个任务的指标。
     * Register metrics for a single task.
     */
    private void registerTaskMetrics(MeterRegistry registry, String taskName, TaskMetrics metrics) {
        Tags tags = Tags.of(Tag.of("task.name", taskName),
                Tag.of("task.type", metrics.getTaskType().name()));

        // 成功计数 / Success count
        Gauge.builder(METRIC_PREFIX + ".success.count", metrics,
                m -> (double) m.getSuccessCount().sum())
                .tags(tags)
                .description("Successful task executions")
                .register(registry);

        // 失败计数 / Failure count
        Gauge.builder(METRIC_PREFIX + ".failure.count", metrics,
                m -> (double) m.getFailureCount().sum())
                .tags(tags)
                .description("Failed task executions")
                .register(registry);

        // QPS
        Gauge.builder(METRIC_PREFIX + ".qps", metrics,
                m -> (double) m.getQps())
                .tags(tags)
                .description("Queries per second")
                .register(registry);

        // 平均响应时间 / Average response time
        Gauge.builder(METRIC_PREFIX + ".response.time", metrics,
                m -> (double) m.getAvgResponseTime())
                .tags(tags)
                .description("Average response time in milliseconds")
                .register(registry);

        // 队列深度 / Queue depth
        Gauge.builder(METRIC_PREFIX + ".queue.depth", metrics,
                m -> (double) m.getQueueDepth().get())
                .tags(tags)
                .description("Current queue depth")
                .register(registry);

        // 活跃线程数 / Active threads
        Gauge.builder(METRIC_PREFIX + ".active.threads", metrics,
                m -> (double) m.getActiveThreads().get())
                .tags(tags)
                .description("Active thread count")
                .register(registry);

        // 线程池大小 / Pool size
        Gauge.builder(METRIC_PREFIX + ".pool.size", metrics,
                m -> (double) m.getCurrentMaxPoolSize().get())
                .tags(tags)
                .description("Thread pool size")
                .register(registry);
    }
}
