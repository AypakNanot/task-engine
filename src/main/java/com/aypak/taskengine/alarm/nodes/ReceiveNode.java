package com.aypak.taskengine.alarm.nodes;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.PipelineContext;
import com.aypak.taskengine.alarm.core.PipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 接收节点 - 流水线第一个节点。
 * 负责告警格式校验和必填字段验证。
 * Receive node - first node in the pipeline.
 * Responsible for alarm format validation and required field verification.
 */
public class ReceiveNode implements PipelineNode {

    private static final Logger log = LoggerFactory.getLogger(ReceiveNode.class);

    @Override
    public String getNodeName() {
        return "Receive";
    }

    @Override
    public boolean process(AlarmEvent event, PipelineContext context) throws Exception {
        long startTime = System.currentTimeMillis();

        // 验证必填字段 / Validate required fields
        if (event.getId() == null || event.getId().isEmpty()) {
            log.warn("Alarm missing required field: id");
            event.setStatus(AlarmEvent.ProcessingStatus.FAILED);
            event.setErrorMessage("Missing required field: id");
            return false;
        }

        if (event.getDeviceId() == null || event.getDeviceId().isEmpty()) {
            log.warn("Alarm {} missing required field: deviceId", event.getId());
            event.setStatus(AlarmEvent.ProcessingStatus.FAILED);
            event.setErrorMessage("Missing required field: deviceId");
            return false;
        }

        if (event.getAlarmType() == null || event.getAlarmType().isEmpty()) {
            log.warn("Alarm {} missing required field: alarmType", event.getId());
            event.setStatus(AlarmEvent.ProcessingStatus.FAILED);
            event.setErrorMessage("Missing required field: alarmType");
            return false;
        }

        if (event.getOccurTime() == null) {
            log.warn("Alarm {} missing required field: occurTime", event.getId());
            event.setStatus(AlarmEvent.ProcessingStatus.FAILED);
            event.setErrorMessage("Missing required field: occurTime");
            return false;
        }

        // 记录接收时间 / Record receive time
        event.setReceiveTime(System.currentTimeMillis());

        long latency = System.currentTimeMillis() - startTime;
        context.recordNodeLatency(getNodeName(), latency);

        log.debug("Receive node processed alarm {} in {}ms", event.getId(), latency);
        return true;
    }

    @Override
    public void onFailure(AlarmEvent event, Throwable error) {
        log.error("ReceiveNode failed for alarm {}: {}", event.getId(), error.getMessage());
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
