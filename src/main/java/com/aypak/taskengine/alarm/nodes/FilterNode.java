package com.aypak.taskengine.alarm.nodes;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.PipelineContext;
import com.aypak.taskengine.alarm.core.PipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 过滤节点 - 本地过滤
 * 根据配置的过滤规则过滤无效告警
 */
public class FilterNode implements PipelineNode {

    private static final Logger log = LoggerFactory.getLogger(FilterNode.class);

    /** 过滤的告警类型集合 */
    private final Set<String> filteredAlarmTypes = ConcurrentHashMap.newKeySet();

    /** 过滤的设备 ID 集合 */
    private final Set<String> filteredDeviceIds = ConcurrentHashMap.newKeySet();

    /** 是否启用过滤 */
    private volatile boolean enabled = true;

    @Override
    public String getNodeName() {
        return "Filter";
    }

    @Override
    public boolean process(AlarmEvent event, PipelineContext context) {
        if (!enabled) {
            return true;
        }

        long startTime = System.currentTimeMillis();

        // 检查告警类型是否被过滤
        if (filteredAlarmTypes.contains(event.getAlarmType())) {
            log.debug("Alarm {} filtered by alarmType: {}", event.getId(), event.getAlarmType());
            context.setDropReason("Filtered by alarmType: " + event.getAlarmType());
            event.setStatus(AlarmEvent.ProcessingStatus.DROPPED);
            return false;
        }

        // 检查设备 ID 是否被过滤
        if (filteredDeviceIds.contains(event.getDeviceId())) {
            log.debug("Alarm {} filtered by deviceId: {}", event.getId(), event.getDeviceId());
            context.setDropReason("Filtered by deviceId: " + event.getDeviceId());
            event.setStatus(AlarmEvent.ProcessingStatus.DROPPED);
            return false;
        }

        long latency = System.currentTimeMillis() - startTime;
        context.recordNodeLatency(getNodeName(), latency);

        return true;
    }

    /**
     * 添加过滤的告警类型
     */
    public void addFilteredAlarmType(String alarmType) {
        filteredAlarmTypes.add(alarmType);
        log.info("Added filtered alarmType: {}", alarmType);
    }

    /**
     * 移除过滤的告警类型
     */
    public void removeFilteredAlarmType(String alarmType) {
        filteredAlarmTypes.remove(alarmType);
        log.info("Removed filtered alarmType: {}", alarmType);
    }

    /**
     * 添加过滤的设备 ID
     */
    public void addFilteredDeviceId(String deviceId) {
        filteredDeviceIds.add(deviceId);
        log.info("Added filtered deviceId: {}", deviceId);
    }

    /**
     * 移除过滤的设备 ID
     */
    public void removeFilteredDeviceId(String deviceId) {
        filteredDeviceIds.remove(deviceId);
        log.info("Removed filtered deviceId: {}", deviceId);
    }

    /**
     * 设置是否启用过滤
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 清空所有过滤规则
     */
    public void clearFilters() {
        filteredAlarmTypes.clear();
        filteredDeviceIds.clear();
        log.info("Cleared all filters");
    }
}
