package com.aypak.taskengine.alarm.nodes;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.PipelineContext;
import com.aypak.taskengine.alarm.core.PipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 北向通知准备节点
 * 负责将告警转换为北向接口格式
 */
public class NbNotifyNode implements PipelineNode {

    private static final Logger log = LoggerFactory.getLogger(NbNotifyNode.class);

    @Override
    public String getNodeName() {
        return "NB-Notify";
    }

    @Override
    public boolean process(AlarmEvent event, PipelineContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // 转换为北向通知格式
            // 这里可以将转换后的数据存入 context 或 event payload
            // 实际实现可能包括：
            // - 格式转换（XML/JSON）
            // - 字段映射
            // - 编码转换

            context.set("nbNotificationReady", true);

            long latency = System.currentTimeMillis() - startTime;
            context.recordNodeLatency(getNodeName(), latency);

            log.debug("NB-Notify node processed alarm {} in {}ms", event.getId(), latency);

            return true;

        } catch (Exception e) {
            log.error("NB-Notify failed for alarm {}", event.getId(), e);
            throw e;
        }
    }

    @Override
    public void onFailure(AlarmEvent event, Throwable error) {
        log.error("NbNotifyNode failed for alarm {}: {}", event.getId(), error.getMessage());
    }
}
