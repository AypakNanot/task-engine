package com.aypak.engine.alarm.nodes;

import com.aypak.engine.alarm.core.AlarmEvent;
import com.aypak.engine.alarm.core.MaskingRule;
import com.aypak.engine.alarm.core.PipelineContext;
import com.aypak.engine.alarm.core.PipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 北向屏蔽节点。
 * 根据客户屏蔽规则过滤告警。
 * Northbound masking node.
 * Filters alarms based on customer masking rules.
 */
public class NbMaskingNode implements PipelineNode {

    private static final Logger log = LoggerFactory.getLogger(NbMaskingNode.class);

    /** 客户级屏蔽规则：customerId -> rules / Customer-level masking rules: customerId -> rules */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<MaskingRule>> customerRules =
            new ConcurrentHashMap<>();

    /** 是否启用屏蔽 / Whether masking is enabled */
    private volatile boolean enabled = true;

    @Override
    public String getNodeName() {
        return "NB-Masking";
    }

    @Override
    public boolean process(AlarmEvent event, PipelineContext context) {
        if (!enabled) {
            return true;
        }

        long startTime = System.currentTimeMillis();

        // 获取客户 ID
        String customerId = event.getPayload("customerId");
        if (customerId == null) {
            return true;
        }

        // 检查客户屏蔽规则
        CopyOnWriteArrayList<MaskingRule> rules = customerRules.get(customerId);
        if (rules != null) {
            for (MaskingRule rule : rules) {
                if (rule.matches(event)) {
                    log.debug("Alarm {} masked by customer rule {} for customer {}",
                            event.getId(), rule.getId(), customerId);
                    context.setDropReason("Masked by customer rule: " + rule.getId());
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 添加客户屏蔽规则
     */
    public void addRule(String customerId, MaskingRule rule) {
        customerRules
            .computeIfAbsent(customerId, k -> new CopyOnWriteArrayList<>())
            .add(rule);
        log.info("Added customer masking rule: {} for customer {}", rule.getId(), customerId);
    }

    /**
     * 移除客户屏蔽规则
     */
    public void removeRule(String customerId, String ruleId) {
        CopyOnWriteArrayList<MaskingRule> rules = customerRules.get(customerId);
        if (rules != null) {
            rules.removeIf(rule -> rule.getId().equals(ruleId));
            log.info("Removed customer masking rule: {} for customer {}", ruleId, customerId);
        }
    }

    /**
     * 设置是否启用屏蔽
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
