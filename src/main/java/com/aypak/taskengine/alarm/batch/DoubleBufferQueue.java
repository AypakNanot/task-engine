package com.aypak.taskengine.alarm.batch;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 双缓冲队列。
 * 支持高并发写入和批量刷写的双缓冲机制。
 * Double buffer queue.
 * Dual buffering mechanism supporting high-concurrency writes and batch flushing.
 */
public class DoubleBufferQueue {

    private static final Logger log = LoggerFactory.getLogger(DoubleBufferQueue.class);

    /** 活跃缓冲区 - 接收新数据 / Active buffer - receives new data */
    private List<AlarmEvent> activeBuffer;

    /** 备用缓冲区 - 等待刷写 / Standby buffer - waiting to flush */
    private List<AlarmEvent> standbyBuffer;

    /** 缓冲区容量 / Buffer capacity */
    private final int bufferSize;

    /** 写入锁 / Write lock */
    private final ReentrantLock writeLock = new ReentrantLock();

    /** 切换锁 / Switch lock */
    private final ReentrantLock switchLock = new ReentrantLock();

    /** 队列满时的等待队列 / Wait queue when queue is full */
    private final BlockingQueue<Void> spaceAvailable = new ArrayBlockingQueue<>(1);

    /** 刷写次数 / Flush count */
    private final AtomicLong flushCount = new AtomicLong(0);

    /** 刷写的事件总数 / Total flushed events */
    private final AtomicLong totalFlushedEvents = new AtomicLong(0);

    /** 最后一次刷写时间 / Last flush time */
    private volatile long lastFlushTime = System.currentTimeMillis();

    /** 是否已关闭 / Whether closed */
    private volatile boolean closed = false;

    public DoubleBufferQueue(int bufferSize) {
        this.bufferSize = bufferSize;
        this.activeBuffer = new ArrayList<>(bufferSize);
        this.standbyBuffer = new ArrayList<>(bufferSize);
    }

    /**
     * 提交事件到队列。
     * Submit event to queue.
     * @param event 告警事件 / alarm event
     * @return true 表示成功提交，false 表示队列已满 / true if successfully submitted, false if queue is full
     */
    public boolean offer(AlarmEvent event) {
        if (closed) {
            return false;
        }

        writeLock.lock();
        try {
            if (activeBuffer.size() >= bufferSize) {
                // 缓冲区已满 / Buffer is full
                return false;
            }
            activeBuffer.add(event);
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 尝试提交事件，带超时。
     * Try to submit event with timeout.
     */
    public boolean offer(AlarmEvent event, long timeout, TimeUnit unit) throws InterruptedException {
        if (closed) {
            return false;
        }

        long deadline = System.nanoTime() + unit.toNanos(timeout);

        while (true) {
            writeLock.lock();
            try {
                if (activeBuffer.size() >= bufferSize) {
                    // 缓冲区已满，等待 / Buffer is full, waiting
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return false; // 超时 / Timeout
                    }
                } else {
                    activeBuffer.add(event);
                    return true;
                }
            } finally {
                writeLock.unlock();
            }

            // 等待空间可用 / Wait for space available
            Thread.sleep(10);
        }
    }

    /**
     * 切换缓冲区并返回待刷写的数据。
     * Switch buffer and return data to flush.
     * @return 待刷写的事件列表，如果为空则表示没有数据 / Events to flush, null if no data
     */
    public List<AlarmEvent> switchAndGet() {
        switchLock.lock();
        try {
            writeLock.lock();
            try {
                if (activeBuffer.isEmpty()) {
                    return null;
                }

                // 交换缓冲区 / Switch buffers
                List<AlarmEvent> toFlush = activeBuffer;
                activeBuffer = standbyBuffer;
                standbyBuffer = toFlush;

                return toFlush;
            } finally {
                writeLock.unlock();
            }
        } finally {
            switchLock.unlock();
        }
    }

    /**
     * 标记刷写完成。
     * Mark as flushed.
     * @param eventCount 刷写的事件数量 / Number of flushed events
     */
    public void markFlushed(int eventCount) {
        flushCount.incrementAndGet();
        totalFlushedEvents.addAndGet(eventCount);
        lastFlushTime = System.currentTimeMillis();

        // 清空备用缓冲区 / Clear standby buffer
        if (standbyBuffer != null) {
            standbyBuffer.clear();
        }
    }

    /**
     * 获取活跃缓冲区大小。
     * Get active buffer size.
     */
    public int getActiveSize() {
        writeLock.lock();
        try {
            return activeBuffer.size();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 获取总待处理事件数。
     * Get total pending events.
     */
    public int getTotalSize() {
        writeLock.lock();
        try {
            return activeBuffer.size() + standbyBuffer.size();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 获取缓冲区容量。
     * Get buffer capacity.
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * 获取刷写次数。
     * Get flush count.
     */
    public long getFlushCount() {
        return flushCount.get();
    }

    /**
     * 获取总刷写事件数。
     * Get total flushed events.
     */
    public long getTotalFlushedEvents() {
        return totalFlushedEvents.get();
    }

    /**
     * 获取最后一次刷写时间。
     * Get last flush time.
     */
    public long getLastFlushTime() {
        return lastFlushTime;
    }

    /**
     * 关闭队列。
     * Close queue.
     */
    public void close() {
        closed = true;
    }

    /**
     * 是否已关闭。
     * Whether closed.
     */
    public boolean isClosed() {
        return closed;
    }
}
