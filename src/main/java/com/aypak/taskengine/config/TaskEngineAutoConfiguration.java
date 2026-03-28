package com.aypak.taskengine.config;

import com.aypak.taskengine.api.TaskMonitorController;
import com.aypak.taskengine.executor.*;
import com.aypak.taskengine.monitor.MetricsCollector;
import com.aypak.taskengine.monitor.QueueMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Auto-configuration for Task Engine.
 */
@Slf4j
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(TaskEngineProperties.class)
public class TaskEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TaskEngineImpl taskEngine(TaskEngineProperties properties) {
        log.info("Initializing Task Engine with properties: globalMaxThreads={}, scaleFactor={}, scaleUpThreshold={}",
                properties.getGlobalMaxThreads(), properties.getScaleFactor(), properties.getScaleUpThreshold());
        return new TaskEngineImpl(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricsCollector metricsCollector(TaskEngineImpl taskEngine, TaskEngineProperties properties) {
        return new MetricsCollector(taskEngine, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public QueueMonitor queueMonitor(TaskEngineImpl taskEngine, TaskEngineProperties properties) {
        QueueMonitor monitor = new QueueMonitor(taskEngine, properties.getScaleUpThreshold());
        monitor.start(properties.getQueueMonitorInterval());
        return monitor;
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicScaler dynamicScaler(TaskEngineImpl taskEngine, TaskEngineProperties properties) {
        DynamicScaler scaler = new DynamicScaler(taskEngine, properties);
        // Check scaling every 5 seconds
        scaler.start(5000);
        return scaler;
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskMonitorController taskMonitorController(MetricsCollector metricsCollector, TaskEngineImpl taskEngine) {
        return new TaskMonitorController(metricsCollector, taskEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskEngineHealthIndicator taskEngineHealthIndicator(TaskEngineImpl taskEngine) {
        return new TaskEngineHealthIndicator(taskEngine);
    }
}