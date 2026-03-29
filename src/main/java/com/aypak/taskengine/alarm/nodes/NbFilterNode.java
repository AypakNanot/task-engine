package com.aypak.taskengine.alarm.nodes;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.PipelineContext;
import com.aypak.taskengine.alarm.core.PipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 北向过滤节点
 * 根据客户订阅规则过滤告警
 */
public class NbFilterNode implements PipelineNode {

    private static final Logger log = LoggerFactory.getLogger(NbFilterNode.class);

    /** 订阅的告警类型集合：customerId -> alarmTypes */
    private final ConcurrentHashMap<String, Set<String>> customerSubscriptions = new ConcurrentHashMap<>();

    /** 是否启用过滤 */
    private volatile boolean enabled = true;

    @Override
    public String getNodeName() {
        return "NB-Filter";
    }

    @Override
    public boolean process(AlarmEvent event, PipelineContext context) {
        if (!enabled) {
            return true;
        }

        long startTime = System.currentTimeMillis();

        // 获取客户 ID（从 payload 或 context）
        String customerId = event.getPayload("customerId");
        if (customerId == null) {
            // 没有指定客户，默认通过
            return true;
        }

        // 检查客户订阅
        Set<String> subscriptions = customerSubscriptions.get(customerId);
        if (subscriptions == null || subscriptions.isEmpty()) {
            // 客户没有订阅任何告警类型
            log.debug("Alarm {} filtered: customer {} has no subscriptions",
                    event.getId(), customerId);
            context.setDropReason("No subscription for customer " + customerId);
            return false;
        }

        // 检查告警类型是否在订阅中
        if (!subscriptions.contains(event.getAlarmType()) &&
            !subscriptions.contains("*")) {
            log.debug("Alarm {} filtered: customer {} not subscribed to type {}",
                    event.getId(), customerId, event.getAlarmType());
            context.setDropReason("Not subscribed: " + event.getAlarmType());
            return false;
        }

        long latency = System.currentTimeMillis() - startTime;
        context.recordNodeLatency(getNodeName(), latency);

        return true;
    }

    /**
     * 添加客户订阅
     */
    public void addSubscription(String customerId, String alarmType) {
        customerSubscriptions
            .computeIfAbsent(customerId, k -> ConcurrentHashMap.newKeySet())
            .add(alarmType);
        log.info("Added subscription: customer {} -> {}", customerId, alarmType);
    }

    /**
     * 移除客户订阅
     */
    public void removeSubscription(String customerId, String alarmType) {
        Set<String> subscriptions = customerSubscriptions.get(customerId);
        if (subscriptions != null) {
            subscriptions.remove(alarmType);
            log.info("Removed subscription: customer {} -> {}", customerId, alarmType);
        }
    }

    /**
     * 设置是否启用过滤
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
