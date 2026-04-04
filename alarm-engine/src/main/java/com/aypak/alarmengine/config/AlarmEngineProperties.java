package com.aypak.alarmengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 告警引擎配置属性
 * Alarm engine configuration properties.
 */
@Data
@ConfigurationProperties(prefix = "alarm-engine")
public class AlarmEngineProperties {

    /** 是否启用告警引擎 / Whether to enable alarm engine */
    private boolean enabled = true;

    /** Worker 数量 / Worker count */
    private int workerCount = 16;

    /** Worker 队列容量 / Worker queue capacity */
    private int workerQueueCapacity = 5000;

    /** Receiver 队列容量 / Receiver queue capacity */
    private int receiverQueueCapacity = 50000;

    /** 拒绝策略 / Rejection policy */
    private String rejectPolicy = "DROP";

    /** 批量大小 / Batch size */
    private int batchSize = 1000;

    /** 批量超时（毫秒）/ Batch timeout in milliseconds */
    private long batchTimeoutMs = 500;

    /** 优雅停机超时（秒）/ Graceful shutdown timeout in seconds */
    private long shutdownTimeoutSec = 30;

    /** 监控间隔（秒）/ Monitor interval in seconds */
    private long monitorIntervalSec = 1;
}
