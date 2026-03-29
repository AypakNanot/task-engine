package com.aypak.taskengine.alarm.receiver;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.RejectPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 告警接收器。
 * 负责接收外部告警并提供背压保护。
 * Alarm receiver.
 * Responsible for receiving external alarms and providing backpressure protection.
 */
public class AlarmReceiver {

    private static final Logger log = LoggerFactory.getLogger(AlarmReceiver.class);

    /** 默认队列容量 / Default queue capacity */
    private static final int DEFAULT_CAPACITY = 50000;

    /** 告警队列 / Alarm queue */
    private final BlockingQueue<AlarmEvent> queue;

    /** 队列容量 / Queue capacity */
    private final int capacity;

    /** 拒绝策略 / Rejection policy */
    private final RejectPolicy rejectPolicy;

    /** 运行标志 / Running flag */
    private volatile boolean running = true;

    /** 接收计数 / Receive count */
    private final AtomicLong receiveCount = new AtomicLong(0);

    /** 丢弃计数 / Drop count */
    private final AtomicLong dropCount = new AtomicLong(0);

    /** 丢弃日志 / Drop logger */
    private final DropLogger dropLogger = new DropLogger();

    /** 最后接收时间 / Last receive time */
    private volatile long lastReceiveTime = System.currentTimeMillis();

    /**
     * 创建告警接收器。
     * Create alarm receiver.
     */
    public AlarmReceiver() {
        this(DEFAULT_CAPACITY, RejectPolicy.DROP);
    }

    /**
     * 创建告警接收器。
     * Create alarm receiver.
     * @param capacity 队列容量 / queue capacity
     * @param rejectPolicy 拒绝策略 / rejection policy
     */
    public AlarmReceiver(int capacity, RejectPolicy rejectPolicy) {
        this.capacity = capacity;
        this.rejectPolicy = rejectPolicy;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * 接收告警。
     * Receive alarm.
     * @param event 告警事件 / alarm event
     * @return true 表示成功接收，false 表示被拒绝 / true if successfully received, false if rejected
     */
    public boolean receive(AlarmEvent event) {
        if (!running) {
            log.warn("Receiver is not running, rejecting event: {}", event.getId());
            return false;
        }

        lastReceiveTime = System.currentTimeMillis();

        switch (rejectPolicy) {
            case DROP:
                return handleDrop(event);
            case DROP_OLDEST:
                return handleDropOldest(event);
            case BLOCK:
                return handleBlock(event);
            case CALLER_RUNS:
                return handleCallerRuns(event);
            default:
                return handleDrop(event);
        }
    }

    /**
     * DROP 策略 - 丢弃新数据。
     * DROP policy - drop new data.
     */
    private boolean handleDrop(AlarmEvent event) {
        if (queue.offer(event)) {
            receiveCount.incrementAndGet();
            return true;
        }
        dropCount.incrementAndGet();
        dropLogger.recordDrop(event);
        return false;
    }

    /**
     * DROP_OLDEST 策略 - 丢弃最旧数据。
     * DROP_OLDEST policy - drop oldest data.
     */
    private boolean handleDropOldest(AlarmEvent event) {
        // 尝试移除最旧元素 / Try to remove oldest element
        AlarmEvent oldest = queue.poll();
        if (oldest != null) {
            dropCount.incrementAndGet();
            dropLogger.recordDrop(oldest);
        }
        if (queue.offer(event)) {
            receiveCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * BLOCK 策略 - 阻塞等待。
     * BLOCK policy - block and wait.
     */
    private boolean handleBlock(AlarmEvent event) {
        try {
            if (queue.offer(event, 5, TimeUnit.SECONDS)) {
                receiveCount.incrementAndGet();
                return true;
            }
            dropCount.incrementAndGet();
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * CALLER_RUNS 策略 - 调用者线程处理。
     * CALLER_RUNS policy - caller runs.
     */
    private boolean handleCallerRuns(AlarmEvent event) {
        if (queue.offer(event)) {
            receiveCount.incrementAndGet();
            return true;
        }
        // 队列满时返回 false，调用者需自行处理
        // Return false when queue is full, caller needs to handle it
        dropCount.incrementAndGet();
        return false;
    }

    /**
     * 获取告警（消费者使用）。
     * Poll alarm (for consumer).
     */
    public AlarmEvent poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
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
     * 停止接收。
     * Stop receiving.
     */
    public void stop() {
        log.info("AlarmReceiver stopping...");
        this.running = false;
    }

    /**
     * 是否正在运行。
     * Whether running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取接收计数。
     * Get receive count.
     */
    public long getReceiveCount() {
        return receiveCount.get();
    }

    /**
     * 获取丢弃计数。
     * Get drop count.
     */
    public long getDropCount() {
        return dropCount.get();
    }

    /**
     * 获取队列利用率（百分比）。
     * Get queue utilization (percentage).
     */
    public double getQueueUtilization() {
        return (double) queue.size() / capacity * 100;
    }

    /**
     * 获取最后接收时间。
     * Get last receive time.
     */
    public long getLastReceiveTime() {
        return lastReceiveTime;
    }

    /**
     * 获取丢弃日志器。
     * Get drop logger.
     */
    public DropLogger getDropLogger() {
        return dropLogger;
    }
}
