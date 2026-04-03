package com.aypak.alarmengine.nodes;

import com.aypak.alarmengine.core.AlarmEvent;
import com.aypak.flowengine.core.FlowContext;
import com.aypak.flowengine.core.FlowEvent;
import com.aypak.flowengine.core.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 接收节点 - 流水线第一个节点。
 * 负责告警格式校验和必填字段验证。
 * Receive node - first node in the pipeline.
 * Responsible for alarm format validation and required field verification.
 */
public class ReceiveNode implements FlowNode<String, AlarmEvent> {

    private static final Logger log = LoggerFactory.getLogger(ReceiveNode.class);

    @Override
    public String getNodeName() {
        return "Receive";
    }

    @Override
    public boolean process(FlowEvent<String, AlarmEvent> event, FlowContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        AlarmEvent alarmEvent = event.getPayload();

        // 验证必填字段 / Validate required fields
        if (alarmEvent.getId() == null || alarmEvent.getId().isEmpty()) {
            log.warn("Alarm missing required field: id");
            alarmEvent.setStatus(AlarmEvent.ProcessingStatus.FAILED);
            alarmEvent.setErrorMessage("Missing required field: id");
            return false;
        }

        if (alarmEvent.getDeviceId() == null || alarmEvent.getDeviceId().isEmpty()) {
            log.warn("Alarm {} missing required field: deviceId", alarmEvent.getId());
            alarmEvent.setStatus(AlarmEvent.ProcessingStatus.FAILED);
            alarmEvent.setErrorMessage("Missing required field: deviceId");
            return false;
        }

        if (alarmEvent.getAlarmType() == null || alarmEvent.getAlarmType().isEmpty()) {
            log.warn("Alarm {} missing required field: alarmType", alarmEvent.getId());
            alarmEvent.setStatus(AlarmEvent.ProcessingStatus.FAILED);
            alarmEvent.setErrorMessage("Missing required field: alarmType");
            return false;
        }

        if (alarmEvent.getOccurTime() == null) {
            log.warn("Alarm {} missing required field: occurTime", alarmEvent.getId());
            alarmEvent.setStatus(AlarmEvent.ProcessingStatus.FAILED);
            alarmEvent.setErrorMessage("Missing required field: occurTime");
            return false;
        }

        // 记录接收时间 / Record receive time
        alarmEvent.setReceiveTime(System.currentTimeMillis());

        log.debug("Receive node processed alarm {} in {}ms", alarmEvent.getId(), System.currentTimeMillis() - startTime);
        return true;
    }

    @Override
    public void onFailure(FlowEvent<String, AlarmEvent> event, Throwable error) {
        log.error("ReceiveNode failed for alarm {}: {}", event.getPayload().getId(), error.getMessage());
    }

    /**
     * 接收节点是关键节点。
     * Receive node is critical.
     */
    @Override
    public boolean isCritical() {
        return true;
    }
}
