package com.aypak.engine.flow.dispatcher;

import com.aypak.engine.flow.core.FlowEvent;
import com.aypak.engine.flow.core.FlowContext;
import com.aypak.engine.flow.core.FlowNode;
import com.aypak.engine.flow.monitor.FlowMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker 线程实现。
 * Worker thread implementation.
 *
 * <p>每个 Worker 拥有独立的队列和流水线处理逻辑。</p>
 * <p>Each Worker has its own queue and pipeline processing logic.</p>
 *
 * <p>同一分片键的事件始终由同一个 Worker 处理，保证顺序性。</p>
 * <p>Events with the same shard key are always handled by the same Worker for ordering.</p>
 *
 * @param <K> 分片键类型 / shard key type
 * @param <T> 负载类型 / payload type
 */
public class FlowWorker<K, T> implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(FlowWorker.class);

    /** Worker ID */
    private final int workerId;

    /** 事件队列 / Event queue */
    private final BlockingQueue<FlowEvent<K, T>> queue;

    /** 流水线节点列表 / Pipeline nodes list */
    private final List<FlowNode<K, T>> nodes;

    /** 运行标志 / Running flag */
    private volatile boolean running = true;

    /** 关机信号 / Shutdown signal */
    private volatile boolean shutdownSignaled = false;

    /** 关机完成计数器 / Shutdown completion latch */
    private final CountDownLatch shutdownLatch;

    /** 活跃线程计数器 / Active thread counter */
    private final AtomicInteger activeCount;

    /** 队列深度计数器 / Queue depth counter */
    private final AtomicInteger queueDepth;

    /** 流指标 / Flow metrics */
    private final FlowMetrics metrics;

    /** Worker 状态 / Worker state */
    private volatile WorkerState state = WorkerState.IDLE;

    /** 处理计数 / Processed count */
    private final AtomicInteger processedCount = new AtomicInteger(0);

    /** 失败计数 / Failure count */
    private final AtomicInteger failureCount = new AtomicInteger(0);

    /**
     * 创建 Worker。
     * Create Worker.
     *
     * @param workerId     Worker ID
     * @param queueCapacity 队列容量 / queue capacity
     * @param nodes         节点列表 / nodes list
     * @param shutdownLatch 关机计数器 / shutdown latch
     * @param activeCount   活跃计数 / active count
     * @param queueDepth    队列深度计数 / queue depth count
     * @param metrics       流指标 / flow metrics
     */
    public FlowWorker(int workerId, int queueCapacity, List<FlowNode<K, T>> nodes,
                      CountDownLatch shutdownLatch, AtomicInteger activeCount,
                      AtomicInteger queueDepth, FlowMetrics metrics) {
        this.workerId = workerId;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.nodes = new ArrayList<>(nodes);
        this.shutdownLatch = shutdownLatch;
        this.activeCount = activeCount;
        this.queueDepth = queueDepth;
        this.metrics = metrics;
    }

    @Override
    public void run() {
        activeCount.incrementAndGet();
        log.info("FlowWorker-{} started", workerId);

        try {
            while (running) {
                try {
                    // 等待事件，带超时以便检查 shutdown 信号
                    // Wait for events, with timeout to check shutdown signal
                    FlowEvent<K, T> event = queue.poll(100, TimeUnit.MILLISECONDS);

                    if (event == null) {
                        // 队列为空，检查是否需要关闭
                        // Queue is empty, check if shutdown is needed
                        if (shutdownSignaled) {
                            log.info("FlowWorker-{} shutting down, queue is empty", workerId);
                            break;
                        }
                        state = WorkerState.IDLE;
                        continue;
                    }

                    state = WorkerState.PROCESSING;
                    queueDepth.decrementAndGet();
                    processedCount.incrementAndGet();

                    // 处理流水线
                    // Process pipeline
                    processPipeline(event);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("FlowWorker-{} interrupted", workerId);
                    break;
                } catch (Exception e) {
                    // 捕获未知异常，防止 Worker 线程崩溃
                    // Catch unknown exceptions to prevent Worker thread crash
                    log.error("FlowWorker-{} unexpected error", workerId, e);
                    failureCount.incrementAndGet();
                }
            }
        } finally {
            running = false;
            state = WorkerState.STOPPED;
            activeCount.decrementAndGet();
            shutdownLatch.countDown();
            log.info("FlowWorker-{} stopped. Processed: {}, Failures: {}",
                    workerId, processedCount.get(), failureCount.get());
        }
    }

    /**
     * 处理事件流水线。
     * Process event pipeline.
     */
    private void processPipeline(FlowEvent<K, T> event) {
        long startTime = System.currentTimeMillis();
        event.setReceiveTime(startTime);
        event.setStatus(FlowEvent.ProcessingStatus.PROCESSING);

        FlowContext context = new FlowContext();

        try {
            for (FlowNode<K, T> node : nodes) {
                if (!running && !node.isCritical()) {
                    // 关闭期间跳过非关键节点
                    // Skip non-critical nodes during shutdown
                    continue;
                }

                try {
                    long nodeStart = System.currentTimeMillis();
                    boolean shouldContinue = node.process(event, context);
                    long nodeLatency = System.currentTimeMillis() - nodeStart;
                    context.recordNodeLatency(node.getNodeName(), nodeLatency);

                    if (!shouldContinue) {
                        // 节点决定停止处理（如过滤、屏蔽）
                        // Node decides to stop processing (e.g., filtering, masking)
                        break;
                    }

                    if (!context.shouldContinue()) {
                        // 上下文标记停止
                        // Context marked to stop
                        break;
                    }

                } catch (Exception e) {
                    node.onFailure(event, e);
                    if (node.isCritical()) {
                        // 关键节点失败，终止处理
                        // Critical node failure, terminate processing
                        event.setStatus(FlowEvent.ProcessingStatus.FAILED);
                        event.setErrorMessage("Critical node " + node.getNodeName() + " failed: " + e.getMessage());
                        failureCount.incrementAndGet();
                        if (metrics != null) {
                            metrics.recordFailure();
                        }
                        return;
                    }
                    // 非关键节点失败，继续处理
                    // Non-critical node failure, continue processing
                    log.debug("Node {} failed but continuing: {}", node.getNodeName(), e.getMessage());
                }
            }

            // 处理完成
            // Processing completed
            long completeTime = System.currentTimeMillis();
            event.setCompleteTime(completeTime);

            if (event.getStatus() != FlowEvent.ProcessingStatus.FAILED) {
                if (context.isDropped()) {
                    event.setStatus(FlowEvent.ProcessingStatus.DROPPED);
                } else if (context.isPersisted() && context.isNotified()) {
                    event.setStatus(FlowEvent.ProcessingStatus.COMPLETED);
                } else if (context.isPersisted()) {
                    event.setStatus(FlowEvent.ProcessingStatus.PERSISTED);
                } else {
                    event.setStatus(FlowEvent.ProcessingStatus.COMPLETED);
                }
                // 记录成功处理
                // Record successful processing
                if (metrics != null) {
                    metrics.recordSuccess();
                }
            }

        } catch (Exception e) {
            log.error("FlowWorker-{} failed to process event {}", workerId, event.getId(), e);
            event.setStatus(FlowEvent.ProcessingStatus.FAILED);
            event.setErrorMessage(e.getMessage());
            failureCount.incrementAndGet();
            if (metrics != null) {
                metrics.recordFailure();
            }
        }
    }

    /**
     * 提交事件到队列。
     * Submit event to queue.
     *
     * @param event 事件 / event
     * @return true 表示成功提交，false 表示队列已满 / true if successfully submitted, false if queue is full
     */
    public boolean submit(FlowEvent<K, T> event) {
        if (shutdownSignaled) {
            log.warn("FlowWorker-{} rejecting event, shutdown signaled", workerId);
            return false;
        }

        boolean offered = queue.offer(event);
        if (offered) {
            queueDepth.incrementAndGet();
        }
        return offered;
    }

    /**
     * 尝试提交事件，带超时。
     * Try to submit event with timeout.
     */
    public boolean submit(FlowEvent<K, T> event, long timeout, TimeUnit unit) throws InterruptedException {
        if (shutdownSignaled) {
            return false;
        }

        boolean offered = queue.offer(event, timeout, unit);
        if (offered) {
            queueDepth.incrementAndGet();
        }
        return offered;
    }

    /**
     * 信号关闭。
     * Signal shutdown.
     */
    public void signalShutdown() {
        log.info("FlowWorker-{} shutdown signaled", workerId);
        this.shutdownSignaled = true;
    }

    /**
     * 强制停止。
     * Force stop.
     */
    public void stop() {
        log.info("FlowWorker-{} stopping", workerId);
        this.running = false;
        this.shutdownSignaled = true;
    }

    // ==================== Getters ====================

    /**
     * 获取 Worker ID。
     * Get worker ID.
     */
    public int getWorkerId() {
        return workerId;
    }

    /**
     * 获取队列大小。
     * Get queue size.
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 获取队列剩余容量。
     * Get queue remaining capacity.
     */
    public int getRemainingCapacity() {
        return queue.remainingCapacity();
    }

    /**
     * 获取处理计数。
     * Get processed count.
     */
    public int getProcessedCount() {
        return processedCount.get();
    }

    /**
     * 获取失败计数。
     * Get failure count.
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 获取 Worker 状态。
     * Get worker state.
     */
    public WorkerState getState() {
        return state;
    }

    /**
     * 是否正在运行。
     * Whether running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 是否已关闭。
     * Whether shutdown.
     */
    public boolean isShutdown() {
        return shutdownSignaled;
    }

    /**
     * Worker 状态枚举。
     * Worker state enum.
     */
    public enum WorkerState {
        /** 空闲 / Idle */
        IDLE,
        /** 处理中 / Processing */
        PROCESSING,
        /** 停止中 / Stopping */
        STOPPING,
        /** 已停止 / Stopped */
        STOPPED
    }
}
