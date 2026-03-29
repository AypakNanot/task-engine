package com.aypak.taskengine.alarm.batch;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * 批量数据库执行器
 * 支持双触发机制（数量或时间）的批量插入
 */
public class BatchDBExecutor {

    private static final Logger log = LoggerFactory.getLogger(BatchDBExecutor.class);

    /** 默认批量大小 */
    private static final int DEFAULT_BATCH_SIZE = 1000;

    /** 默认超时时间（毫秒）*/
    private static final long DEFAULT_BATCH_TIMEOUT_MS = 500;

    /** 数据源 */
    private final DataSource dataSource;

    /** SQL 插入语句 */
    private final String insertSql;

    /** 批量大小 */
    private final int batchSize;

    /** 批量超时（毫秒）*/
    private final long batchTimeoutMs;

    /** 双缓冲队列 */
    private final DoubleBufferQueue queue;

    /** 定时调度器 */
    private final ScheduledExecutorService scheduler;

    /** 运行标志 */
    private volatile boolean running = true;

    /** 插入次数 */
    private final LongAdder insertCount = new LongAdder();

    /** 插入记录总数 */
    private final LongAdder totalInsertedRecords = new LongAdder();

    /** 失败次数 */
    private final LongAdder failureCount = new LongAdder();

    /** 总延迟（毫秒）*/
    private final AtomicLong totalLatency = new AtomicLong(0);

    /** 最后一次刷写时间 */
    private volatile long lastFlushTime = System.currentTimeMillis();

    /** 结果回调 */
    private Consumer<BatchResult> resultCallback;

    /**
     * 创建批量数据库执行器
     * @param dataSource 数据源
     * @param insertSql 插入 SQL 语句
     */
    public BatchDBExecutor(DataSource dataSource, String insertSql) {
        this(dataSource, insertSql, DEFAULT_BATCH_SIZE, DEFAULT_BATCH_TIMEOUT_MS);
    }

    /**
     * 创建批量数据库执行器
     * @param dataSource 数据源
     * @param insertSql 插入 SQL 语句
     * @param batchSize 批量大小
     * @param batchTimeoutMs 批量超时（毫秒）
     */
    public BatchDBExecutor(DataSource dataSource, String insertSql,
                          int batchSize, long batchTimeoutMs) {
        this.dataSource = dataSource;
        this.insertSql = insertSql;
        this.batchSize = batchSize;
        this.batchTimeoutMs = batchTimeoutMs;
        this.queue = new DoubleBufferQueue(batchSize * 2);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "BatchDBExecutor-Scheduler"));

        // 启动定时刷写任务
        startScheduler();
    }

    /**
     * 启动定时调度器
     */
    private void startScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                try {
                    flushIfNeeded();
                } catch (Exception e) {
                    log.error("Scheduled flush failed", e);
                }
            }
        }, batchTimeoutMs, batchTimeoutMs / 2, TimeUnit.MILLISECONDS);
    }

    /**
     * 检查并执行刷写
     */
    private void flushIfNeeded() {
        long now = System.currentTimeMillis();
        long sinceLastFlush = now - lastFlushTime;

        // 检查是否需要刷写（超时或数量达到阈值）
        if (sinceLastFlush >= batchTimeoutMs || queue.getActiveSize() >= batchSize) {
            flush();
        }
    }

    /**
     * 提交事件到批量队列
     */
    public void submit(AlarmEvent event) {
        if (!running) {
            log.warn("BatchDBExecutor is not running, rejecting event: {}", event.getId());
            return;
        }

        try {
            boolean offered = queue.offer(event, 5, TimeUnit.SECONDS);
            if (!offered) {
                log.warn("Failed to submit event {} to batch queue", event.getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while submitting event to batch queue", e);
        }

        // 检查是否需要立即刷写
        if (queue.getActiveSize() >= batchSize) {
            flush();
        }
    }

    /**
     * 执行批量刷写
     */
    public void flush() {
        List<AlarmEvent> events = queue.switchAndGet();
        if (events == null || events.isEmpty()) {
            return;
        }

        // 创建防御性副本，避免并发修改
        List<AlarmEvent> eventsCopy = new ArrayList<>(events);

        long startTime = System.currentTimeMillis();
        BatchResult result = BatchResult.success(0, 0);

        try {
            result = executeBatch(eventsCopy);
            queue.markFlushed(eventsCopy.size());
            lastFlushTime = System.currentTimeMillis();

            // 更新统计
            insertCount.increment();
            totalInsertedRecords.add(events.size());
            long latency = System.currentTimeMillis() - startTime;
            totalLatency.addAndGet(latency);

            if (log.isDebugEnabled()) {
                log.debug("Batch flush completed: {} events in {}ms", eventsCopy.size(), latency);
            }

        } catch (SQLException e) {
            failureCount.increment();
            log.error("Batch insert failed", e);
            result = BatchResult.failure(eventsCopy.size(), e);

            // 重试逻辑：降级为单条插入
            retrySingleInserts(eventsCopy);
        } finally {
            // 通知回调
            if (resultCallback != null) {
                resultCallback.accept(result);
            }
        }
    }

    /**
     * 执行批量插入
     */
    private BatchResult executeBatch(List<AlarmEvent> events) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {

            for (AlarmEvent event : events) {
                ps.setString(1, event.getDeviceId());
                ps.setString(2, event.getAlarmType());
                ps.setTimestamp(3, event.getOccurTime() != null ?
                        Timestamp.valueOf(event.getOccurTime()) : null);
                ps.setString(4, event.getSeverity() != null ? event.getSeverity().name() : null);
                ps.setString(5, event.getSourceSystem());
                ps.setString(6, event.getLocation());
                ps.setString(7, event.getDescription());
                ps.setTimestamp(8, new Timestamp(event.getSubmitTime()));
                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            return BatchResult.success(events.size(), results.length);
        }
    }

    /**
     * 重试单条插入
     */
    private void retrySingleInserts(List<AlarmEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        log.info("Retrying {} events as single inserts", events.size());

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {

            for (AlarmEvent event : events) {
                try {
                    ps.setString(1, event.getDeviceId());
                    ps.setString(2, event.getAlarmType());
                    ps.setTimestamp(3, event.getOccurTime() != null ?
                            Timestamp.valueOf(event.getOccurTime()) : null);
                    ps.setString(4, event.getSeverity() != null ? event.getSeverity().name() : null);
                    ps.setString(5, event.getSourceSystem());
                    ps.setString(6, event.getLocation());
                    ps.setString(7, event.getDescription());
                    ps.setTimestamp(8, new Timestamp(event.getSubmitTime()));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    log.error("Single insert failed for event {}", event.getId(), e);
                    event.setStatus(AlarmEvent.ProcessingStatus.FAILED);
                    event.setErrorMessage("DB insert failed: " + e.getMessage());
                }
            }

        } catch (SQLException e) {
            log.error("Single insert connection failed", e);
        }
    }

    /**
     * 获取插入次数
     */
    public long getInsertCount() {
        return insertCount.sum();
    }

    /**
     * 获取总插入记录数
     */
    public long getTotalInsertedRecords() {
        return totalInsertedRecords.sum();
    }

    /**
     * 获取失败次数
     */
    public long getFailureCount() {
        return failureCount.sum();
    }

    /**
     * 获取平均延迟（毫秒）
     */
    public double getAverageLatency() {
        long count = insertCount.sum();
        return count > 0 ? (double) totalLatency.get() / count : 0;
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return queue.getTotalSize();
    }

    /**
     * 设置结果回调
     */
    public void setResultCallback(Consumer<BatchResult> callback) {
        this.resultCallback = callback;
    }

    /**
     * 关闭执行器
     */
    public void shutdown() {
        log.info("Shutting down BatchDBExecutor...");
        running = false;
        queue.close();

        // 刷写剩余数据
        flush();

        // 关闭调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("BatchDBExecutor shut down completed");
    }

    /**
     * 批量执行结果
     */
    public static class BatchResult {
        public final boolean success;
        public final int expectedCount;
        public final int actualCount;
        public final Exception error;

        private BatchResult(boolean success, int expectedCount, int actualCount, Exception error) {
            this.success = success;
            this.expectedCount = expectedCount;
            this.actualCount = actualCount;
            this.error = error;
        }

        public static BatchResult success(int expected, int actual) {
            return new BatchResult(true, expected, actual, null);
        }

        public static BatchResult failure(int expected, Exception error) {
            return new BatchResult(false, expected, 0, error);
        }
    }
}
