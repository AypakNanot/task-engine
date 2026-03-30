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
import com.aypak.engine.flow.monitor.FlowMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 ShardedFlowEngine 的告警引擎。
 * Alarm engine based on ShardedFlowEngine.
 *
 * <p>使用通用的 ShardedFlowEngine 替代自定义的 ShardDispatcher 和 Worker。</p>
 * <p>Uses generic ShardedFlowEngine instead of custom ShardDispatcher and Worker.</p>
 */
public class FlowAlarmEngine implements AlarmEngine {

    private static final Logger log = LoggerFactory.getLogger(FlowAlarmEngine.class);

    /** 默认 Worker 数量 / Default Worker count */
    private static final int DEFAULT_WORKER_COUNT = 16;

    /** 默认 Worker 队列容量 / Default Worker queue capacity */
    private static final int DEFAULT_WORKER_QUEUE_CAPACITY = 5000;

    /** 默认 Receiver 队列容量 / Default Receiver queue capacity */
    private static final int DEFAULT_RECEIVER_QUEUE_CAPACITY = 50000;

    /** 分片流引擎 / Sharded flow engine */
    private final ShardedFlowEngine<String, AlarmEvent> flowEngine;

    /** 告警指标 / Alarm metrics */
    private final AlarmMetrics metrics;

    /** 批量数据库执行器 / Batch database executor */
    private final BatchDBExecutor dbExecutor;

    /** 运行标志 / Running flag */
    private volatile boolean running = false;

    /**
     * 创建基于 Flow 的告警引擎。
     * Create flow-based alarm engine.
     * @param dataSource 数据源 / data source
     * @param insertSql 插入 SQL 语句 / insert SQL statement
     */
    public FlowAlarmEngine(DataSource dataSource, String insertSql) {
        this(dataSource, insertSql, DEFAULT_WORKER_COUNT, DEFAULT_WORKER_QUEUE_CAPACITY,
                DEFAULT_RECEIVER_QUEUE_CAPACITY, RejectPolicy.DROP);
    }

    /**
     * 创建基于 Flow 的告警引擎。
     * Create flow-based alarm engine.
     * @param dataSource 数据源 / data source
     * @param insertSql 插入 SQL 语句 / insert SQL statement
     * @param workerCount Worker 数量 / Worker count
     * @param workerQueueCapacity Worker 队列容量 / Worker queue capacity
     * @param receiverQueueCapacity Receiver 队列容量 / Receiver queue capacity
     * @param rejectPolicy 拒绝策略 / rejection policy
     */
    public FlowAlarmEngine(DataSource dataSource, String insertSql,
                           int workerCount, int workerQueueCapacity,
                           int receiverQueueCapacity, RejectPolicy rejectPolicy) {
        // 创建批量数据库执行器
        // Create batch database executor
        this.dbExecutor = new BatchDBExecutor(dataSource, insertSql);

        // 创建告警指标
        // Create alarm metrics
        this.metrics = new AlarmMetrics();

        // 创建流水线节点列表
        // Create pipeline nodes list
        List<PipelineNode> pipelineNodes = createPipelineNodes();

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
        for (PipelineNode node : pipelineNodes) {
            AlarmFlowAdapter adapter = new AlarmFlowAdapter(node);
            builder.addNode(adapter);
        }

        // 构建引擎（不启动）
        // Build engine (without starting)
        this.flowEngine = builder.buildWithoutStart();

        log.info("FlowAlarmEngine created with {} workers, workerQueueCapacity={}, receiverQueueCapacity={}, policy={}",
                workerCount, workerQueueCapacity, receiverQueueCapacity, rejectPolicy);
    }

    /**
     * 创建流水线节点列表。
     * Create pipeline nodes list.
     */
    private List<PipelineNode> createPipelineNodes() {
        List<PipelineNode> nodes = new ArrayList<>(9);

        // 1. ReceiveNode - 接收节点
        nodes.add(new ReceiveNode());

        // 2. FilterNode - 本地过滤节点
        nodes.add(new FilterNode());

        // 3. MaskingNode - 本地屏蔽节点
        nodes.add(new MaskingNode());

        // 4. AnalysisNode - 业务分析节点
        nodes.add(new AnalysisNode());

        // 5. PersistenceNode - 持久化节点
        nodes.add(new PersistenceNode(dbExecutor));

        // 6. NbNotifyNode - 北向通知准备节点
        nodes.add(new NbNotifyNode());

        // 7. NbFilterNode - 北向过滤节点
        nodes.add(new NbFilterNode());

        // 8. NbMaskingNode - 北向屏蔽节点
        nodes.add(new NbMaskingNode());

        // 9. NbPushNode - 北向推送节点
        nodes.add(new NbPushNode());

        log.info("Pipeline nodes created: 9 nodes from Receive to NB-Push");

        return nodes;
    }

    /**
     * 映射拒绝策略。
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
     * 启动引擎。
     * Start engine.
     */
    public void start() {
        if (running) {
            log.warn("FlowAlarmEngine is already running");
            return;
        }

        log.info("===========================================");
        log.info("FlowAlarmEngine starting...");
        log.info("===========================================");

        flowEngine.start();

        running = true;

        log.info("===========================================");
        log.info("FlowAlarmEngine started successfully");
        log.info("===========================================");
    }

    @Override
    public boolean submit(AlarmEvent event) {
        if (!running) {
            log.warn("FlowAlarmEngine is not running, rejecting event: {}", event.getId());
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
        // 同步流引擎指标到告警指标（可选）
        // Sync flow engine metrics to alarm metrics (optional)
        FlowMetrics flowMetrics = flowEngine.getMetrics();
        if (flowMetrics != null) {
            // 可以根据需要同步指标
            // Can sync metrics based on needs
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

        log.info("Shutting down FlowAlarmEngine...");
        flowEngine.shutdown(30, java.util.concurrent.TimeUnit.SECONDS);
        dbExecutor.shutdown();
        running = false;
        log.info("FlowAlarmEngine shut down completed");
    }

    /**
     * 获取流引擎。
     * Get flow engine.
     */
    public ShardedFlowEngine<String, AlarmEvent> getFlowEngine() {
        return flowEngine;
    }

    /**
     * 获取批量数据库执行器。
     * Get batch database executor.
     */
    public BatchDBExecutor getDbExecutor() {
        return dbExecutor;
    }
}
