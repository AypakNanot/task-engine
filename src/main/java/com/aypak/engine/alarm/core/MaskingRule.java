package com.aypak.engine.alarm.core;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 告警屏蔽规则。
 * 用于过滤不需要处理或推送的告警。
 * Alarm masking rule.
 * Used to filter alarms that do not need processing or pushing.
 */
public class MaskingRule {

    /** 规则唯一 ID / Unique rule ID */
    private final String id;

    /** 规则名称 / Rule name */
    private final String name;

    /** 目标类型：DEVICE, ALARM_TYPE, GLOBAL / Target type: DEVICE, ALARM_TYPE, GLOBAL */
    private final TargetType targetType;

    /** 目标 ID（设备 ID 或告警类型）/ Target ID (device ID or alarm type) */
    private final String targetId;

    /** 是否启用 / Whether enabled */
    private boolean enabled;

    /** 生效开始时间 / Effective start time */
    private final LocalDateTime startTime;

    /** 生效结束时间 / Effective end time */
    private final LocalDateTime endTime;

    /** 告警类型匹配表达式（支持通配符）/ Alarm type matching expression (supports wildcards) */
    private final String alarmTypePattern;

    /** 严重度匹配（null 表示匹配所有）/ Severity match (null means match all) */
    private final AlarmEvent.Severity minSeverity;

    /** 规则描述 / Rule description */
    private final String description;

    /** 创建时间 / Create time */
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
     * 创建屏蔽规则的 Builder。
     * Builder for creating MaskingRule.
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    /**
     * 获取规则 ID。
     * Get rule ID.
     */
    public String getId() { return id; }

    /**
     * 获取规则名称。
     * Get rule name.
     */
    public String getName() { return name; }

    /**
     * 获取目标类型。
     * Get target type.
     */
    public TargetType getTargetType() { return targetType; }

    /**
     * 获取目标 ID。
     * Get target ID.
     */
    public String getTargetId() { return targetId; }

    /**
     * 获取是否启用。
     * Get whether enabled.
     */
    public boolean isEnabled() { return enabled; }

    /**
     * 获取开始时间。
     * Get start time.
     */
    public LocalDateTime getStartTime() { return startTime; }

    /**
     * 获取结束时间。
     * Get end time.
     */
    public LocalDateTime getEndTime() { return endTime; }

    /**
     * 获取告警类型模式。
     * Get alarm type pattern.
     */
    public String getAlarmTypePattern() { return alarmTypePattern; }

    /**
     * 获取最小严重度。
     * Get minimum severity.
     */
    public AlarmEvent.Severity getMinSeverity() { return minSeverity; }

    /**
     * 获取描述。
     * Get description.
     */
    public String getDescription() { return description; }

    /**
     * 获取创建时间。
     * Get create time.
     */
    public LocalDateTime getCreateTime() { return createTime; }

    /**
     * 设置规则是否启用。
     * Set whether rule is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 检查规则是否匹配给定告警。
     * Check if rule matches given alarm.
     * @param event 告警事件 / alarm event
     * @return true 表示匹配，告警应被屏蔽 / true if matches, alarm should be masked
     */
    public boolean matches(AlarmEvent event) {
        if (!enabled) {
            return false;
        }

        // 检查时间范围 / Check time range
        LocalDateTime now = LocalDateTime.now();
        if (startTime != null && now.isBefore(startTime)) {
            return false;
        }
        if (endTime != null && now.isAfter(endTime)) {
            return false;
        }

        // 检查严重度 / Check severity
        if (minSeverity != null && event.getSeverity().getLevel() < minSeverity.getLevel()) {
            return false;
        }

        // 检查告警类型匹配 / Check alarm type match
        if (alarmTypePattern != null && !matchesAlarmType(event.getAlarmType())) {
            return false;
        }

        return true;
    }

    /**
     * 检查告警类型是否匹配模式。
     * 支持通配符 * 和 ?
     * Check if alarm type matches pattern.
     * Supports wildcards * and ?.
     */
    private boolean matchesAlarmType(String alarmType) {
        if (alarmTypePattern == null) {
            return true;
        }
        if (alarmType == null) {
            return false;
        }

        // 简单通配符匹配 / Simple wildcard match
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
     * 目标类型枚举。
     * Target type enumeration.
     */
    public enum TargetType {
        /** 全局规则，应用于所有告警 / Global rule, applied to all alarms */
        GLOBAL,
        /** 设备级规则，应用于特定设备 / Device-level rule, applied to specific device */
        DEVICE,
        /** 告警类型级规则，应用于特定告警类型 / Alarm type-level rule, applied to specific alarm type */
        ALARM_TYPE
    }

    /**
     * Builder pattern for MaskingRule.
     * 屏蔽规则的构建器模式。
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

        /**
         * 设置 ID。
         * Set ID.
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * 设置名称。
         * Set name.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 设置目标类型。
         * Set target type.
         */
        public Builder targetType(TargetType targetType) {
            this.targetType = targetType;
            return this;
        }

        /**
         * 设置目标 ID。
         * Set target ID.
         */
        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        /**
         * 设置开始时间。
         * Set start time.
         */
        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        /**
         * 设置结束时间。
         * Set end time.
         */
        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        /**
         * 设置告警类型模式。
         * Set alarm type pattern.
         */
        public Builder alarmTypePattern(String alarmTypePattern) {
            this.alarmTypePattern = alarmTypePattern;
            return this;
        }

        /**
         * 设置最小严重度。
         * Set minimum severity.
         */
        public Builder minSeverity(AlarmEvent.Severity minSeverity) {
            this.minSeverity = minSeverity;
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
         * 构建屏蔽规则。
         * Build the masking rule.
         */
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
