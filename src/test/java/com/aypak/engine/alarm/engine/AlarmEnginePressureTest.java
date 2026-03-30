package com.aypak.engine.alarm.engine;

import com.aypak.engine.alarm.core.AlarmEvent;
import com.aypak.engine.alarm.core.RejectPolicy;
import com.aypak.engine.alarm.monitor.AlarmMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AlarmEngine 压力测试
 * 模拟 1000 个设备持续发送告警 15 分钟
 */
public class AlarmEnginePressureTest {

    private static final Logger log = LoggerFactory.getLogger(AlarmEnginePressureTest.class);

    /** 测试持续时间（分钟）*/
    private static final int DURATION_MINUTES = 15;

    /** 设备数量 */
    private static final int DEVICE_COUNT = 1000;

    /** 告警引擎 */
    private AlarmEngineImpl engine;

    /** 运行标志 */
    private AtomicBoolean running;

    /** 线程池 */
    private ExecutorService executor;

    /** 统计信息 */
    private PressureTestStats stats;

    @BeforeEach
    void setUp() {
        log.info("===========================================");
        log.info("AlarmEngine Pressure Test Setup");
        log.info("===========================================");
        log.info("Device Count: {}", DEVICE_COUNT);
        log.info("Duration: {} minutes", DURATION_MINUTES);
        log.info("===========================================");

        // 使用简单数据源，不实际入库
        engine = new AlarmEngineImpl(new SimpleTestDataSource(), "INSERT INTO alarm_event VALUES (?,?,?,?,?,?,?,?)",
                16, 5000, RejectPolicy.DROP);
        engine.start();
        running = new AtomicBoolean(true);
        executor = Executors.newFixedThreadPool(100);
        stats = new PressureTestStats();
    }

    @AfterEach
    void tearDown() {
        running.set(false);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (engine != null) {
            engine.shutdown();
        }
    }

    /**
     * 压力测试主方法
     * 模拟 1000 个设备持续 15 分钟发送告警
     */
    @Test
    void testPressureWith1000Devices() throws InterruptedException {
        log.info("Starting pressure test with {} devices for {} minutes...", DEVICE_COUNT, DURATION_MINUTES);

        CountDownLatch startLatch = new CountDownLatch(DEVICE_COUNT);
        long startTime = System.currentTimeMillis();

        // 启动 1000 个设备模拟线程
        for (int i = 0; i < DEVICE_COUNT; i++) {
            final int deviceIndex = i;
            executor.submit(() -> {
                String deviceId = "device-" + String.format("%04d", deviceIndex);
                startLatch.countDown();

                int alarmCounter = 0;
                while (running.get()) {
                    try {
                        // 每个设备每秒发送 1-10 个告警（模拟不同负载）
                        int alarmsPerSecond = (deviceIndex % 10) + 1;
                        for (int j = 0; j < alarmsPerSecond; j++) {
                            AlarmEvent event = createAlarmEvent(deviceId, alarmCounter++);
                            boolean accepted = engine.submit(event);
                            if (accepted) {
                                stats.recordSubmitted();
                            } else {
                                stats.recordRejected();
                            }
                        }

                        // 等待到下一秒
                        Thread.sleep(1000);

                        // 每秒记录一次统计
                        stats.recordSecond();

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Device {} error", deviceId, e);
                    }
                }
            });
        }

        // 等待所有设备启动
        startLatch.await();
        log.info("All {} devices started", DEVICE_COUNT);

        // 运行指定时长
        long elapsedSeconds = 0;
        while (elapsedSeconds < DURATION_MINUTES * 60) {
            Thread.sleep(10000); // 每 10 秒输出一次状态
            elapsedSeconds += 10;

            AlarmMetrics.MetricsSnapshot snapshot = engine.getMetrics().getSnapshot();
            long submittedPerSecond = stats.getSubmittedLast10Seconds() / 10;
            long rejectedPerSecond = stats.getRejectedLast10Seconds() / 10;

            log.info("[{}s] QPS: {} (accepted) + {} (rejected) | Engine QPS: {} | Queue: R[{}] W[{}] | Success: {}% | RT: {}ms",
                    elapsedSeconds, submittedPerSecond, rejectedPerSecond, snapshot.qps,
                    snapshot.receiverQueueDepth, snapshot.totalWorkerQueueDepth,
                    String.format("%.2f", snapshot.successRate), snapshot.avgProcessingRT);
        }

        // 停止测试
        running.set(false);
        Thread.sleep(2000); // 等待剩余任务完成

        // 输出最终统计
        printFinalStats(startTime);
    }

    /**
     * 快速压力测试（1 分钟版本，用于调试）
     */
    @Test
    void testPressureQuick() throws InterruptedException {
        log.info("Starting QUICK pressure test (1 minute)...");

        CountDownLatch startLatch = new CountDownLatch(DEVICE_COUNT);
        long startTime = System.currentTimeMillis();

        // 启动 1000 个设备模拟线程
        for (int i = 0; i < DEVICE_COUNT; i++) {
            final int deviceIndex = i;
            executor.submit(() -> {
                String deviceId = "device-" + String.format("%04d", deviceIndex);
                startLatch.countDown();

                int alarmCounter = 0;
                while (running.get()) {
                    try {
                        // 每个设备每秒发送 1-10 个告警
                        int alarmsPerSecond = (deviceIndex % 10) + 1;
                        for (int j = 0; j < alarmsPerSecond; j++) {
                            AlarmEvent event = createAlarmEvent(deviceId, alarmCounter++);
                            boolean accepted = engine.submit(event);
                            if (accepted) {
                                stats.recordSubmitted();
                            } else {
                                stats.recordRejected();
                            }
                        }
                        Thread.sleep(1000);
                        stats.recordSecond();

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Device {} error", deviceId, e);
                    }
                }
            });
        }

        startLatch.await();
        log.info("All {} devices started", DEVICE_COUNT);

        // 运行 1 分钟
        for (int i = 0; i < 6; i++) {
            Thread.sleep(10000);
            AlarmMetrics.MetricsSnapshot snapshot = engine.getMetrics().getSnapshot();
            log.info("[{}s] Submitted: {} | Rejected: {} | Engine QPS: {} | Queue: {} | RT: {}ms",
                    (i + 1) * 10, stats.getSubmittedTotal(), stats.getRejectedTotal(),
                    snapshot.qps, snapshot.receiverQueueDepth + snapshot.totalWorkerQueueDepth,
                    snapshot.avgProcessingRT);
        }

        running.set(false);
        Thread.sleep(2000);
        printFinalStats(startTime);
    }

    /**
     * 高负载压力测试
     * 使用更多线程和更快的发送速率来测试引擎最大 QPS
     */
    @Test
    void testHighLoadQPS() throws InterruptedException {
        log.info("Starting HIGH LOAD QPS test (30 seconds)...");

        CountDownLatch startLatch = new CountDownLatch(DEVICE_COUNT);
        long startTime = System.currentTimeMillis();

        // 使用更多线程来模拟设备
        ExecutorService highLoadExecutor = Executors.newFixedThreadPool(500);

        try {
            // 启动 1000 个设备模拟线程
            for (int i = 0; i < DEVICE_COUNT; i++) {
                final int deviceIndex = i;
                highLoadExecutor.submit(() -> {
                    String deviceId = "device-" + String.format("%04d", deviceIndex);
                    startLatch.countDown();

                    int alarmCounter = 0;
                    while (running.get()) {
                        try {
                            // 每个设备快速发送告警，减少睡眠
                            int batchSize = (deviceIndex % 10) + 1;
                            for (int j = 0; j < batchSize; j++) {
                                AlarmEvent event = createAlarmEvent(deviceId, alarmCounter++);
                                boolean accepted = engine.submit(event);
                                if (accepted) {
                                    stats.recordSubmitted();
                                } else {
                                    stats.recordRejected();
                                }
                            }
                            // 缩短睡眠时间到 100ms，提高发送频率
                            Thread.sleep(100);
                            stats.recordSecond();

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            log.error("Device {} error", deviceId, e);
                        }
                    }
                });
            }

            startLatch.await();
            log.info("All {} devices started", DEVICE_COUNT);

            // 运行 30 秒，每 5 秒输出一次
            for (int i = 0; i < 6; i++) {
                Thread.sleep(5000);
                AlarmMetrics.MetricsSnapshot snapshot = engine.getMetrics().getSnapshot();
                long elapsed = System.currentTimeMillis() - startTime;
                long actualQps = stats.getSubmittedTotal() * 1000 / elapsed;
                log.info("[{}s] Submitted: {} | Rejected: {} | Actual QPS: {} | Engine QPS: {} | Success: {}",
                        (i + 1) * 5, stats.getSubmittedTotal(), stats.getRejectedTotal(),
                        actualQps, snapshot.qps, snapshot.successCount);
            }

            running.set(false);
            Thread.sleep(2000);

            // 打印最终统计
            long totalDuration = System.currentTimeMillis() - startTime;
            long finalQps = stats.getSubmittedTotal() * 1000 / totalDuration;
            log.info("===========================================");
            log.info("HIGH LOAD QPS Test Complete");
            log.info("===========================================");
            log.info("Duration: {} ms", totalDuration);
            log.info("Total Submitted: {}", stats.getSubmittedTotal());
            log.info("Total Rejected: {}", stats.getRejectedTotal());
            log.info("Average QPS: {}", finalQps);
            log.info("Success Count: {}", engine.getMetrics().getSnapshot().successCount);
            log.info("===========================================");

        } finally {
            highLoadExecutor.shutdown();
            if (!highLoadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                highLoadExecutor.shutdownNow();
            }
        }
    }

    /**
     * 创建告警事件
     */
    private AlarmEvent createAlarmEvent(String deviceId, int counter) {
        return AlarmEvent.builder()
                .id("alarm-" + deviceId + "-" + counter)
                .deviceId(deviceId)
                .alarmType("ALARM_TYPE_" + (counter % 10))
                .occurTime(LocalDateTime.now())
                .severity(AlarmEvent.Severity.INFO)
                .sourceSystem("PressureTest")
                .location("TestLocation")
                .description("Pressure test alarm #" + counter)
                .build();
    }

    /**
     * 打印最终统计信息
     */
    private void printFinalStats(long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        AlarmMetrics metrics = engine.getMetrics();
        AlarmMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();

        log.info("");
        log.info("===========================================");
        log.info("PRESSURE TEST RESULTS");
        log.info("===========================================");
        log.info("Duration: {} minutes ({} seconds)", duration / 60000, duration / 1000);
        log.info("Device Count: {}", DEVICE_COUNT);
        log.info("");
        log.info("Submission Stats:");
        log.info("  Total Submitted: {}", stats.getSubmittedTotal());
        log.info("  Total Rejected:  {}", stats.getRejectedTotal());
        log.info("  Average QPS:     {}", stats.getSubmittedTotal() / (duration / 1000));
        log.info("");
        log.info("Engine Metrics:");
        log.info("  Incoming Count:  {}", snapshot.incomingCount);
        log.info("  Success Count:   {}", snapshot.successCount);
        log.info("  Failure Count:   {}", snapshot.failureCount);
        log.info("  Dropped Count:   {}", snapshot.droppedCount);
        log.info("  Success Rate:    {}%", String.format("%.2f", snapshot.successRate));
        log.info("  Avg RT:          {} ms", snapshot.avgProcessingRT);
        log.info("");
        log.info("Queue Status:");
        log.info("  Receiver Queue:  {}", snapshot.receiverQueueDepth);
        log.info("  Worker Queue:    {}", snapshot.totalWorkerQueueDepth);
        log.info("");
        log.info("Performance Summary:");
        log.info("  Peak QPS:        ~{}", stats.getSubmittedTotal() / (duration / 1000));
        log.info("  Target QPS:      10,000+");
        log.info("===========================================");
    }

    /**
     * 压力测试统计
     */
    static class PressureTestStats {
        private final AtomicLong submittedTotal = new AtomicLong(0);
        private final AtomicLong rejectedTotal = new AtomicLong(0);
        private final AtomicLong submittedLast10Seconds = new AtomicLong(0);
        private final AtomicLong rejectedLast10Seconds = new AtomicLong(0);
        private volatile long lastResetTime = System.currentTimeMillis();

        public void recordSubmitted() {
            submittedTotal.incrementAndGet();
            submittedLast10Seconds.incrementAndGet();
        }

        public void recordRejected() {
            rejectedTotal.incrementAndGet();
            rejectedLast10Seconds.incrementAndGet();
        }

        public void recordSecond() {
            long now = System.currentTimeMillis();
            if (now - lastResetTime >= 10000) {
                submittedLast10Seconds.set(0);
                rejectedLast10Seconds.set(0);
                lastResetTime = now;
            }
        }

        public long getSubmittedTotal() {
            return submittedTotal.get();
        }

        public long getRejectedTotal() {
            return rejectedTotal.get();
        }

        public long getSubmittedLast10Seconds() {
            return submittedLast10Seconds.get();
        }

        public long getRejectedLast10Seconds() {
            return rejectedLast10Seconds.get();
        }
    }

    /**
     * 简单测试数据源（支持模拟连接）
     */
    static class SimpleTestDataSource implements javax.sql.DataSource {
        @Override public java.sql.Connection getConnection() { return new MockConnection(); }
        @Override public java.sql.Connection getConnection(String username, String password) { return new MockConnection(); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public void setLoginTimeout(int seconds) {}
        @Override public java.util.logging.Logger getParentLogger() { return null; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    /**
     * 模拟数据库连接
     */
    static class MockConnection implements java.sql.Connection {
        @Override public java.sql.PreparedStatement prepareStatement(String sql) { return new MockPreparedStatement(sql); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) { return new MockPreparedStatement(sql); }
        @Override public java.sql.Statement createStatement() { throw new UnsupportedOperationException(); }
        @Override public java.sql.CallableStatement prepareCall(String sql) { throw new UnsupportedOperationException(); }
        @Override public String nativeSQL(String sql) { throw new UnsupportedOperationException(); }
        @Override public void commit() {}
        @Override public void rollback() {}
        @Override public void close() {}
        @Override public boolean isClosed() { return false; }
        @Override public boolean getAutoCommit() { return true; }
        @Override public void setAutoCommit(boolean autoCommit) {}
        @Override public boolean isReadOnly() { return false; }
        @Override public void setReadOnly(boolean readOnly) {}
        @Override public String getCatalog() { return null; }
        @Override public void setCatalog(String catalog) {}
        @Override public int getTransactionIsolation() { return 0; }
        @Override public void setTransactionIsolation(int level) {}
        @Override public java.sql.DatabaseMetaData getMetaData() { throw new UnsupportedOperationException(); }
        @Override public void setHoldability(int holdability) {}
        @Override public int getHoldability() { return 0; }
        @Override public java.sql.Savepoint setSavepoint() { throw new UnsupportedOperationException(); }
        @Override public java.sql.Savepoint setSavepoint(String name) { throw new UnsupportedOperationException(); }
        @Override public void rollback(java.sql.Savepoint savepoint) {}
        @Override public void releaseSavepoint(java.sql.Savepoint savepoint) { throw new UnsupportedOperationException(); }
        @Override public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) { return new MockPreparedStatement(sql); }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) { throw new UnsupportedOperationException(); }
        @Override public java.util.Map<String, Class<?>> getTypeMap() { throw new UnsupportedOperationException(); }
        @Override public void setTypeMap(java.util.Map<String, Class<?>> map) {}
        @Override public int getNetworkTimeout() { return 0; }
        @Override public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) {}
        @Override public String getSchema() { return null; }
        @Override public void setSchema(String schema) {}
        @Override public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { return new MockPreparedStatement(sql); }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) { throw new UnsupportedOperationException(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames) { return new MockPreparedStatement(sql); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes) { return new MockPreparedStatement(sql); }
        @Override public java.sql.Clob createClob() { throw new UnsupportedOperationException(); }
        @Override public java.sql.Blob createBlob() { throw new UnsupportedOperationException(); }
        @Override public java.sql.NClob createNClob() { throw new UnsupportedOperationException(); }
        @Override public java.sql.SQLXML createSQLXML() { throw new UnsupportedOperationException(); }
        @Override public boolean isValid(int timeout) { return true; }
        @Override public void abort(java.util.concurrent.Executor executor) {}
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) { throw new UnsupportedOperationException(); }
        @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) { throw new UnsupportedOperationException(); }
        @Override public java.util.Properties getClientInfo() throws java.sql.SQLException { return new java.util.Properties(); }
        @Override public String getClientInfo(String name) throws java.sql.SQLException { return null; }
        @Override public void setClientInfo(String name, String value) {}
        @Override public void setClientInfo(java.util.Properties properties) {}
    }

    /**
     * 模拟 PreparedStatement
     */
    static class MockPreparedStatement implements java.sql.PreparedStatement {
        private final String sql;
        MockPreparedStatement(String sql) { this.sql = sql; }
        @Override public int[] executeBatch() { try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return new int[0]; }
        @Override public boolean execute(String sql) { return false; }
        @Override public boolean execute() { return false; }
        @Override public int executeUpdate() { return 1; }
        @Override public int executeUpdate(String sql) { return 1; }
        @Override public boolean execute(String sql, String[] columnNames) { return false; }
        @Override public boolean execute(String sql, int[] columnIndexes) { return false; }
        @Override public boolean execute(String sql, int autoGeneratedKeys) { return false; }
        @Override public int executeUpdate(String sql, String[] columnNames) { return 1; }
        @Override public int executeUpdate(String sql, int[] columnIndexes) { return 1; }
        @Override public int executeUpdate(String sql, int autoGeneratedKeys) { return 1; }
        @Override public long executeLargeUpdate() { return 1; }
        @Override public long executeLargeUpdate(String sql, String[] columnNames) { return 1; }
        @Override public long executeLargeUpdate(String sql, int[] columnIndexes) { return 1; }
        @Override public long executeLargeUpdate(String sql, int autoGeneratedKeys) { return 1; }
        @Override public void close() {}
        @Override public void setString(int parameterIndex, String x) {}
        @Override public void setTimestamp(int parameterIndex, java.sql.Timestamp x) {}
        @Override public void setInt(int parameterIndex, int x) {}
        @Override public void setLong(int parameterIndex, long x) {}
        @Override public void setDouble(int parameterIndex, double x) {}
        @Override public void setFloat(int parameterIndex, float x) {}
        @Override public void setBoolean(int parameterIndex, boolean x) {}
        @Override public void setBytes(int parameterIndex, byte[] x) {}
        @Override public void setDate(int parameterIndex, java.sql.Date x) {}
        @Override public void setTime(int parameterIndex, java.sql.Time x) {}
        @Override public void setObject(int parameterIndex, Object x) {}
        @Override public void addBatch() {}
        @Override public void addBatch(String sql) {}
        @Override public void clearBatch() {}
        @Override public java.sql.ResultSet executeQuery() { throw new UnsupportedOperationException(); }
        @Override public java.sql.ResultSet executeQuery(String sql) { throw new UnsupportedOperationException(); }
        @Override public java.sql.Connection getConnection() { return new MockConnection(); }
        // 省略其他方法，使用默认抛出异常实现
        @Override public java.sql.ResultSetMetaData getMetaData() { throw new UnsupportedOperationException(); }
        @Override public java.sql.ParameterMetaData getParameterMetaData() { throw new UnsupportedOperationException(); }
        @Override public void setArray(int parameterIndex, java.sql.Array x) {}
        @Override public void setBigDecimal(int parameterIndex, java.math.BigDecimal x) {}
        @Override public void setRef(int parameterIndex, java.sql.Ref x) {}
        @Override public void setURL(int parameterIndex, java.net.URL x) {}
        @Override public void setRowId(int parameterIndex, java.sql.RowId x) {}
        @Override public void setNString(int parameterIndex, String value) {}
        @Override public void setNCharacterStream(int parameterIndex, java.io.Reader value, long length) {}
        @Override public void setNClob(int parameterIndex, java.sql.NClob value) {}
        @Override public void setClob(int parameterIndex, java.io.Reader reader, long length) {}
        @Override public void setBlob(int parameterIndex, java.sql.Blob x) {}
        @Override public void setBlob(int parameterIndex, java.io.InputStream inputStream, long length) {}
        @Override public void setBlob(int parameterIndex, java.io.InputStream inputStream) {}
        @Override public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) {}
        @Override public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) {}
        @Override public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) {}
        @Override public void setObject(int parameterIndex, Object x, int targetSqlType) {}
        @Override public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) {}
        @Override public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) {}
        @Override public void setDate(int parameterIndex, java.sql.Date x, java.util.Calendar cal) {}
        @Override public void setTime(int parameterIndex, java.sql.Time x, java.util.Calendar cal) {}
        @Override public void setTimestamp(int parameterIndex, java.sql.Timestamp x, java.util.Calendar cal) {}
        @Override public void setNull(int parameterIndex, int sqlType) {}
        @Override public void setNull(int parameterIndex, int sqlType, String typeName) {}
        @Override public void setShort(int parameterIndex, short x) {}
        @Override public void setByte(int parameterIndex, byte x) {}
        @Override public void setBinaryStream(int parameterIndex, java.io.InputStream x) {}
        @Override public void setBinaryStream(int parameterIndex, java.io.InputStream x, long length) {}
        @Override public void setAsciiStream(int parameterIndex, java.io.InputStream x) {}
        @Override public void setAsciiStream(int parameterIndex, java.io.InputStream x, long length) {}
        @Override public void setCharacterStream(int parameterIndex, java.io.Reader reader) {}
        @Override public void setCharacterStream(int parameterIndex, java.io.Reader reader, long length) {}
        @Override public void setNCharacterStream(int parameterIndex, java.io.Reader value) {}
        @Override public boolean isClosed() { return false; }
        @Override public void setPoolable(boolean poolable) {}
        @Override public boolean isPoolable() { return false; }
        @Override public void closeOnCompletion() {}
        @Override public boolean isCloseOnCompletion() { return false; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public int getMaxFieldSize() { return 0; }
        @Override public int getMaxRows() { return 0; }
        @Override public long getLargeMaxRows() { return 0; }
        @Override public int getFetchDirection() { return 0; }
        @Override public int getFetchSize() { return 0; }
        @Override public int getResultSetConcurrency() { return 0; }
        @Override public int getResultSetType() { return 0; }
        @Override public int getResultSetHoldability() { return 0; }
        @Override public int getQueryTimeout() { return 0; }
        @Override public void setQueryTimeout(int seconds) {}
        @Override public void setMaxFieldSize(int max) {}
        @Override public void setMaxRows(int max) {}
        @Override public void setLargeMaxRows(long max) {}
        @Override public void setFetchDirection(int direction) {}
        @Override public void setFetchSize(int rows) {}
        @Override public java.sql.SQLWarning getWarnings() { return null; }
        @Override public void clearWarnings() {}
        @Override public void cancel() {}
        @Override public void setCursorName(String name) {}
        @Override public boolean getMoreResults() { return false; }
        @Override public boolean getMoreResults(int current) { return false; }
        @Override public int getUpdateCount() { return 0; }
        @Override public long getLargeUpdateCount() { return 0; }
        @Override public void setEscapeProcessing(boolean enable) {}
        @Override public java.sql.ResultSet getResultSet() { return null; }
        @Override public java.sql.ResultSet getGeneratedKeys() { return null; }
        @Override public void setClob(int parameterIndex, java.io.Reader reader) {}
        @Override public void setClob(int parameterIndex, java.sql.Clob x) {}
        @Override public void setSQLXML(int parameterIndex, java.sql.SQLXML x) {}
        @Override public void setNClob(int parameterIndex, java.io.Reader reader) {}
        @Override public void clearParameters() {}
        @Override public void setNClob(int parameterIndex, java.io.Reader reader, long length) {}
    }
}
