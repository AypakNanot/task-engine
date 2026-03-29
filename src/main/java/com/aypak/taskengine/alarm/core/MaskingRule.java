package com.aypak.taskengine.alarm.core;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 告警屏蔽规则
 * 用于过滤不需要处理或推送的告警
 */
public class MaskingRule {

    /** 规则唯一 ID */
    private final String id;

    /** 规则名称 */
    private final String name;

    /** 目标类型：DEVICE, ALARM_TYPE, GLOBAL */
    private final TargetType targetType;

    /** 目标 ID（设备 ID 或告警类型）*/
    private final String targetId;

    /** 是否启用 */
    private boolean enabled;

    /** 生效开始时间 */
    private final LocalDateTime startTime;

    /** 生效结束时间 */
    private final LocalDateTime endTime;

    /** 告警类型匹配表达式（支持通配符）*/
    private final String alarmTypePattern;

    /** 严重度匹配（null 表示匹配所有）*/
    private final AlarmEvent.Severity minSeverity;

    /** 规则描述 */
    private final String description;

    /** 创建时间 */
    private final LocalDateTime createTime;

    public MaskingRule(String id, String name, TargetType targetType, String targetId,
                       LocalDateTime startTime, LocalDateTime endTime,
                       String alarmTypePattern, AlarmEvent.Severity minSeverity, String description) {
        this.id = id;
        this.name = name;
        this.targetType = targetType;
        this.targetId = targetId;
        this.enabled = true;
        this.startTime = startTime;
        this.endTime = endTime;
        this.alarmTypePattern = alarmTypePattern;
        this.minSeverity = minSeverity;
        this.description = description;
        this.createTime = LocalDateTime.now();
    }

    /**
     * 创建屏蔽规则的 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public TargetType getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getAlarmTypePattern() { return alarmTypePattern; }
    public AlarmEvent.Severity getMinSeverity() { return minSeverity; }
    public String getDescription() { return description; }
    public LocalDateTime getCreateTime() { return createTime; }

    /**
     * 设置规则是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 检查规则是否匹配给定告警
     * @param event 告警事件
     * @return true 表示匹配，告警应被屏蔽
     */
    public boolean matches(AlarmEvent event) {
        if (!enabled) {
            return false;
        }

        // 检查时间范围
        LocalDateTime now = LocalDateTime.now();
        if (startTime != null && now.isBefore(startTime)) {
            return false;
        }
        if (endTime != null && now.isAfter(endTime)) {
            return false;
        }

        // 检查严重度
        if (minSeverity != null && event.getSeverity().getLevel() < minSeverity.getLevel()) {
            return false;
        }

        // 检查告警类型匹配
        if (alarmTypePattern != null && !matchesAlarmType(event.getAlarmType())) {
            return false;
        }

        return true;
    }

    /**
     * 检查告警类型是否匹配模式
     * 支持通配符 * 和 ?
     */
    private boolean matchesAlarmType(String alarmType) {
        if (alarmTypePattern == null) {
            return true;
        }
        if (alarmType == null) {
            return false;
        }

        // 简单通配符匹配
        if (alarmTypePattern.equals("*")) {
            return true;
        }
        if (alarmTypePattern.endsWith("*")) {
            String prefix = alarmTypePattern.substring(0, alarmTypePattern.length() - 1);
            return alarmType.startsWith(prefix);
        }
        if (alarmTypePattern.startsWith("*")) {
            String suffix = alarmTypePattern.substring(1);
            return alarmType.endsWith(suffix);
        }
        return alarmType.equals(alarmTypePattern);
    }

    /**
     * 目标类型枚举
     */
    public enum TargetType {
        /** 全局规则，应用于所有告警 */
        GLOBAL,
        /** 设备级规则，应用于特定设备 */
        DEVICE,
        /** 告警类型级规则，应用于特定告警类型 */
        ALARM_TYPE
    }

    /**
     * Builder pattern for MaskingRule
     */
    public static class Builder {
        private String id;
        private String name;
        private TargetType targetType = TargetType.GLOBAL;
        private String targetId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String alarmTypePattern;
        private AlarmEvent.Severity minSeverity;
        private String description;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder targetType(TargetType targetType) {
            this.targetType = targetType;
            return this;
        }

        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder alarmTypePattern(String alarmTypePattern) {
            this.alarmTypePattern = alarmTypePattern;
            return this;
        }

        public Builder minSeverity(AlarmEvent.Severity minSeverity) {
            this.minSeverity = minSeverity;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public MaskingRule build() {
            if (id == null || name == null) {
                throw new IllegalArgumentException("Missing required fields: id, name");
            }
            return new MaskingRule(id, name, targetType, targetId, startTime, endTime,
                    alarmTypePattern, minSeverity, description);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MaskingRule that = (MaskingRule) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
