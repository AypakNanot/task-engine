package com.aypak.engine.alarm.engine;

import com.aypak.engine.alarm.adapter.AlarmFlowAdapter;
import com.aypak.engine.alarm.batch.BatchDBExecutor;
import com.aypak.engine.alarm.core.AlarmEvent;
import com.aypak.engine.alarm.core.PipelineNode;
import com.aypak.engine.alarm.core.RejectPolicy;
import com.aypak.engine.alarm.monitor.AlarmMetrics;
import com.aypak.engine.alarm.nodes.*;
import com.aypak.engine.flow.ShardedFlowEngine;
import com.aypak.engine.flow.ShardedFlowEngineBuilder;
import com.aypak.engine.flow.core.FlowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 告警引擎实现 - 基于 ShardedFlowEngine
 * Alarm engine implementation based on ShardedFlowEngine.
 * Integrates all components and provides unified alarm processing interface.
 */
public class AlarmEngineImpl implements AlarmEngine {

    private static final Logger log = LoggerFactory.getLogger(AlarmEngineImpl.class);

    /** 默认 Worker 数量 / Default Worker count */
    private static final int DEFAULT_WORKER_COUNT = 16;

    /** 默认 Worker 队列容量 / Default Worker queue capacity */
    private static final int DEFAULT_WORKER_QUEUE_CAPACITY = 5000;

    /** 分片流引擎 / Sharded flow engine */
    private final ShardedFlowEngine<String, AlarmEvent> flowEngine;

    /** 批量数据库执行器 / Batch database executor */
    private final BatchDBExecutor dbExecutor;

    /** 告警指标 / Alarm metrics */
    private final AlarmMetrics metrics;

    /** 运行标志 / Running flag */
    private volatile boolean running = false;

    /**
     * 创建告警引擎
     * Create alarm engine.
     * @param dataSource 数据源 / data source
     * @param insertSql 插入 SQL 语句 / insert SQL statement
     */
    public AlarmEngineImpl(DataSource dataSource, String insertSql) {
        this(dataSource, insertSql, DEFAULT_WORKER_COUNT, DEFAULT_WORKER_QUEUE_CAPACITY,
                RejectPolicy.DROP);
    }

    /**
     * 创建告警引擎
     * Create alarm engine.
     * @param dataSource 数据源 / data source
     * @param insertSql 插入 SQL 语句 / insert SQL statement
     * @param workerCount Worker 数量 / Worker count
     * @param workerQueueCapacity Worker 队列容量 / Worker queue capacity
     * @param rejectPolicy 拒绝策略 / rejection policy
     */
    public AlarmEngineImpl(DataSource dataSource, String insertSql,
                          int workerCount, int workerQueueCapacity,
                          RejectPolicy rejectPolicy) {
        // 创建批量数据库执行器（必须在 createPipelineNodes 之前创建）
        // Create batch DB executor (must be created before createPipelineNodes)
        this.dbExecutor = new BatchDBExecutor(dataSource, insertSql);

        // 创建告警指标
        // Create alarm metrics
        this.metrics = new AlarmMetrics();

        // 创建流水线节点列表
        // Create pipeline nodes list
        List<PipelineNode> nodes = createPipelineNodes();

        // 映射拒绝策略
        // Map rejection policy
        com.aypak.engine.flow.core.RejectPolicy flowRejectPolicy = mapRejectPolicy(rejectPolicy);

        // 创建分片流引擎构建器
        // Create sharded flow engine builder
        ShardedFlowEngineBuilder<String, AlarmEvent> builder = ShardedFlowEngine.<String, AlarmEvent>builder()
                .name("AlarmEngine")
                .shardCount(workerCount)
                .queueCapacity(workerQueueCapacity)
                .rejectPolicy(flowRejectPolicy)
                .metricsEnabled(true);

        // 将所有 PipelineNode 适配为 FlowNode 并添加到构建器
        // Adapt all PipelineNode to FlowNode and add to builder
        for (PipelineNode node : nodes) {
            AlarmFlowAdapter adapter = new AlarmFlowAdapter(node);
            builder.addNode(adapter);
        }

        // 构建引擎（不启动）
        // Build engine (without starting)
        this.flowEngine = builder.buildWithoutStart();

        log.info("AlarmEngineImpl created with {} workers, workerQueueCapacity={}, policy={}",
                workerCount, workerQueueCapacity, rejectPolicy);
    }

    /**
     * 创建流水线节点列表
     * Create pipeline nodes list.
     */
    private List<PipelineNode> createPipelineNodes() {
        List<PipelineNode> nodes = new ArrayList<>(9);

        // 1. ReceiveNode - 接收节点 / Receive node
        nodes.add(new ReceiveNode());

        // 2. FilterNode - 本地过滤节点 / Local filter node
        nodes.add(new FilterNode());

        // 3. MaskingNode - 本地屏蔽节点 / Local masking node
        nodes.add(new MaskingNode());

        // 4. AnalysisNode - 业务分析节点 / Business analysis node
        nodes.add(new AnalysisNode());

        // 5. PersistenceNode - 持久化节点 / Persistence node
        nodes.add(new PersistenceNode(dbExecutor));

        // 6. NB-NotifyNode - 北向通知准备节点 / Northbound notify preparation node
        nodes.add(new NbNotifyNode());

        // 7. NB-FilterNode - 北向过滤节点 / Northbound filter node
        nodes.add(new NbFilterNode());

        // 8. NB-MaskingNode - 北向屏蔽节点 / Northbound masking node
        nodes.add(new NbMaskingNode());

        // 9. NB-PushNode - 北向推送节点 / Northbound push node
        nodes.add(new NbPushNode());

        log.info("Pipeline nodes created: 9 nodes from Receive to NB-Push");

        return nodes;
    }

    /**
     * 映射拒绝策略
     * Map rejection policy.
     */
    private com.aypak.engine.flow.core.RejectPolicy mapRejectPolicy(RejectPolicy policy) {
        switch (policy) {
            case DROP:
                return com.aypak.engine.flow.core.RejectPolicy.DROP;
            case BLOCK:
                return com.aypak.engine.flow.core.RejectPolicy.BLOCK;
            case CALLER_RUNS:
                return com.aypak.engine.flow.core.RejectPolicy.CALLER_RUNS;
            case DROP_OLDEST:
                return com.aypak.engine.flow.core.RejectPolicy.DROP_OLDEST;
            default:
                return com.aypak.engine.flow.core.RejectPolicy.DROP;
        }
    }

    /**
     * 启动引擎
     * Start engine.
     */
    public void start() {
        if (running) {
            log.warn("AlarmEngine is already running");
            return;
        }

        log.info("===========================================");
        log.info("AlarmEngine starting...");
        log.info("===========================================");

        flowEngine.start();

        running = true;

        log.info("===========================================");
        log.info("AlarmEngine started successfully");
        log.info("===========================================");
    }

    @Override
    public boolean submit(AlarmEvent event) {
        if (!running) {
            log.warn("AlarmEngine is not running, rejecting event: {}", event.getId());
            return false;
        }

        // 记录进入指标 / Record incoming metric
        metrics.recordIncoming();

        // 提交到流引擎，使用 deviceId 作为分片键
        // Submit to flow engine with deviceId as shard key
        FlowEvent<String, AlarmEvent> flowEvent = new FlowEvent<>(event.getDeviceId(), event);
        return flowEngine.submit(flowEvent);
    }

    @Override
    public AlarmMetrics getMetrics() {
        // 同步流引擎指标到告警指标（仅一次快照）
        // Sync flow engine metrics to alarm metrics (one-time snapshot)
        var flowMetrics = flowEngine.getMetrics();
        if (flowMetrics != null) {
            metrics.getSuccessCounter().reset();
            metrics.getFailureCounter().reset();
            metrics.getDroppedCounter().reset();
            metrics.getSuccessCounter().add(flowMetrics.getSuccessCount().sum());
            metrics.getFailureCounter().add(flowMetrics.getFailureCount().sum());
            metrics.getDroppedCounter().add(flowMetrics.getDroppedCount().sum());
        }
        return metrics;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void shutdown() {
        if (!running) {
            return;
        }

        log.info("Shutting down AlarmEngine...");
        flowEngine.shutdown(30, java.util.concurrent.TimeUnit.SECONDS);
        dbExecutor.shutdown();
        running = false;
        log.info("AlarmEngine shut down completed");
    }

    /**
     * 获取批量数据库执行器
     * Get batch database executor.
     */
    public BatchDBExecutor getDbExecutor() {
        return dbExecutor;
    }
}
