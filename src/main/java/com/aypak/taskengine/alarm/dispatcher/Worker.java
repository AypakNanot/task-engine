package com.aypak.taskengine.alarm.dispatcher;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.PipelineContext;
import com.aypak.taskengine.alarm.core.PipelineNode;
import com.aypak.taskengine.alarm.monitor.AlarmMetrics;
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
 * Worker 线程实现
 * 每个 Worker 拥有独立的队列和流水线处理逻辑
 * 同一 DeviceID 的告警始终由同一个 Worker 处理，保证顺序性
 * Worker thread implementation.
 * Each Worker has its own queue and pipeline processing logic.
 * Alarms from the same DeviceID are always handled by the same Worker for ordering.
 */
public class Worker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    /** Worker ID */
    private final int workerId;

    /** 告警队列 / Alarm queue */
    private final BlockingQueue<AlarmEvent> queue;

    /** 流水线节点列表 / Pipeline nodes list */
    private final List<PipelineNode> nodes;

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

    /** 告警指标 / Alarm metrics */
    private final AlarmMetrics metrics;

    /** Worker 状态 / Worker state */
    private volatile WorkerState state = WorkerState.IDLE;

    /** 处理的告警计数 / Processed alarm count */
    private final AtomicInteger processedCount = new AtomicInteger(0);

    /** 失败计数 / Failure count */
    private final AtomicInteger failureCount = new AtomicInteger(0);

    public Worker(int workerId, int queueCapacity, List<PipelineNode> nodes,
                  CountDownLatch shutdownLatch, AtomicInteger activeCount,
                  AtomicInteger queueDepth, AlarmMetrics metrics) {
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
        log.info("Worker-{} started", workerId);

        try {
            while (running) {
                try {
                    // 等待告警，带超时以便检查 shutdown 信号
                    // Wait for alarms, with timeout to check shutdown signal
                    AlarmEvent event = queue.poll(100, TimeUnit.MILLISECONDS);

                    if (event == null) {
                        // 队列为空，检查是否需要关闭
                        // Queue is empty, check if shutdown is needed
                        if (shutdownSignaled) {
                            log.info("Worker-{} shutting down, queue is empty", workerId);
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
                    log.warn("Worker-{} interrupted", workerId);
                    break;
                } catch (Exception e) {
                    // 捕获未知异常，防止 Worker 线程崩溃
                    // Catch unknown exceptions to prevent Worker thread crash
                    log.error("Worker-{} unexpected error", workerId, e);
                    failureCount.incrementAndGet();
                }
            }
        } finally {
            running = false;
            state = WorkerState.STOPPED;
            activeCount.decrementAndGet();
            shutdownLatch.countDown();
            log.info("Worker-{} stopped. Processed: {}, Failures: {}",
                    workerId, processedCount.get(), failureCount.get());
        }
    }

    /**
     * 处理告警流水线
     * Process alarm pipeline.
     */
    private void processPipeline(AlarmEvent event) {
        long startTime = System.currentTimeMillis();
        event.setReceiveTime(startTime);
        event.setStatus(AlarmEvent.ProcessingStatus.PROCESSING);

        PipelineContext context = new PipelineContext();

        try {
            for (PipelineNode node : nodes) {
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
                        event.setStatus(AlarmEvent.ProcessingStatus.FAILED);
                        event.setErrorMessage("Critical node " + node.getNodeName() + " failed: " + e.getMessage());
                        failureCount.incrementAndGet();
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

            if (event.getStatus() != AlarmEvent.ProcessingStatus.FAILED) {
                if (context.isDropped()) {
                    event.setStatus(AlarmEvent.ProcessingStatus.DROPPED);
                } else if (context.isPersisted() && context.isNotified()) {
                    event.setStatus(AlarmEvent.ProcessingStatus.COMPLETED);
                } else if (context.isPersisted()) {
                    event.setStatus(AlarmEvent.ProcessingStatus.PERSISTED);
                } else {
                    event.setStatus(AlarmEvent.ProcessingStatus.COMPLETED);
                }
                // 记录成功处理
                // Record successful processing
                if (metrics != null) {
                    metrics.recordSuccess();
                }
            }

        } catch (Exception e) {
            log.error("Worker-{} failed to process alarm {}", workerId, event.getId(), e);
            event.setStatus(AlarmEvent.ProcessingStatus.FAILED);
            event.setErrorMessage(e.getMessage());
            failureCount.incrementAndGet();
            // 记录失败到指标
            // Record failure to metrics
            if (metrics != null) {
                metrics.recordFailure();
            }
        }
    }

    /**
     * 提交告警到队列
     * Submit alarm to queue.
     * @param event 告警事件 / alarm event
     * @return true 表示成功提交，false 表示队列已满 / true if successfully submitted, false if queue is full
     */
    public boolean submit(AlarmEvent event) {
        if (shutdownSignaled) {
            log.warn("Worker-{} rejecting event, shutdown signaled", workerId);
            return false;
        }

        boolean offered = queue.offer(event);
        if (offered) {
            queueDepth.incrementAndGet();
        }
        return offered;
    }

    /**
     * 尝试提交告警，带超时
     * Try to submit alarm with timeout.
     */
    public boolean submit(AlarmEvent event, long timeout, TimeUnit unit) throws InterruptedException {
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
     * 信号关闭
     * Signal shutdown.
     */
    public void signalShutdown() {
        log.info("Worker-{} shutdown signaled", workerId);
        this.shutdownSignaled = true;
    }

    /**
     * 强制停止
     * Force stop.
     */
    public void stop() {
        log.info("Worker-{} stopping", workerId);
        this.running = false;
        this.shutdownSignaled = true;
    }

    /**
     * 获取 Worker ID
     * Get worker ID.
     */
    public int getWorkerId() {
        return workerId;
    }

    /**
     * 获取队列大小
     * Get queue size.
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 获取队列剩余容量
     * Get queue remaining capacity.
     */
    public int getRemainingCapacity() {
        return queue.remainingCapacity();
    }

    /**
     * 获取处理计数
     * Get processed count.
     */
    public int getProcessedCount() {
        return processedCount.get();
    }

    /**
     * 获取失败计数
     * Get failure count.
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * 获取 Worker 状态
     * Get worker state.
     */
    public WorkerState getState() {
        return state;
    }

    /**
     * 是否正在运行
     * Whether running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 是否已关闭
     * Whether shutdown.
     */
    public boolean isShutdown() {
        return shutdownSignaled;
    }

    /**
     * Worker 状态枚举
     * Worker state enum.
     */
    public enum WorkerState {
        IDLE,       // 空闲 / Idle
        PROCESSING, // 处理中 / Processing
        STOPPING,   // 停止中 / Stopping
        STOPPED     // 已停止 / Stopped
    }
}
