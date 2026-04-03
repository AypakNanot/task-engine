package com.aypak.alarmengine.nodes;

import com.aypak.alarmengine.core.AlarmEvent;
import com.aypak.flowengine.core.FlowContext;
import com.aypak.flowengine.core.FlowEvent;
import com.aypak.flowengine.core.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 过滤节点 - 本地过滤。
 * 根据配置的过滤规则过滤无效告警。
 * Filter node - local filtering.
 * Filters invalid alarms based on configured filter rules.
 */
public class FilterNode implements FlowNode<String, AlarmEvent> {

    private static final Logger log = LoggerFactory.getLogger(FilterNode.class);

    /** 过滤的告警类型集合 / Set of filtered alarm types */
    private final Set<String> filteredAlarmTypes = ConcurrentHashMap.newKeySet();

    /** 过滤的设备 ID 集合 / Set of filtered device IDs */
    private final Set<String> filteredDeviceIds = ConcurrentHashMap.newKeySet();

    /** 是否启用过滤 / Whether filtering is enabled */
    private volatile boolean enabled = true;

    @Override
    public String getNodeName() {
        return "Filter";
    }

    @Override
    public boolean process(FlowEvent<String, AlarmEvent> event, FlowContext context) {
        if (!enabled) {
            return true;
        }

        AlarmEvent alarmEvent = event.getPayload();

        // 检查告警类型是否被过滤 / Check if alarm type is filtered
        if (filteredAlarmTypes.contains(alarmEvent.getAlarmType())) {
            log.debug("Alarm {} filtered by alarmType: {}", alarmEvent.getId(), alarmEvent.getAlarmType());
            context.set("dropReason", "Filtered by alarmType: " + alarmEvent.getAlarmType());
            alarmEvent.setStatus(AlarmEvent.ProcessingStatus.DROPPED);
            context.stop();
            return false;
        }

        // 检查设备 ID 是否被过滤 / Check if device ID is filtered
        if (filteredDeviceIds.contains(alarmEvent.getDeviceId())) {
            log.debug("Alarm {} filtered by deviceId: {}", alarmEvent.getId(), alarmEvent.getDeviceId());
            context.set("dropReason", "Filtered by deviceId: " + alarmEvent.getDeviceId());
            alarmEvent.setStatus(AlarmEvent.ProcessingStatus.DROPPED);
            context.stop();
            return false;
        }

        return true;
    }

    /**
     * 添加过滤的告警类型。
     * Add filtered alarm type.
     */
    public void addFilteredAlarmType(String alarmType) {
        filteredAlarmTypes.add(alarmType);
        log.info("Added filtered alarmType: {}", alarmType);
    }

    /**
     * 移除过滤的告警类型。
     * Remove filtered alarm type.
     */
    public void removeFilteredAlarmType(String alarmType) {
        filteredAlarmTypes.remove(alarmType);
        log.info("Removed filtered alarmType: {}", alarmType);
    }

    /**
     * 添加过滤的设备 ID。
     * Add filtered device ID.
     */
    public void addFilteredDeviceId(String deviceId) {
        filteredDeviceIds.add(deviceId);
        log.info("Added filtered deviceId: {}", deviceId);
    }

    /**
     * 移除过滤的设备 ID。
     * Remove filtered device ID.
     */
    public void removeFilteredDeviceId(String deviceId) {
        filteredDeviceIds.remove(deviceId);
        log.info("Removed filtered deviceId: {}", deviceId);
    }

    /**
     * 设置是否启用过滤。
     * Set whether filtering is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 清空所有过滤规则。
     * Clear all filter rules.
     */
    public void clearFilters() {
        filteredAlarmTypes.clear();
        filteredDeviceIds.clear();
        log.info("Cleared all filters");
    }
}
