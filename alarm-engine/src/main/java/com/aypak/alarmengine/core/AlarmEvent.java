package com.aypak.alarmengine.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警事件数据结构。
 * 包含告警处理所需的所有基本信息。
 * Alarm event data structure.
 * Contains all basic information required for alarm processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmEvent {

    /** 告警唯一 ID / Unique alarm ID */
    private String id;

    /** 设备 ID - 用于分片路由，保证同一设备告警有序处理 / Device ID - used for sharding routing to ensure ordered processing of alarms from the same device */
    private String deviceId;

    /** 告警类型 / Alarm type */
    private String alarmType;

    /** 告警发生时间 / Alarm occurrence time */
    private LocalDateTime occurTime;

    /** 告警严重度：CRITICAL, MAJOR, MINOR, WARNING, INFO / Alarm severity: CRITICAL, MAJOR, MINOR, WARNING, INFO */
    private Severity severity;

    /** 告警来源系统 / Source system of the alarm */
    private String sourceSystem;

    /** 告警位置信息 / Alarm location information */
    private String location;

    /** 告警详细描述 / Alarm detailed description */
    private String description;

    /** 告警产生时的时间戳（毫秒）/ Timestamp when alarm was generated (milliseconds) */
    @Builder.Default
    private long submitTime = System.currentTimeMillis();

    /** 进入接收节点的时间戳（毫秒）/ Timestamp when entering receive node (milliseconds) */
    private Long receiveTime;

    /** 处理完成时间戳（毫秒）/ Timestamp when processing completed (milliseconds) */
    private Long completeTime;

    /** 自定义 payload 数据 / Custom payload data */
    @Builder.Default
    private Map<String, Object> payload = new ConcurrentHashMap<>();

    /** 处理状态标记 / Processing status flag */
    @Builder.Default
    private volatile ProcessingStatus status = ProcessingStatus.PENDING;

    /** 失败时的错误信息 / Error message when failed */
    private String errorMessage;

    /**
     * 添加 payload 数据。
     * Add payload data.
     *
     * @param key   键 / key
     * @param value 值 / value
     */
    public void putPayload(String key, Object value) {
        this.payload.put(key, value);
    }

    /**
     * 获取 payload 数据。
     * Get payload data by key.
     *
     * @param key 键 / key
     * @param <T> 值类型 / value type
     * @return 值 / value
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayload(String key) {
        return (T) this.payload.get(key);
    }

    /**
     * 计算处理时延（毫秒）。
     * Calculate processing latency in milliseconds.
     *
     * @return 处理时延，如果未完成则返回 -1 / processing latency, -1 if not completed
     */
    public long getProcessingLatency() {
        if (completeTime == null) {
            return -1;
        }
        return completeTime - submitTime;
    }

    /**
     * 告警严重度枚举。
     * Alarm severity enumeration.
     */
    public enum Severity {
        /** 紧急 / Critical */
        CRITICAL(5),
        /** 主要 / Major */
        MAJOR(4),
        /** 次要 / Minor */
        MINOR(3),
        /** 警告 / Warning */
        WARNING(2),
        /** 信息 / Info */
        INFO(1);

        private final int level;

        Severity(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * 处理状态枚举。
     * Processing status enumeration.
     */
    public enum ProcessingStatus {
        /** 待处理 / Pending */
        PENDING,
        /** 处理中 / Processing */
        PROCESSING,
        /** 已入库 / Persisted */
        PERSISTED,
        /** 已通知 / Notified */
        NOTIFIED,
        /** 完成 / Completed */
        COMPLETED,
        /** 失败 / Failed */
        FAILED,
        /** 已丢弃 / Dropped */
        DROPPED
    }
}
