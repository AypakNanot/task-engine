package com.aypak.taskengine.alarm.nodes;

import com.aypak.taskengine.alarm.batch.BatchDBExecutor;
import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.PipelineContext;
import com.aypak.taskengine.alarm.core.PipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 持久化节点 - 批量入库。
 * 将告警提交到批量执行器进行数据库写入。
 * Persistence node - batch persistence.
 * Submits alarms to batch executor for database writing.
 */
public class PersistenceNode implements PipelineNode {

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
    public boolean process(AlarmEvent event, PipelineContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // 提交到批量执行器 / Submit to batch executor
            batchExecutor.submit(event);

            // 标记为已提交持久化 / Mark as submitted for persistence
            context.markPersisted();
            event.setStatus(AlarmEvent.ProcessingStatus.PERSISTED);

            long latency = System.currentTimeMillis() - startTime;
            context.recordNodeLatency(getNodeName(), latency);

            log.debug("Persistence node submitted alarm {} to batch executor in {}ms",
                    event.getId(), latency);

            return true;

        } catch (Exception e) {
            log.error("Persistence failed for alarm {}", event.getId(), e);
            throw e;
        }
    }

    @Override
    public void onFailure(AlarmEvent event, Throwable error) {
        log.error("PersistenceNode failed for alarm {}: {}", event.getId(), error.getMessage());
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
