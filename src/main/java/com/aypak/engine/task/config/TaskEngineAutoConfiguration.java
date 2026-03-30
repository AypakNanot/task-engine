package com.aypak.engine.task.config;

import com.aypak.engine.task.api.TaskMonitorController;
import com.aypak.engine.task.executor.DynamicScaler;
import com.aypak.engine.task.executor.TaskEngineImpl;
import com.aypak.engine.task.monitor.MetricsCollector;
import com.aypak.engine.task.monitor.QueueMonitor;
import com.aypak.engine.task.monitor.TaskEngineMetricsBinder;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 任务引擎自动配置。
 * Auto-configuration for Task Engine.
 */
@Slf4j
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(TaskEngineProperties.class)
public class TaskEngineAutoConfiguration {

    /**
     * 创建任务引擎实例。
     * Create task engine instance.
     *
     * @param properties 任务引擎属性 / task engine properties
     * @return 任务引擎实现 / task engine implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskEngineImpl taskEngine(TaskEngineProperties properties) {
        log.info("Initializing Task Engine with properties: globalMaxThreads={}, scaleFactor={}, scaleUpThreshold={}",
                properties.getGlobalMaxThreads(), properties.getScaleFactor(), properties.getScaleUpThreshold());
        return new TaskEngineImpl(properties);
    }

    /**
     * 创建指标收集器。
     * Create metrics collector.
     *
     * @param taskEngine 任务引擎 / task engine
     * @param properties 任务引擎属性 / task engine properties
     * @return 指标收集器 / metrics collector
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricsCollector metricsCollector(TaskEngineImpl taskEngine, TaskEngineProperties properties) {
        return new MetricsCollector(taskEngine, properties);
    }

    /**
     * 创建队列监控器。
     * Create queue monitor.
     *
     * @param taskEngine 任务引擎 / task engine
     * @param properties 任务引擎属性 / task engine properties
     * @return 队列监控器 / queue monitor
     */
    @Bean
    @ConditionalOnMissingBean
    public QueueMonitor queueMonitor(TaskEngineImpl taskEngine, TaskEngineProperties properties) {
        QueueMonitor monitor = new QueueMonitor(taskEngine, properties.getScaleUpThreshold());
        monitor.start(properties.getQueueMonitorInterval());
        return monitor;
    }

    /**
     * 创建动态扩展器。
     * Create dynamic scaler.
     *
     * @param taskEngine 任务引擎 / task engine
     * @param properties 任务引擎属性 / task engine properties
     * @return 动态扩展器 / dynamic scaler
     */
    @Bean
    @ConditionalOnMissingBean
    public DynamicScaler dynamicScaler(TaskEngineImpl taskEngine, TaskEngineProperties properties) {
        DynamicScaler scaler = new DynamicScaler(taskEngine, properties);
        // 每 5 秒检查一次扩展 / Check scaling every 5 seconds
        scaler.start(5000);
        return scaler;
    }

    /**
     * 创建任务监控控制器。
     * Create task monitor controller.
     *
     * @param metricsCollector 指标收集器 / metrics collector
     * @param taskEngine       任务引擎 / task engine
     * @return 任务监控控制器 / task monitor controller
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskMonitorController taskMonitorController(MetricsCollector metricsCollector, TaskEngineImpl taskEngine) {
        return new TaskMonitorController(metricsCollector, taskEngine);
    }

    /**
     * 创建任务引擎健康指示器。
     * Create task engine health indicator.
     *
     * @param taskEngine 任务引擎 / task engine
     * @return 健康指示器 / health indicator
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskEngineHealthIndicator taskEngineHealthIndicator(TaskEngineImpl taskEngine) {
        return new TaskEngineHealthIndicator(taskEngine);
    }

    /**
     * 创建 Micrometer 指标绑定器。
     * 仅在 Micrometer 可用时创建（通过 spring-boot-starter-actuator 自动引入）。
     * Create Micrometer metrics binder.
     * Only created when Micrometer is available (auto-imported via spring-boot-starter-actuator).
     *
     * @param taskEngine 任务引擎 / task engine
     * @return Micrometer 指标绑定器 / Micrometer metrics binder
     */
    @Bean
    @ConditionalOnMissingBean
    public MeterBinder taskEngineMetricsBinder(TaskEngineImpl taskEngine) {
        return new TaskEngineMetricsBinder(taskEngine);
    }
}