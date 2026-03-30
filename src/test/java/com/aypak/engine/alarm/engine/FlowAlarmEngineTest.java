package com.aypak.engine.alarm.engine;

import com.aypak.engine.alarm.core.AlarmEvent;
import com.aypak.engine.alarm.core.RejectPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FlowAlarmEngine 测试。
 * FlowAlarmEngine tests.
 */
@DisplayName("FlowAlarmEngine Tests")
class FlowAlarmEngineTest {

    @Test
    @DisplayName("Should create FlowAlarmEngine")
    void testFlowAlarmEngineCreation() {
        TestDataSource dataSource = new TestDataSource();
        FlowAlarmEngine engine = new FlowAlarmEngine(dataSource, "INSERT INTO test VALUES (?, ?)");

        assertNotNull(engine);
        assertFalse(engine.isRunning());
    }

    @Test
    @DisplayName("Should start and stop engine")
    void testEngineStartAndStop() throws Exception {
        TestDataSource dataSource = new TestDataSource();
        FlowAlarmEngine engine = new FlowAlarmEngine(dataSource, "INSERT INTO test VALUES (?, ?)");

        engine.start();
        assertTrue(engine.isRunning());

        engine.shutdown();
        assertFalse(engine.isRunning());
    }

    @Test
    @DisplayName("Should reject events before start")
    void testSubmitBeforeStart() {
        TestDataSource dataSource = new TestDataSource();
        FlowAlarmEngine engine = new FlowAlarmEngine(dataSource, "INSERT INTO test VALUES (?, ?)");
        AlarmEvent event = createTestEvent();

        boolean accepted = engine.submit(event);

        assertFalse(accepted);
    }

    @Test
    @DisplayName("Should accept and process events")
    void testSubmitAndProcess() throws Exception {
        TestDataSource dataSource = new TestDataSource();
        FlowAlarmEngine engine = new FlowAlarmEngine(dataSource, "INSERT INTO test VALUES (?, ?)", 4, 1000, 5000, RejectPolicy.DROP);

        engine.start();

        int submitCount = 10;
        for (int i = 0; i < submitCount; i++) {
            AlarmEvent event = createTestEvent();
            boolean accepted = engine.submit(event);
            assertTrue(accepted, "Event " + i + " should be accepted");
        }

        Thread.sleep(2000);

        var metrics = engine.getMetrics();
        assertNotNull(metrics);

        engine.shutdown();
    }

    @Test
    @DisplayName("Should get metrics")
    void testGetMetrics() throws Exception {
        TestDataSource dataSource = new TestDataSource();
        FlowAlarmEngine engine = new FlowAlarmEngine(dataSource, "INSERT INTO test VALUES (?, ?)");

        engine.start();
        var metrics = engine.getMetrics();

        assertNotNull(metrics);

        engine.shutdown();
    }

    private AlarmEvent createTestEvent() {
        return AlarmEvent.builder()
                .id("test-id-" + System.currentTimeMillis())
                .deviceId("test-device-001")
                .alarmType("TEST_ALARM")
                .occurTime(java.time.LocalDateTime.now())
                .description("Test alarm for FlowAlarmEngine testing")
                .build();
    }

    /**
     * 测试用数据源
     */
    static class TestDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("TestDataSource does not support real connections");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("TestDataSource does not support real connections");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public Logger getParentLogger() {
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
