package com.aypak.alarmengine.core;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警事件数据结构。
 * 包含告警处理所需的所有基本信息。
 * Alarm event data structure.
 * Contains all basic information required for alarm processing.
 */
public class AlarmEvent {

    /** 告警唯一 ID / Unique alarm ID */
    private final String id;

    /** 设备 ID - 用于分片路由，保证同一设备告警有序处理 / Device ID - used for sharding routing to ensure ordered processing of alarms from the same device */
    private final String deviceId;

    /** 告警类型 / Alarm type */
    private final String alarmType;

    /** 告警发生时间 / Alarm occurrence time */
    private final LocalDateTime occurTime;

    /** 告警严重度：CRITICAL, MAJOR, MINOR, WARNING, INFO / Alarm severity: CRITICAL, MAJOR, MINOR, WARNING, INFO */
    private Severity severity;

    /** 告警来源系统 / Source system of the alarm */
    private String sourceSystem;

    /** 告警位置信息 / Alarm location information */
    private String location;

    /** 告警详细描述 / Alarm detailed description */
    private String description;

    /** 告警产生时的时间戳（毫秒）/ Timestamp when alarm was generated (milliseconds) */
    private final long submitTime;

    /** 进入接收节点的时间戳（毫秒）/ Timestamp when entering receive node (milliseconds) */
    private Long receiveTime;

    /** 处理完成时间戳（毫秒）/ Timestamp when processing completed (milliseconds) */
    private Long completeTime;

    /** 自定义 payload 数据 / Custom payload data */
    private final Map<String, Object> payload;

    /** 处理状态标记 / Processing status flag */
    private volatile ProcessingStatus status = ProcessingStatus.PENDING;

    /** 失败时的错误信息 / Error message when failed */
    private String errorMessage;

    public AlarmEvent(String id, String deviceId, String alarmType, LocalDateTime occurTime, Severity severity) {
        this.id = id;
        this.deviceId = deviceId;
        this.alarmType = alarmType;
        this.occurTime = occurTime;
        this.severity = severity;
        this.submitTime = System.currentTimeMillis();
        this.payload = new ConcurrentHashMap<>();
    }

    /**
     * 创建告警事件的 Builder。
     * Builder for creating AlarmEvent.
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    /**
     * 获取告警 ID。
     * Get alarm ID.
     */
    public String getId() { return id; }

    /**
     * 获取设备 ID。
     * Get device ID.
     */
    public String getDeviceId() { return deviceId; }

    /**
     * 获取告警类型。
     * Get alarm type.
     */
    public String getAlarmType() { return alarmType; }

    /**
     * 获取告警发生时间。
     * Get alarm occurrence time.
     */
    public LocalDateTime getOccurTime() { return occurTime; }

    /**
     * 获取告警严重度。
     * Get alarm severity.
     */
    public Severity getSeverity() { return severity; }

    /**
     * 获取告警来源系统。
     * Get source system.
     */
    public String getSourceSystem() { return sourceSystem; }

    /**
     * 获取告警位置。
     * Get location.
     */
    public String getLocation() { return location; }

    /**
     * 获取告警描述。
     * Get description.
     */
    public String getDescription() { return description; }

    /**
     * 获取提交时间。
     * Get submit time.
     */
    public long getSubmitTime() { return submitTime; }

    /**
     * 获取接收时间。
     * Get receive time.
     */
    public Long getReceiveTime() { return receiveTime; }

    /**
     * 获取完成时间。
     * Get complete time.
     */
    public Long getCompleteTime() { return completeTime; }

    /**
     * 获取 payload 数据。
     * Get payload data.
     */
    public Map<String, Object> getPayload() { return payload; }

    /**
     * 获取处理状态。
     * Get processing status.
     */
    public ProcessingStatus getStatus() { return status; }

    /**
     * 获取错误消息。
     * Get error message.
     */
    public String getErrorMessage() { return errorMessage; }

    // Setters (for pipeline processing)

    /**
     * 设置严重度。
     * Set severity.
     */
    public void setSeverity(Severity severity) { this.severity = severity; }

    /**
     * 设置来源系统。
     * Set source system.
     */
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

    /**
     * 设置位置。
     * Set location.
     */
    public void setLocation(String location) { this.location = location; }

    /**
     * 设置描述。
     * Set description.
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * 设置接收时间。
     * Set receive time.
     */
    public void setReceiveTime(Long receiveTime) { this.receiveTime = receiveTime; }

    /**
     * 设置完成时间。
     * Set complete time.
     */
    public void setCompleteTime(Long completeTime) { this.completeTime = completeTime; }

    /**
     * 设置处理状态。
     * Set processing status.
     */
    public void setStatus(ProcessingStatus status) { this.status = status; }

    /**
     * 设置错误消息。
     * Set error message.
     */
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    /**
     * 添加 payload 数据。
     * Add payload data.
     */
    public void putPayload(String key, Object value) {
        this.payload.put(key, value);
    }

    /**
     * 获取 payload 数据。
     * Get payload data by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayload(String key) {
        return (T) this.payload.get(key);
    }

    /**
     * 计算处理时延（毫秒）。
     * Calculate processing latency in milliseconds.
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

        /**
         * 获取严重度级别。
         * Get severity level.
         */
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

    /**
     * Builder pattern for AlarmEvent.
     * 告警事件的构建器模式。
     */
    public static class Builder {
        private String id;
        private String deviceId;
        private String alarmType;
        private LocalDateTime occurTime;
        private Severity severity = Severity.INFO;
        private String sourceSystem;
        private String location;
        private String description;
        private Map<String, Object> payload;

        /**
         * 设置 ID。
         * Set ID.
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * 设置设备 ID。
         * Set device ID.
         */
        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        /**
         * 设置告警类型。
         * Set alarm type.
         */
        public Builder alarmType(String alarmType) {
            this.alarmType = alarmType;
            return this;
        }

        /**
         * 设置发生时间。
         * Set occurrence time.
         */
        public Builder occurTime(LocalDateTime occurTime) {
            this.occurTime = occurTime;
            return this;
        }

        /**
         * 设置严重度。
         * Set severity.
         */
        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        /**
         * 设置来源系统。
         * Set source system.
         */
        public Builder sourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
            return this;
        }

        /**
         * 设置位置。
         * Set location.
         */
        public Builder location(String location) {
            this.location = location;
            return this;
        }

        /**
         * 设置描述。
         * Set description.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 设置 payload。
         * Set payload.
         */
        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        /**
         * 构建告警事件。
         * Build the alarm event.
         */
        public AlarmEvent build() {
            if (id == null || deviceId == null || alarmType == null || occurTime == null) {
                throw new IllegalArgumentException("Missing required fields: id, deviceId, alarmType, occurTime");
            }
            AlarmEvent event = new AlarmEvent(id, deviceId, alarmType, occurTime, severity);
            event.setSourceSystem(sourceSystem);
            event.setLocation(location);
            event.setDescription(description);
            if (payload != null) {
                event.payload.putAll(payload);
            }
            return event;
        }
    }
}
