package com.aypak.taskengine.alarm.engine;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AlarmEngine 基础测试
 */
class AlarmEngineTest {

    @Test
    void testEngineCreation() {
        // 给定
        TestDataSource dataSource = new TestDataSource();
        String insertSql = "INSERT INTO test VALUES (?, ?)";

        // 当
        AlarmEngineImpl engine = new AlarmEngineImpl(dataSource, insertSql, 4, 1000, 5000,
                com.aypak.taskengine.alarm.core.RejectPolicy.DROP);

        // 则
        assertNotNull(engine);
        assertFalse(engine.isRunning()); // 还未启动
    }

    @Test
    void testEngineStartAndStop() {
        // 给定
        TestDataSource dataSource = new TestDataSource();
        AlarmEngineImpl engine = new AlarmEngineImpl(dataSource, "INSERT INTO test VALUES (?, ?)");

        // 当
        engine.start();

        // 则
        assertTrue(engine.isRunning());

        // 清理
        engine.shutdown();
        assertFalse(engine.isRunning());
    }

    @Test
    void testSubmitBeforeStart() {
        // 给定
        TestDataSource dataSource = new TestDataSource();
        AlarmEngineImpl engine = new AlarmEngineImpl(dataSource, "INSERT INTO test VALUES (?, ?)");
        AlarmEvent event = createTestEvent();

        // 当
        boolean accepted = engine.submit(event);

        // 则
        assertFalse(accepted); // 引擎未启动，应该拒绝
    }

    @Test
    void testGetMetrics() {
        // 给定
        TestDataSource dataSource = new TestDataSource();
        AlarmEngineImpl engine = new AlarmEngineImpl(dataSource, "INSERT INTO test VALUES (?, ?)");

        // 当
        engine.start();
        var metrics = engine.getMetrics();

        // 则
        assertNotNull(metrics);

        // 清理
        engine.shutdown();
    }

    /**
     * 创建测试告警事件
     */
    private AlarmEvent createTestEvent() {
        return AlarmEvent.builder()
                .id("test-id-" + System.currentTimeMillis())
                .deviceId("test-device-001")
                .alarmType("TEST_ALARM")
                .occurTime(LocalDateTime.now())
                .description("Test alarm for unit testing")
                .build();
    }

    /**
     * 测试用数据源
     */
    static class TestDataSource implements javax.sql.DataSource {
        @Override
        public java.sql.Connection getConnection() {
            throw new UnsupportedOperationException("TestDataSource does not support real connections");
        }

        @Override
        public java.sql.Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("TestDataSource does not support real connections");
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
            // NOOP
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            // NOOP
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
