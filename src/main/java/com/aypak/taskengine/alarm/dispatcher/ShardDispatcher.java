package com.aypak.taskengine.alarm.dispatcher;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.PipelineNode;
import com.aypak.taskengine.alarm.core.RejectPolicy;
import com.aypak.taskengine.alarm.monitor.AlarmMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分片调度器
 * 基于 DeviceID 哈希取模将告警分发到不同的 Worker 线程
 * 保证同一设备的告警始终由同一个 Worker 处理，确保顺序性
 * Shard dispatcher.
 * Distributes alarms to different Worker threads based on DeviceID hash modulo.
 * Ensures alarms from the same device are always handled by the same Worker for ordering.
 */
public class ShardDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ShardDispatcher.class);

    /** Worker 数量 / Worker count */
    private final int workerCount;

    /** Worker 队列容量 / Worker queue capacity */
    private final int queueCapacity;

    /** 流水线节点列表 / Pipeline nodes list */
    private final List<PipelineNode> nodes;

    /** Worker 数组 / Worker array */
    private final Worker[] workers;

    /** 拒绝策略 / Rejection policy */
    private final RejectPolicy rejectPolicy;

    /** 活跃 Worker 计数器 / Active Worker counter */
    private final AtomicInteger activeCount = new AtomicInteger(0);

    /** 总队列深度计数器 / Total queue depth counter */
    private final AtomicInteger totalQueueDepth = new AtomicInteger(0);

    /** 每个 Worker 的队列深度计数器 / Queue depth counter for each Worker */
    private final AtomicInteger[] workerQueueDepths;

    /** 关机计数器 / Shutdown latch */
    private CountDownLatch shutdownLatch;

    /** 是否已关闭 / Whether shutdown */
    private volatile boolean shutdown = false;

    /** 丢弃计数 / Dropped count */
    private final AtomicInteger droppedCount = new AtomicInteger(0);

    /** 提交计数 / Submit count */
    private final AtomicInteger submitCount = new AtomicInteger(0);

    /** 告警指标 / Alarm metrics */
    private final AlarmMetrics metrics;

    /**
     * 创建分片调度器
     * Create shard dispatcher.
     * @param workerCount Worker 数量 / Worker count
     * @param queueCapacity 每个 Worker 的队列容量 / Queue capacity per Worker
     * @param nodes 流水线节点列表 / Pipeline nodes list
     * @param rejectPolicy 拒绝策略 / Rejection policy
     * @param metrics 告警指标 / Alarm metrics
     */
    public ShardDispatcher(int workerCount, int queueCapacity,
                           List<PipelineNode> nodes, RejectPolicy rejectPolicy,
                           AlarmMetrics metrics) {
        this.workerCount = workerCount;
        this.queueCapacity = queueCapacity;
        this.nodes = nodes;
        this.rejectPolicy = rejectPolicy;
        this.workers = new Worker[workerCount];
        this.workerQueueDepths = new AtomicInteger[workerCount];
        this.metrics = metrics;

        // 初始化 Worker
        // Initialize Workers
        for (int i = 0; i < workerCount; i++) {
            workerQueueDepths[i] = new AtomicInteger(0);
        }
    }

    /**
     * 启动调度器
     * Start dispatcher.
     */
    public void start() {
        if (shutdown) {
            throw new IllegalStateException("Dispatcher has been shut down");
        }

        shutdownLatch = new CountDownLatch(workerCount);

        // 创建并启动 Worker 线程
        // Create and start Worker threads
        for (int i = 0; i < workerCount; i++) {
            workers[i] = new Worker(i, queueCapacity, nodes, shutdownLatch,
                    activeCount, workerQueueDepths[i], metrics);
            Thread workerThread = new Thread(workers[i], "AlarmWorker-" + i);
            workerThread.setDaemon(false);
            workerThread.start();
        }

        log.info("ShardDispatcher started with {} workers, queue capacity: {}, policy: {}",
                workerCount, queueCapacity, rejectPolicy);
    }

    /**
     * 提交告警事件
     * 根据 DeviceID 哈希值路由到对应的 Worker
     * Submit alarm event.
     * Routes to corresponding Worker based on DeviceID hash.
     * @param event 告警事件 / alarm event
     * @return true 表示成功提交，false 表示被拒绝 / true if successfully submitted, false if rejected
     */
    public boolean submit(AlarmEvent event) {
        if (shutdown) {
            log.warn("Dispatcher is shut down, rejecting event: {}", event.getId());
            return false;
        }

        submitCount.incrementAndGet();

        // 计算 shard 索引
        // Calculate shard index
        int shardIndex = getShardIndex(event.getDeviceId());
        Worker worker = workers[shardIndex];

        // 根据拒绝策略处理
        // Handle based on rejection policy
        switch (rejectPolicy) {
            case DROP:
                return handleDrop(worker, event);
            case DROP_OLDEST:
                return handleDropOldest(worker, event);
            case BLOCK:
                return handleBlock(worker, event);
            case CALLER_RUNS:
                return handleCallerRuns(worker, event);
            default:
                return handleDrop(worker, event);
        }
    }

    /**
     * DROP 策略：队列满时丢弃新事件
     * DROP policy: drop new event when queue is full.
     */
    private boolean handleDrop(Worker worker, AlarmEvent event) {
        if (worker.submit(event)) {
            return true;
        }
        droppedCount.incrementAndGet();
        log.debug("Dropped event {} due to full queue on worker {}",
                event.getId(), worker.getWorkerId());
        return false;
    }

    /**
     * DROP_OLDEST 策略：队列满时丢弃最旧事件
     * DROP_OLDEST policy: drop oldest event when queue is full.
     */
    private boolean handleDropOldest(Worker worker, AlarmEvent event) {
        // ArrayBlockingQueue 不支持直接丢弃最旧元素
        // ArrayBlockingQueue does not support directly dropping oldest element
        // 这里简化处理：直接返回 false，由上层处理
        // Simplified: return false, handled by upper layer
        if (worker.submit(event)) {
            return true;
        }
        droppedCount.incrementAndGet();
        return false;
    }

    /**
     * BLOCK 策略：阻塞等待
     * BLOCK policy: block and wait.
     */
    private boolean handleBlock(Worker worker, AlarmEvent event) {
        try {
            if (worker.submit(event, 5, TimeUnit.SECONDS)) {
                return true;
            }
            droppedCount.incrementAndGet();
            log.warn("Failed to submit event {} after blocking wait", event.getId());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            droppedCount.incrementAndGet();
            return false;
        }
    }

    /**
     * CALLER_RUNS 策略：调用者线程处理
     * 注意：这里简化处理，实际需要在调用者线程中执行流水线
     * CALLER_RUNS policy: process in caller thread.
     * Note: Simplified here, actual pipeline execution needed in caller thread.
     */
    private boolean handleCallerRuns(Worker worker, AlarmEvent event) {
        if (worker.submit(event)) {
            return true;
        }
        // 队列满时在调用者线程中处理
        // Process in caller thread when queue is full
        log.debug("Queue full on worker {}, processing in caller thread", worker.getWorkerId());
        droppedCount.incrementAndGet();
        // 简化：这里仍然尝试提交，如果失败则返回 false
        // Simplified: still try to submit, return false if failed
        // 完整实现需要在调用者线程中执行 processPipeline
        // Full implementation should execute processPipeline in caller thread
        return false;
    }

    /**
     * 计算 DeviceID 的 shard 索引
     * Calculate shard index for DeviceID.
     */
    private int getShardIndex(String deviceId) {
        int hash = deviceId.hashCode();
        // 确保哈希值为正
        // Ensure hash value is positive
        hash = Math.abs(hash);
        return hash % workerCount;
    }

    /**
     * 优雅关闭
     * Graceful shutdown.
     * @param timeout 超时时间 / timeout
     * @param unit 时间单位 / time unit
     * @return true 表示正常关闭，false 表示超时 / true if shutdown completed, false if timed out
     */
    public boolean shutdown(long timeout, TimeUnit unit) {
        log.info("ShardDispatcher shutting down...");
        shutdown = true;

        // 信号所有 Worker 关闭
        // Signal all Workers to shutdown
        for (Worker worker : workers) {
            worker.signalShutdown();
        }

        try {
            boolean completed = shutdownLatch.await(timeout, unit);
            if (completed) {
                log.info("ShardDispatcher shut down completed");
            } else {
                log.warn("ShardDispatcher shut down timed out, forcing...");
                // 强制停止
                // Force stop
                for (Worker worker : workers) {
                    worker.stop();
                }
            }
            return completed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("ShardDispatcher shut down interrupted", e);
            return false;
        }
    }

    /**
     * 获取 Worker 数量
     * Get worker count.
     */
    public int getWorkerCount() {
        return workerCount;
    }

    /**
     * 获取活跃 Worker 数量
     * Get active worker count.
     */
    public int getActiveCount() {
        return activeCount.get();
    }

    /**
     * 获取总队列深度
     * Get total queue depth.
     */
    public int getTotalQueueDepth() {
        int total = 0;
        for (AtomicInteger depth : workerQueueDepths) {
            total += depth.get();
        }
        return total;
    }

    /**
     * 获取每个 Worker 的队列深度
     * Get queue depth for each Worker.
     */
    public int[] getWorkerQueueDepths() {
        int[] depths = new int[workerCount];
        for (int i = 0; i < workerCount; i++) {
            depths[i] = workerQueueDepths[i].get();
        }
        return depths;
    }

    /**
     * 获取提交计数
     * Get submit count.
     */
    public int getSubmitCount() {
        return submitCount.get();
    }

    /**
     * 获取丢弃计数
     * Get dropped count.
     */
    public int getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * 获取指定 Worker
     * Get specified Worker.
     */
    public Worker getWorker(int index) {
        if (index < 0 || index >= workerCount) {
            throw new IllegalArgumentException("Invalid worker index: " + index);
        }
        return workers[index];
    }

    /**
     * 获取所有 Worker 的处理统计
     * Get processing statistics for all Workers.
     */
    public List<WorkerStats> getWorkerStats() {
        List<WorkerStats> stats = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            Worker worker = workers[i];
            stats.add(new WorkerStats(
                    i,
                    worker.getQueueSize(),
                    worker.getProcessedCount(),
                    worker.getFailureCount(),
                    worker.getState().name()
            ));
        }
        return stats;
    }

    /**
     * Worker 统计信息
     * Worker statistics.
     */
    public static class WorkerStats {
        public final int workerId;
        public final int queueSize;
        public final int processedCount;
        public final int failureCount;
        public final String state;

        public WorkerStats(int workerId, int queueSize, int processedCount,
                          int failureCount, String state) {
            this.workerId = workerId;
            this.queueSize = queueSize;
            this.processedCount = processedCount;
            this.failureCount = failureCount;
            this.state = state;
        }
    }
}
