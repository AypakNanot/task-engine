package com.aypak.alarmengine.nodes;

import com.aypak.alarmengine.core.AlarmEvent;
import com.aypak.flowengine.core.FlowContext;
import com.aypak.flowengine.core.FlowEvent;
import com.aypak.flowengine.core.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 北向通知准备节点。
 * 负责将告警转换为北向接口格式。
 * Northbound notification preparation node.
 * Responsible for converting alarms to northbound interface format.
 */
public class NbNotifyNode implements FlowNode<String, AlarmEvent> {

    private static final Logger log = LoggerFactory.getLogger(NbNotifyNode.class);

    @Override
    public String getNodeName() {
        return "NB-Notify";
    }

    @Override
    public boolean process(FlowEvent<String, AlarmEvent> event, FlowContext context) {
        long startTime = System.currentTimeMillis();
        AlarmEvent alarmEvent = event.getPayload();

        try {
            // 转换为北向通知格式 / Convert to northbound notification format
            // 这里可以将转换后的数据存入 context 或 event payload
            // Here you can store converted data in context or event payload
            // 实际实现可能包括：/ Actual implementation may include:
            // - 格式转换（XML/JSON）/ Format conversion (XML/JSON)
            // - 字段映射 / Field mapping
            // - 编码转换 / Encoding conversion

            context.set("nbNotificationReady", true);

            log.debug("NB-Notify node processed alarm {} in {}ms", alarmEvent.getId(), System.currentTimeMillis() - startTime);

            return true;

        } catch (Exception e) {
            log.error("NB-Notify failed for alarm {}", alarmEvent.getId(), e);
            throw e;
        }
    }

    @Override
    public void onFailure(FlowEvent<String, AlarmEvent> event, Throwable error) {
        log.error("NbNotifyNode failed for alarm {}: {}", event.getPayload().getId(), error.getMessage());
    }
}
