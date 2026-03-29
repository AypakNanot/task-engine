package com.aypak.taskengine.alarm.core;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警事件数据结构
 * 包含告警处理所需的所有基本信息
 */
public class AlarmEvent {

    /** 告警唯一 ID */
    private final String id;

    /** 设备 ID - 用于分片路由，保证同一设备告警有序处理 */
    private final String deviceId;

    /** 告警类型 */
    private final String alarmType;

    /** 告警发生时间 */
    private final LocalDateTime occurTime;

    /** 告警严重度：CRITICAL, MAJOR, MINOR, WARNING, INFO */
    private Severity severity;

    /** 告警来源系统 */
    private String sourceSystem;

    /** 告警位置信息 */
    private String location;

    /** 告警详细描述 */
    private String description;

    /** 告警产生时的时间戳（毫秒）*/
    private final long submitTime;

    /** 进入接收节点的时间戳（毫秒）*/
    private Long receiveTime;

    /** 处理完成时间戳（毫秒）*/
    private Long completeTime;

    /** 自定义 payload 数据 */
    private final Map<String, Object> payload;

    /** 处理状态标记 */
    private volatile ProcessingStatus status = ProcessingStatus.PENDING;

    /** 失败时的错误信息 */
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
     * 创建告警事件的 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getAlarmType() { return alarmType; }
    public LocalDateTime getOccurTime() { return occurTime; }
    public Severity getSeverity() { return severity; }
    public String getSourceSystem() { return sourceSystem; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public long getSubmitTime() { return submitTime; }
    public Long getReceiveTime() { return receiveTime; }
    public Long getCompleteTime() { return completeTime; }
    public Map<String, Object> getPayload() { return payload; }
    public ProcessingStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }

    // Setters (for pipeline processing)
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public void setLocation(String location) { this.location = location; }
    public void setDescription(String description) { this.description = description; }
    public void setReceiveTime(Long receiveTime) { this.receiveTime = receiveTime; }
    public void setCompleteTime(Long completeTime) { this.completeTime = completeTime; }
    public void setStatus(ProcessingStatus status) { this.status = status; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    /**
     * 添加 payload 数据
     */
    public void putPayload(String key, Object value) {
        this.payload.put(key, value);
    }

    /**
     * 获取 payload 数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayload(String key) {
        return (T) this.payload.get(key);
    }

    /**
     * 计算处理时延（毫秒）
     */
    public long getProcessingLatency() {
        if (completeTime == null) {
            return -1;
        }
        return completeTime - submitTime;
    }

    /**
     * 告警严重度枚举
     */
    public enum Severity {
        CRITICAL(5),
        MAJOR(4),
        MINOR(3),
        WARNING(2),
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
     * 处理状态枚举
     */
    public enum ProcessingStatus {
        PENDING,        // 待处理
        PROCESSING,     // 处理中
        PERSISTED,      // 已入库
        NOTIFIED,       // 已通知
        COMPLETED,      // 完成
        FAILED,         // 失败
        DROPPED         // 已丢弃
    }

    /**
     * Builder pattern for AlarmEvent
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

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder alarmType(String alarmType) {
            this.alarmType = alarmType;
            return this;
        }

        public Builder occurTime(LocalDateTime occurTime) {
            this.occurTime = occurTime;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder sourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

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
