package com.aypak.engine.flow.dispatcher;

import com.aypak.engine.flow.core.FlowContext;
import com.aypak.engine.flow.core.FlowEvent;
import com.aypak.engine.flow.core.FlowNode;
import com.aypak.engine.flow.core.RejectPolicy;
import com.aypak.engine.flow.monitor.FlowMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分片调度器。
 * Shard dispatcher.
 *
 * <p>基于分片键哈希取模将事件分发到不同的 Worker 线程。</p>
 * <p>Distributes events to different Worker threads based on shard key hash modulo.</p>
 *
 * <p>保证同一分片键的事件始终由同一个 Worker 处理，确保顺序性。</p>
 * <p>Ensures events with the same shard key are always handled by the same Worker for ordering.</p>
 *
 * @param <K> 分片键类型 / shard key type
 * @param <T> 负载类型 / payload type
 */
public class ShardDispatcher<K, T> {

    private static final Logger log = LoggerFactory.getLogger(ShardDispatcher.class);

    /** Worker 数量 / Worker count */
    private final int workerCount;

    /** Worker 队列容量 / Worker queue capacity */
    private final int queueCapacity;

    /** 流水线节点列表 / Pipeline nodes list */
    private final List<FlowNode<K, T>> nodes;

    /** Worker 数组 / Worker array */
    private final FlowWorker<K, T>[] workers;

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

    /** 流指标 / Flow metrics */
    private final FlowMetrics metrics;

    /**
     * 创建分片调度器。
     * Create shard dispatcher.
     *
     * @param workerCount   Worker 数量 / Worker count
     * @param queueCapacity 每个 Worker 的队列容量 / Queue capacity per Worker
     * @param nodes         流水线节点列表 / Pipeline nodes list
     * @param rejectPolicy  拒绝策略 / Rejection policy
     * @param metrics       流指标 / Flow metrics
     */
    @SuppressWarnings("unchecked")
    public ShardDispatcher(int workerCount, int queueCapacity,
                           List<FlowNode<K, T>> nodes, RejectPolicy rejectPolicy,
                           FlowMetrics metrics) {
        this.workerCount = workerCount;
        this.queueCapacity = queueCapacity;
        this.nodes = nodes;
        this.rejectPolicy = rejectPolicy;
        this.workers = new FlowWorker[workerCount];
        this.workerQueueDepths = new AtomicInteger[workerCount];
        this.metrics = metrics;

        // 初始化 Worker 队列深度计数器
        // Initialize Worker queue depth counters
        for (int i = 0; i < workerCount; i++) {
            workerQueueDepths[i] = new AtomicInteger(0);
        }
    }

    /**
     * 启动调度器。
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
            workers[i] = new FlowWorker<>(i, queueCapacity, nodes, shutdownLatch,
                    activeCount, workerQueueDepths[i], metrics);
            Thread workerThread = new Thread(workers[i], "FlowWorker-" + i);
            workerThread.setDaemon(false);
            workerThread.start();
        }

        log.info("ShardDispatcher started with {} workers, queue capacity: {}, policy: {}",
                workerCount, queueCapacity, rejectPolicy);
    }

    /**
     * 提交事件。
     * Submit event.
     * 根据分片键哈希值路由到对应的 Worker。
     * Routes to corresponding Worker based on shard key hash.
     *
     * @param event 事件 / event
     * @return true 表示成功提交，false 表示被拒绝 / true if successfully submitted, false if rejected
     */
    public boolean submit(FlowEvent<K, T> event) {
        if (shutdown) {
            log.warn("Dispatcher is shut down, rejecting event: {}", event.getId());
            return false;
        }

        submitCount.incrementAndGet();

        // 计算 shard 索引
        // Calculate shard index
        int shardIndex = getShardIndex(event.getShardKey());
        FlowWorker<K, T> worker = workers[shardIndex];

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
     * 批量提交事件。
     * Submit events in batch.
     * 根据分片键哈希值路由到对应的 Worker。
     * Routes to corresponding Worker based on shard key hash.
     *
     * @param events 事件列表 / event list
     * @return 成功提交的事件数量 / number of successfully submitted events
     */
    public int submit(java.util.List<FlowEvent<K, T>> events) {
        if (shutdown) {
            log.warn("Dispatcher is shut down, rejecting batch events");
            return 0;
        }

        int successCount = 0;
        submitCount.addAndGet(events.size());

        for (FlowEvent<K, T> event : events) {
            // 计算 shard 索引
            // Calculate shard index
            int shardIndex = getShardIndex(event.getShardKey());
            FlowWorker<K, T> worker = workers[shardIndex];

            // 根据拒绝策略处理
            // Handle based on rejection policy
            boolean submitted = switch (rejectPolicy) {
                case DROP -> handleDrop(worker, event);
                case DROP_OLDEST -> handleDropOldest(worker, event);
                case BLOCK -> handleBlock(worker, event);
                case CALLER_RUNS -> handleCallerRuns(worker, event);
                default -> handleDrop(worker, event);
            };

            if (submitted) {
                successCount++;
            }
        }

        return successCount;
    }

    /**
     * DROP 策略：队列满时丢弃新事件。
     * DROP policy: drop new event when queue is full.
     */
    private boolean handleDrop(FlowWorker<K, T> worker, FlowEvent<K, T> event) {
        if (worker.submit(event)) {
            return true;
        }
        droppedCount.incrementAndGet();
        log.debug("Dropped event {} due to full queue on worker {}",
                event.getId(), worker.getWorkerId());
        return false;
    }

    /**
     * DROP_OLDEST 策略：队列满时丢弃最旧事件。
     * DROP_OLDEST policy: drop oldest event when queue is full.
     */
    private boolean handleDropOldest(FlowWorker<K, T> worker, FlowEvent<K, T> event) {
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
     * BLOCK 策略：阻塞等待。
     * BLOCK policy: block and wait.
     */
    private boolean handleBlock(FlowWorker<K, T> worker, FlowEvent<K, T> event) {
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
     * CALLER_RUNS 策略：调用者线程处理。
     * CALLER_RUNS policy: process in caller thread.
     */
    private boolean handleCallerRuns(FlowWorker<K, T> worker, FlowEvent<K, T> event) {
        if (worker.submit(event)) {
            return true;
        }
        // 队列满时在调用者线程中直接处理
        // Process in caller thread when queue is full
        log.debug("Queue full on worker {}, processing in caller thread", worker.getWorkerId());
        try {
            // 直接调用 Worker 的 run 方法来处理一个事件
            // This is a simplified implementation - in reality,
            // CALLER_RUNS would need more sophisticated handling
            event.setStatus(FlowEvent.ProcessingStatus.PROCESSING);
            for (FlowNode<K, T> node : nodes) {
                try {
                    boolean shouldContinue = node.process(event, new FlowContext());
                    if (!shouldContinue) {
                        break;
                    }
                } catch (Exception e) {
                    node.onFailure(event, e);
                    if (node.isCritical()) {
                        event.setStatus(FlowEvent.ProcessingStatus.FAILED);
                        if (metrics != null) {
                            metrics.recordFailure();
                        }
                        return true;
                    }
                }
            }
            event.setStatus(FlowEvent.ProcessingStatus.COMPLETED);
            if (metrics != null) {
                metrics.recordSuccess();
            }
            return true;
        } catch (Exception e) {
            log.error("CALLER_RUNS failed to process event", e);
            if (metrics != null) {
                metrics.recordFailure();
            }
            return true; // 仍然认为已处理
        }
    }

    /**
     * 计算分片索引。
     * Calculate shard index.
     */
    private int getShardIndex(K shardKey) {
        int hash = shardKey.hashCode();
        // 确保哈希值为正
        // Ensure hash value is positive
        hash = Math.abs(hash);
        return hash % workerCount;
    }

    /**
     * 优雅关闭。
     * Graceful shutdown.
     *
     * @param timeout 超时时间 / timeout
     * @param unit    时间单位 / time unit
     * @return true 表示正常关闭，false 表示超时 / true if shutdown completed, false if timed out
     */
    public boolean shutdown(long timeout, TimeUnit unit) {
        log.info("ShardDispatcher shutting down...");
        shutdown = true;

        // 信号所有 Worker 关闭
        // Signal all Workers to shutdown
        for (FlowWorker<K, T> worker : workers) {
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
                for (FlowWorker<K, T> worker : workers) {
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

    // ==================== Getters ====================

    /**
     * 获取 Worker 数量。
     * Get worker count.
     */
    public int getWorkerCount() {
        return workerCount;
    }

    /**
     * 获取活跃 Worker 数量。
     * Get active worker count.
     */
    public int getActiveCount() {
        return activeCount.get();
    }

    /**
     * 获取总队列深度。
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
     * 获取提交计数。
     * Get submit count.
     */
    public int getSubmitCount() {
        return submitCount.get();
    }

    /**
     * 获取丢弃计数。
     * Get dropped count.
     */
    public int getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * 获取指定 Worker。
     * Get specified Worker.
     */
    public FlowWorker<K, T> getWorker(int index) {
        if (index < 0 || index >= workerCount) {
            throw new IllegalArgumentException("Invalid worker index: " + index);
        }
        return workers[index];
    }

    /**
     * 获取所有 Worker 的处理统计。
     * Get processing statistics for all Workers.
     */
    public List<WorkerStats> getWorkerStats() {
        List<WorkerStats> stats = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            FlowWorker<K, T> worker = workers[i];
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
     * Worker 统计信息。
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
