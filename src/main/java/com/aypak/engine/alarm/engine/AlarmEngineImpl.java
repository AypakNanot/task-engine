package com.aypak.engine.alarm.engine;

import com.aypak.engine.alarm.core.AlarmEvent;
import com.aypak.engine.alarm.core.PipelineNode;
import com.aypak.engine.alarm.core.RejectPolicy;
import com.aypak.engine.alarm.dispatcher.ShardDispatcher;
import com.aypak.engine.alarm.nodes.*;
import com.aypak.engine.alarm.receiver.AlarmReceiver;
import com.aypak.engine.alarm.batch.BatchDBExecutor;
import com.aypak.engine.alarm.monitor.AlarmMetrics;
import com.aypak.engine.alarm.monitor.MonitorTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 告警引擎实现
 * 整合所有组件，提供统一的告警处理接口
 * Alarm engine implementation.
 * Integrates all components and provides unified alarm processing interface.
 */
public class AlarmEngineImpl implements AlarmEngine {

    private static final Logger log = LoggerFactory.getLogger(AlarmEngineImpl.class);

    /** 默认 Worker 数量 / Default Worker count */
    private static final int DEFAULT_WORKER_COUNT = 16;

    /** 默认 Worker 队列容量 / Default Worker queue capacity */
    private static final int DEFAULT_WORKER_QUEUE_CAPACITY = 5000;

    /** 默认 Receiver 队列容量 / Default Receiver queue capacity */
    private static final int DEFAULT_RECEIVER_QUEUE_CAPACITY = 50000;

    /** 告警接收器 / Alarm receiver */
    private final AlarmReceiver receiver;

    /** 分片调度器 / Shard dispatcher */
    private final ShardDispatcher dispatcher;

    /** 批量数据库执行器 / Batch database executor */
    private final BatchDBExecutor dbExecutor;

    /** 告警指标 / Alarm metrics */
    private final AlarmMetrics metrics;

    /** 监控任务 / Monitor task */
    private final MonitorTask monitorTask;

    /** 优雅停机处理器 / Graceful shutdown handler */
    private final GracefulShutdown gracefulShutdown;

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
                DEFAULT_RECEIVER_QUEUE_CAPACITY, RejectPolicy.DROP);
    }

    /**
     * 创建告警引擎
     * Create alarm engine.
     * @param dataSource 数据源 / data source
     * @param insertSql 插入 SQL 语句 / insert SQL statement
     * @param workerCount Worker 数量 / Worker count
     * @param workerQueueCapacity Worker 队列容量 / Worker queue capacity
     * @param receiverQueueCapacity Receiver 队列容量 / Receiver queue capacity
     * @param rejectPolicy 拒绝策略 / rejection policy
     */
    public AlarmEngineImpl(DataSource dataSource, String insertSql,
                          int workerCount, int workerQueueCapacity,
                          int receiverQueueCapacity, RejectPolicy rejectPolicy) {
        // 创建批量数据库执行器（必须在 createPipelineNodes 之前创建）
        // Create batch DB executor (must be created before createPipelineNodes)
        this.dbExecutor = new BatchDBExecutor(dataSource, insertSql);

        // 创建告警指标（必须在 ShardDispatcher 之前创建）
        // Create alarm metrics (must be created before ShardDispatcher)
        this.metrics = new AlarmMetrics();

        // 创建流水线节点列表
        // Create pipeline nodes list
        List<PipelineNode> nodes = createPipelineNodes();

        // 创建告警接收器
        // Create alarm receiver
        this.receiver = new AlarmReceiver(receiverQueueCapacity, rejectPolicy);

        // 创建分片调度器
        // Create shard dispatcher
        this.dispatcher = new ShardDispatcher(workerCount, workerQueueCapacity, nodes, rejectPolicy, metrics);

        // 创建监控任务
        // Create monitor task
        this.monitorTask = new MonitorTask(metrics, receiver, dispatcher);

        // 创建优雅停机处理器
        // Create graceful shutdown handler
        this.gracefulShutdown = new GracefulShutdown(receiver, dispatcher, dbExecutor, 30);

        log.info("AlarmEngineImpl created with {} workers, workerQueueCapacity={}, receiverQueueCapacity={}, policy={}",
                workerCount, workerQueueCapacity, receiverQueueCapacity, rejectPolicy);
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

        // 启动分片调度器 / Start shard dispatcher
        dispatcher.start();

        // 启动监控任务 / Start monitor task
        monitorTask.start();

        // 注册优雅停机钩子 / Register graceful shutdown hook
        gracefulShutdown.registerShutdownHook();

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

        // 直接提交到分片调度器，receiver 仅用于监控指标
        // Submit directly to shard dispatcher, receiver is only for monitoring metrics
        return dispatcher.submit(event);
    }

    @Override
    public AlarmMetrics getMetrics() {
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

        // 执行优雅停机 / Execute graceful shutdown
        gracefulShutdown.shutdown();

        // 停止监控任务 / Stop monitor task
        monitorTask.stop();

        running = false;
    }

    /**
     * 获取告警接收器
     * Get alarm receiver.
     */
    public AlarmReceiver getReceiver() {
        return receiver;
    }

    /**
     * 获取分片调度器
     * Get shard dispatcher.
     */
    public ShardDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * 获取批量数据库执行器
     * Get batch database executor.
     */
    public BatchDBExecutor getDbExecutor() {
        return dbExecutor;
    }
}
