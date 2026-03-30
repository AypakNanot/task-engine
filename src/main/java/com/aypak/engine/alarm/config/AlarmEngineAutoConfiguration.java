package com.aypak.engine.alarm.config;

import com.aypak.engine.alarm.core.RejectPolicy;
import com.aypak.engine.alarm.engine.AlarmEngine;
import com.aypak.engine.alarm.engine.AlarmEngineImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 告警引擎自动配置
 * 只有在存在 DataSource Bean 且启用时才生效
 * Alarm engine auto configuration.
 * Only生效 when DataSource Bean exists and is enabled.
 */
@Configuration
@EnableConfigurationProperties(AlarmEngineProperties.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "alarm-engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AlarmEngineAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlarmEngineAutoConfiguration.class);

    /**
     * 创建告警引擎 Bean
     * Create alarm engine Bean.
     */
    @Bean
    public AlarmEngine alarmEngine(DataSource dataSource, AlarmEngineProperties properties) {
        log.info("Initializing AlarmEngine with properties: workerCount={}, workerQueueCapacity={}, " +
                        "rejectPolicy={}, batchSize={}, batchTimeoutMs={}ms",
                properties.getWorkerCount(),
                properties.getWorkerQueueCapacity(),
                properties.getRejectPolicy(),
                properties.getBatchSize(),
                properties.getBatchTimeoutMs());

        // 创建告警引擎
        // Create alarm engine
        AlarmEngineImpl engine = new AlarmEngineImpl(
                dataSource,
                getInsertSql(),
                properties.getWorkerCount(),
                properties.getWorkerQueueCapacity(),
                parseRejectPolicy(properties.getRejectPolicy())
        );

        // 启动引擎
        // Start engine
        engine.start();

        log.info("AlarmEngine initialized and started");

        return engine;
    }

    /**
     * 获取默认插入 SQL
     * 可通过配置覆盖
     * Get default insert SQL.
     * Can be overridden by configuration.
     */
    private String getInsertSql() {
        // 默认 SQL，可根据实际表结构修改
        // Default SQL, can be modified according to actual table structure
        return "INSERT INTO alarm_event " +
                "(device_id, alarm_type, occur_time, severity, source_system, location, description, submit_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    /**
     * 解析拒绝策略
     * Parse rejection policy.
     */
    private RejectPolicy parseRejectPolicy(String policyStr) {
        try {
            return RejectPolicy.valueOf(policyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid reject policy: {}, defaulting to DROP", policyStr);
            return RejectPolicy.DROP;
        }
    }
}
