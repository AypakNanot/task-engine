package com.aypak.taskengine.alarm.engine;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.PipelineNode;
import com.aypak.taskengine.alarm.core.RejectPolicy;
import com.aypak.taskengine.alarm.dispatcher.ShardDispatcher;
import com.aypak.taskengine.alarm.receiver.AlarmReceiver;
import com.aypak.taskengine.alarm.batch.BatchDBExecutor;
import com.aypak.taskengine.alarm.monitor.AlarmMetrics;
import com.aypak.taskengine.alarm.monitor.MonitorTask;
import com.aypak.taskengine.alarm.nodes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 告警引擎实现
 * 整合所有组件，提供统一的告警处理接口
 */
public class AlarmEngineImpl implements AlarmEngine {

    private static final Logger log = LoggerFactory.getLogger(AlarmEngineImpl.class);

    /** 默认 Worker 数量 */
    private static final int DEFAULT_WORKER_COUNT = 16;

    /** 默认 Worker 队列容量 */
    private static final int DEFAULT_WORKER_QUEUE_CAPACITY = 5000;

    /** 默认 Receiver 队列容量 */
    private static final int DEFAULT_RECEIVER_QUEUE_CAPACITY = 50000;

    /** 告警接收器 */
    private final AlarmReceiver receiver;

    /** 分片调度器 */
    private final ShardDispatcher dispatcher;

    /** 批量数据库执行器 */
    private final BatchDBExecutor dbExecutor;

    /** 告警指标 */
    private final AlarmMetrics metrics;

    /** 监控任务 */
    private final MonitorTask monitorTask;

    /** 优雅停机处理器 */
    private final GracefulShutdown gracefulShutdown;

    /** 运行标志 */
    private volatile boolean running = false;

    /**
     * 创建告警引擎
     * @param dataSource 数据源
     * @param insertSql 插入 SQL 语句
     */
    public AlarmEngineImpl(DataSource dataSource, String insertSql) {
        this(dataSource, insertSql, DEFAULT_WORKER_COUNT, DEFAULT_WORKER_QUEUE_CAPACITY,
                DEFAULT_RECEIVER_QUEUE_CAPACITY, RejectPolicy.DROP);
    }

    /**
     * 创建告警引擎
     * @param dataSource 数据源
     * @param insertSql 插入 SQL 语句
     * @param workerCount Worker 数量
     * @param workerQueueCapacity Worker 队列容量
     * @param receiverQueueCapacity Receiver 队列容量
     * @param rejectPolicy 拒绝策略
     */
    public AlarmEngineImpl(DataSource dataSource, String insertSql,
                          int workerCount, int workerQueueCapacity,
                          int receiverQueueCapacity, RejectPolicy rejectPolicy) {
        // 创建流水线节点列表
        List<PipelineNode> nodes = createPipelineNodes(dataSource, insertSql);

        // 创建告警接收器
        this.receiver = new AlarmReceiver(receiverQueueCapacity, rejectPolicy);

        // 创建分片调度器
        this.dispatcher = new ShardDispatcher(workerCount, workerQueueCapacity, nodes, rejectPolicy);

        // 创建批量数据库执行器
        this.dbExecutor = new BatchDBExecutor(dataSource, insertSql);

        // 创建告警指标
        this.metrics = new AlarmMetrics();

        // 创建监控任务
        this.monitorTask = new MonitorTask(metrics, receiver, dispatcher);

        // 创建优雅停机处理器
        this.gracefulShutdown = new GracefulShutdown(receiver, dispatcher, dbExecutor, 30);

        log.info("AlarmEngineImpl created with {} workers, workerQueueCapacity={}, receiverQueueCapacity={}, policy={}",
                workerCount, workerQueueCapacity, receiverQueueCapacity, rejectPolicy);
    }

    /**
     * 创建流水线节点列表
     */
    private List<PipelineNode> createPipelineNodes(DataSource dataSource, String insertSql) {
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

        // 6. NB-NotifyNode - 北向通知准备节点
        nodes.add(new NbNotifyNode());

        // 7. NB-FilterNode - 北向过滤节点
        nodes.add(new NbFilterNode());

        // 8. NB-MaskingNode - 北向屏蔽节点
        nodes.add(new NbMaskingNode());

        // 9. NB-PushNode - 北向推送节点
        nodes.add(new NbPushNode());

        log.info("Pipeline nodes created: 9 nodes from Receive to NB-Push");

        return nodes;
    }

    /**
     * 启动引擎
     */
    public void start() {
        if (running) {
            log.warn("AlarmEngine is already running");
            return;
        }

        log.info("===========================================");
        log.info("AlarmEngine starting...");
        log.info("===========================================");

        // 启动分片调度器
        dispatcher.start();

        // 启动监控任务
        monitorTask.start();

        // 注册优雅停机钩子
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

        // 记录进入指标
        metrics.recordIncoming();

        // 提交到接收器
        boolean accepted = receiver.receive(event);

        if (!accepted) {
            // 记录丢弃指标
            metrics.recordDropped();
            return false;
        }

        // 从接收器轮询并提交到分片调度器
        // 注意：这里采用直接分发模式，receiver 只作为背压控制
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

        // 执行优雅停机
        gracefulShutdown.shutdown();

        // 停止监控任务
        monitorTask.stop();

        running = false;
    }

    /**
     * 获取告警接收器
     */
    public AlarmReceiver getReceiver() {
        return receiver;
    }

    /**
     * 获取分片调度器
     */
    public ShardDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * 获取批量数据库执行器
     */
    public BatchDBExecutor getDbExecutor() {
        return dbExecutor;
    }
}
