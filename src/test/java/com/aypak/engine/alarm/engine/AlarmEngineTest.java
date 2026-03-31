package com.aypak.engine.alarm.engine;

import com.aypak.engine.alarm.core.AlarmEvent;
import com.aypak.engine.flow.core.RejectPolicy;
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
        AlarmEngineImpl engine = new AlarmEngineImpl(dataSource, insertSql, 4, 1000,
                RejectPolicy.DROP);

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

    @Test
    void testSubmitAndProcess() throws InterruptedException {
        // 给定
        TestDataSource dataSource = new TestDataSource();
        AlarmEngineImpl engine = new AlarmEngineImpl(dataSource, "INSERT INTO test VALUES (?, ?)");
        engine.start();

        // 当：提交 10 个告警
        int submitCount = 10;
        for (int i = 0; i < submitCount; i++) {
            AlarmEvent event = createTestEvent();
            boolean accepted = engine.submit(event);
            assertTrue(accepted, "Event " + i + " should be accepted");
        }

        // 等待处理完成
        Thread.sleep(3000);

        // 获取指标
        var metrics = engine.getMetrics().getSnapshot();
        System.out.println("Success count: " + metrics.successCount);
        System.out.println("Failure count: " + metrics.failureCount);
        System.out.println("Dropped count: " + metrics.droppedCount);

        // 清理
        engine.shutdown();

        // 验证：告警应该被处理（成功或失败）
        assertTrue(metrics.successCount > 0 || metrics.failureCount > 0,
                "Events should be processed (success or failure), but got success=0, failure=0");
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
