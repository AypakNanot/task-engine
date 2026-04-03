package com.aypak.alarmengine.nodes;

import com.aypak.alarmengine.batch.BatchDBExecutor;
import com.aypak.alarmengine.core.AlarmEvent;
import com.aypak.flowengine.core.FlowContext;
import com.aypak.flowengine.core.FlowEvent;
import com.aypak.flowengine.core.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 持久化节点 - 批量入库。
 * 将告警提交到批量执行器进行数据库写入。
 * Persistence node - batch persistence.
 * Submits alarms to batch executor for database writing.
 */
public class PersistenceNode implements FlowNode<String, AlarmEvent> {

    private static final Logger log = LoggerFactory.getLogger(PersistenceNode.class);

    private final BatchDBExecutor batchExecutor;

    public PersistenceNode(BatchDBExecutor batchExecutor) {
        this.batchExecutor = batchExecutor;
    }

    @Override
    public String getNodeName() {
        return "Persistence";
    }

    @Override
    public boolean process(FlowEvent<String, AlarmEvent> event, FlowContext context) {
        long startTime = System.currentTimeMillis();
        AlarmEvent alarmEvent = event.getPayload();

        try {
            // 提交到批量执行器 / Submit to batch executor
            batchExecutor.submit(alarmEvent);

            // 标记为已提交持久化 / Mark as submitted for persistence
            context.markPersisted();
            alarmEvent.setStatus(AlarmEvent.ProcessingStatus.PERSISTED);

            log.debug("Persistence node submitted alarm {} to batch executor in {}ms",
                    alarmEvent.getId(), System.currentTimeMillis() - startTime);

            return true;

        } catch (Exception e) {
            log.error("Persistence failed for alarm {}", alarmEvent.getId(), e);
            throw e;
        }
    }

    @Override
    public void onFailure(FlowEvent<String, AlarmEvent> event, Throwable error) {
        log.error("PersistenceNode failed for alarm {}: {}", event.getPayload().getId(), error.getMessage());
    }

    /**
     * 持久化节点是关键节点。
     * Persistence node is critical.
     */
    @Override
    public boolean isCritical() {
        return true;
    }
}
