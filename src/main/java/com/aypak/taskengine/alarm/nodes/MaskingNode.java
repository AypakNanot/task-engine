package com.aypak.taskengine.alarm.nodes;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.MaskingRule;
import com.aypak.taskengine.alarm.core.PipelineContext;
import com.aypak.taskengine.alarm.core.PipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 屏蔽节点 - 本地屏蔽。
 * 根据屏蔽规则过滤告警。
 * Masking node - local masking.
 * Filters alarms based on masking rules.
 */
public class MaskingNode implements PipelineNode {

    private static final Logger log = LoggerFactory.getLogger(MaskingNode.class);

    /** 全局屏蔽规则 / Global masking rules */
    private final CopyOnWriteArrayList<MaskingRule> globalRules = new CopyOnWriteArrayList<>();

    /** 设备级屏蔽规则：deviceId -> rules / Device-level masking rules: deviceId -> rules */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<MaskingRule>> deviceRules =
            new ConcurrentHashMap<>();

    /** 告警类型级屏蔽规则：alarmType -> rules / Alarm type-level masking rules: alarmType -> rules */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<MaskingRule>> typeRules =
            new ConcurrentHashMap<>();

    /** 是否启用屏蔽 / Whether masking is enabled */
    private volatile boolean enabled = true;

    @Override
    public String getNodeName() {
        return "Masking";
    }

    @Override
    public boolean process(AlarmEvent event, PipelineContext context) {
        if (!enabled) {
            return true;
        }

        long startTime = System.currentTimeMillis();

        if (isMasked(event)) {
            log.debug("Alarm {} masked", event.getId());
            context.setDropReason("Masked by rule");
            event.setStatus(AlarmEvent.ProcessingStatus.DROPPED);
            return false;
        }

        long latency = System.currentTimeMillis() - startTime;
        context.recordNodeLatency(getNodeName(), latency);

        return true;
    }

    /**
     * 检查告警是否被屏蔽
     */
    public boolean isMasked(AlarmEvent event) {
        // 1. 检查全局规则
        for (MaskingRule rule : globalRules) {
            if (rule.matches(event)) {
                log.debug("Alarm {} masked by global rule {}", event.getId(), rule.getId());
                return true;
            }
        }

        // 2. 检查设备级规则
        CopyOnWriteArrayList<MaskingRule> rules = deviceRules.get(event.getDeviceId());
        if (rules != null) {
            for (MaskingRule rule : rules) {
                if (rule.matches(event)) {
                    log.debug("Alarm {} masked by device rule {}", event.getId(), rule.getId());
                    return true;
                }
            }
        }

        // 3. 检查告警类型级规则
        rules = typeRules.get(event.getAlarmType());
        if (rules != null) {
            for (MaskingRule rule : rules) {
                if (rule.matches(event)) {
                    log.debug("Alarm {} masked by type rule {}", event.getId(), rule.getId());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 添加屏蔽规则
     */
    public void addRule(MaskingRule rule) {
        switch (rule.getTargetType()) {
            case GLOBAL:
                globalRules.add(rule);
                log.info("Added global masking rule: {}", rule.getId());
                break;
            case DEVICE:
                deviceRules.computeIfAbsent(rule.getTargetId(), k -> new CopyOnWriteArrayList<>()).add(rule);
                log.info("Added device masking rule: {} for {}", rule.getId(), rule.getTargetId());
                break;
            case ALARM_TYPE:
                typeRules.computeIfAbsent(rule.getTargetId(), k -> new CopyOnWriteArrayList<>()).add(rule);
                log.info("Added type masking rule: {} for {}", rule.getId(), rule.getTargetId());
                break;
        }
    }

    /**
     * 移除屏蔽规则
     */
    public void removeRule(String ruleId) {
        // 从全局规则中移除
        globalRules.removeIf(rule -> rule.getId().equals(ruleId));

        // 从设备规则中移除
        deviceRules.values().forEach(rules ->
                rules.removeIf(rule -> rule.getId().equals(ruleId)));

        // 从类型规则中移除
        typeRules.values().forEach(rules ->
                rules.removeIf(rule -> rule.getId().equals(ruleId)));

        log.info("Removed masking rule: {}", ruleId);
    }

    /**
     * 设置是否启用屏蔽
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 清空所有屏蔽规则
     */
    public void clearRules() {
        globalRules.clear();
        deviceRules.clear();
        typeRules.clear();
        log.info("Cleared all masking rules");
    }

    /**
     * 获取规则数量（用于监控）
     */
    public int getRuleCount() {
        int count = globalRules.size();
        count += deviceRules.values().stream().mapToInt(List::size).sum();
        count += typeRules.values().stream().mapToInt(List::size).sum();
        return count;
    }
}
