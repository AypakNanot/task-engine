package com.aypak.engine.alarm.adapter;

import com.aypak.engine.alarm.core.AlarmEvent;
import com.aypak.engine.alarm.core.PipelineContext;
import com.aypak.engine.alarm.core.PipelineNode;
import com.aypak.engine.flow.core.FlowContext;
import com.aypak.engine.flow.core.FlowEvent;
import com.aypak.engine.flow.core.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 告警流适配器 - 将 PipelineNode 适配为 FlowNode。
 * Alarm flow adapter - adapts PipelineNode to FlowNode.
 *
 * <p>使得现有的告警处理节点可以在 ShardedFlowEngine 中运行。</p>
 * <p>Enables existing alarm processing nodes to run in ShardedFlowEngine.</p>
 */
public class AlarmFlowAdapter implements FlowNode<String, AlarmEvent> {

    private static final Logger log = LoggerFactory.getLogger(AlarmFlowAdapter.class);

    private final PipelineNode pipelineNode;

    public AlarmFlowAdapter(PipelineNode pipelineNode) {
        this.pipelineNode = pipelineNode;
    }

    @Override
    public String getNodeName() {
        return pipelineNode.getNodeName();
    }

    @Override
    public boolean process(FlowEvent<String, AlarmEvent> flowEvent, FlowContext flowContext) throws Exception {
        AlarmEvent alarmEvent = flowEvent.getPayload();

        // 创建流水线上下文
        // Create pipeline context
        PipelineContext pipelineContext = createPipelineContext(flowContext);

        try {
            // 记录节点开始时间
            // Record node start time
            long startTime = System.currentTimeMillis();

            // 调用流水线节点处理
            // Call pipeline node processing
            boolean result = pipelineNode.process(alarmEvent, pipelineContext);

            // 记录节点时延
            // Record node latency
            long latency = System.currentTimeMillis() - startTime;
            pipelineContext.recordNodeLatency(getNodeName(), latency);

            // 同步上下文状态到 flowContext
            // Sync context state to flowContext
            syncContext(flowContext, pipelineContext);

            if (!result) {
                log.debug("Node {} stopped processing for alarm {}", getNodeName(), alarmEvent.getId());
            }

            return result;

        } catch (Exception e) {
            // 同步上下文
            // Sync context
            syncContext(flowContext, pipelineContext);

            // 调用节点的 onFailure 回调
            // Call node's onFailure callback
            pipelineNode.onFailure(alarmEvent, e);

            // 如果是关键节点，抛出异常
            // If critical node, rethrow exception
            if (pipelineNode.isCritical()) {
                alarmEvent.setStatus(AlarmEvent.ProcessingStatus.FAILED);
                alarmEvent.setErrorMessage(e.getMessage());
                throw e;
            }

            // 非关键节点，记录错误并继续
            // Non-critical node, log error and continue
            log.warn("Non-critical node {} failed for alarm {}: {}",
                    getNodeName(), alarmEvent.getId(), e.getMessage());
            return true;
        }
    }

    @Override
    public void onFailure(FlowEvent<String, AlarmEvent> event, Throwable error) {
        pipelineNode.onFailure(event.getPayload(), error);
    }

    @Override
    public boolean isCritical() {
        return pipelineNode.isCritical();
    }

    @Override
    public void initialize() {
        pipelineNode.initialize();
        log.info("AlarmFlowAdapter initialized for node: {}", getNodeName());
    }

    @Override
    public void destroy() {
        pipelineNode.destroy();
        log.info("AlarmFlowAdapter destroyed for node: {}", getNodeName());
    }

    /**
     * 从 flowContext 创建 pipelineContext。
     * Create pipelineContext from flowContext.
     */
    private PipelineContext createPipelineContext(FlowContext flowContext) {
        PipelineContext pipelineContext = new PipelineContext();

        // 如果 flowContext 中已有 pipelineContext，直接使用
        // If pipelineContext already exists in flowContext, use it directly
        if (flowContext.containsKey("pipelineContext")) {
            return (PipelineContext) flowContext.get("pipelineContext");
        }

        return pipelineContext;
    }

    /**
     * 同步 pipelineContext 状态到 flowContext。
     * Sync pipelineContext state to flowContext.
     */
    private void syncContext(FlowContext flowContext, PipelineContext pipelineContext) {
        // 将 pipelineContext 存入 flowContext 以便在节点间传递
        // Store pipelineContext in flowContext for passing between nodes
        flowContext.set("pipelineContext", pipelineContext);

        // 同步状态标记
        // Sync status flags
        if (pipelineContext.isPersisted()) {
            flowContext.markPersisted();
        }

        if (pipelineContext.isNotified()) {
            flowContext.markNotified();
        }

        if (pipelineContext.isDropped()) {
            flowContext.set("dropReason", pipelineContext.getDropReason());
        }

        // 如果停止处理，同步到 flowContext
        // If stop processing, sync to flowContext
        if (!pipelineContext.shouldContinue()) {
            flowContext.stop();
        }
    }
}
