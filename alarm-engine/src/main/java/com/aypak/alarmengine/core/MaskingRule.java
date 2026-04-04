package com.aypak.alarmengine.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警屏蔽规则。
 * 用于过滤不需要处理或推送的告警。
 * Alarm masking rule.
 * Used to filter alarms that do not need processing or pushing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class MaskingRule {

    /** 规则唯一 ID / Unique rule ID */
    private String id;

    /** 规则名称 / Rule name */
    private String name;

    /** 目标类型：DEVICE, ALARM_TYPE, GLOBAL / Target type: DEVICE, ALARM_TYPE, GLOBAL */
    @Builder.Default
    private TargetType targetType = TargetType.GLOBAL;

    /** 目标 ID（设备 ID 或告警类型）/ Target ID (device ID or alarm type) */
    private String targetId;

    /** 是否启用 / Whether enabled */
    @Builder.Default
    private boolean enabled = true;

    /** 生效开始时间 / Effective start time */
    private LocalDateTime startTime;

    /** 生效结束时间 / Effective end time */
    private LocalDateTime endTime;

    /** 告警类型匹配表达式（支持通配符）/ Alarm type matching expression (supports wildcards) */
    private String alarmTypePattern;

    /** 严重度匹配（null 表示匹配所有）/ Severity match (null means match all) */
    private AlarmEvent.Severity minSeverity;

    /** 规则描述 / Rule description */
    private String description;

    /** 创建时间 / Create time */
    @Builder.Default
    private LocalDateTime createTime = LocalDateTime.now();

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
}
