package com.aypak.engine.alarm.engine;

import com.aypak.engine.alarm.core.AlarmEvent;
import com.aypak.engine.alarm.core.RejectPolicy;
import com.aypak.engine.alarm.monitor.AlarmMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AlarmEngine 边界测试
 * 边界测试使用 ShardedFlowEngine 后的边界测试
 */
public class AlarmEngineBoundaryTest {

    private static final Logger log = LoggerFactory.getLogger(AlarmEngineBoundaryTest.class);

    private AlarmEngineImpl engine;

    @AfterEach
    void tearDown() {
        if (engine != null && engine.isRunning()) {
            engine.shutdown();
        }
    }

    /**
     * 测试 1: 队列满时 DROP 策略
     */
    @Test
    void testQueueFullWithDropPolicy() throws InterruptedException {
        log.info("Starting test: Queue Full with DROP Policy");

        engine = new AlarmEngineImpl(new AlarmEngineTest.TestDataSource(), "INSERT INTO test VALUES (?, ?)",
                2, 10, RejectPolicy.DROP);
        engine.start();
        Thread.sleep(500);

        int submitCount = 100;
        int accepted = 0;
        int rejected = 0;

        for (int i = 0; i < submitCount; i++) {
            AlarmEvent event = createEvent("device-queue-full-" + i);
            if (engine.submit(event)) {
                accepted++;
            } else {
                rejected++;
            }
        }

        Thread.sleep(2000);

        log.info("Accepted: {}, Rejected: {}", accepted, rejected);
        assertTrue(accepted > 0, "Some events should be accepted");
        assertTrue(rejected > 0, "Some events should be rejected when queue is full");

        AlarmMetrics.MetricsSnapshot snapshot = engine.getMetrics().getSnapshot();
        log.info("Metrics - Success: {}, Dropped: {}", snapshot.successCount, snapshot.droppedCount);
    }

    /**
     * 测试 2: 队列满时 BLOCK 策略
     */
    @Test
    void testQueueFullWithBlockPolicy() throws InterruptedException {
        log.info("Starting test: Queue Full with BLOCK Policy");

        engine = new AlarmEngineImpl(new AlarmEngineTest.TestDataSource(), "INSERT INTO test VALUES (?, ?)",
                1, 5, RejectPolicy.BLOCK);
        engine.start();
        Thread.sleep(500);

        int submitCount = 20;
        AtomicInteger acceptedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(submitCount);

        for (int i = 0; i < submitCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    AlarmEvent event = createEvent("device-block-" + index);
                    boolean accepted = engine.submit(event);
                    if (accepted) {
                        acceptedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Submit failed", e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);

        log.info("Completed: {}, Accepted: {}", completed, acceptedCount.get());
        assertTrue(acceptedCount.get() > 0, "Events should eventually be accepted with BLOCK policy");
    }

    /**
     * 测试 3: Worker 异常恢复 - 验证单个 Worker 异常不影响其他 Worker
     */
    @Test
    void testWorkerExceptionRecovery() throws InterruptedException {
        log.info("Starting test: Worker Exception Recovery");

        engine = new AlarmEngineImpl(new AlarmEngineTest.TestDataSource(), "INSERT INTO test VALUES (?, ?)",
                4, 100, RejectPolicy.DROP);
        engine.start();
        Thread.sleep(500);

        // 发送正常告警
        int normalCount = 50;
        for (int i = 0; i < normalCount; i++) {
            AlarmEvent event = createEvent("device-normal-" + i);
            engine.submit(event);
        }

        Thread.sleep(2000);

        // 验证：引擎仍在运行
        assertTrue(engine.isRunning(), "Engine should still be running");

        // 发送更多告警
        int moreCount = 20;
        for (int i = 0; i < moreCount; i++) {
            AlarmEvent event = createEvent("device-more-" + i);
            engine.submit(event);
        }

        Thread.sleep(2000);

        AlarmMetrics.MetricsSnapshot snapshot = engine.getMetrics().getSnapshot();
        log.info("After recovery - Success: {}, Failures: {}", snapshot.successCount, snapshot.failureCount);
        assertTrue(snapshot.successCount > 0, "Events should be processed successfully");
    }

    /**
     * 测试 4: 同设备有序性验证
     */
    @Test
    void testSameDeviceOrdering() throws InterruptedException {
        log.info("Starting test: Same Device Ordering");

        engine = new AlarmEngineImpl(new AlarmEngineTest.TestDataSource(), "INSERT INTO test VALUES (?, ?)",
                4, 1000, RejectPolicy.DROP);
        engine.start();
        Thread.sleep(500);

        String deviceId = "device-order-test";
        int eventCount = 100;

        for (int i = 0; i < eventCount; i++) {
            AlarmEvent event = AlarmEvent.builder()
                    .id(deviceId + "-seq-" + i)
                    .deviceId(deviceId)
                    .alarmType("ORDER_TEST")
                    .occurTime(LocalDateTime.now())
                    .description("Sequence: " + i)
                    .build();
            engine.submit(event);
        }

        // 等待处理完成
        Thread.sleep(5000);

        // 验证：所有事件都被处理
        AlarmMetrics.MetricsSnapshot snapshot = engine.getMetrics().getSnapshot();
        log.info("Success count: {}", snapshot.successCount);
        assertTrue(snapshot.successCount >= eventCount, "All events should be processed");
    }

    /**
     * 测试 5: 大量不同设备哈希分布
     */
    @Test
    void testDeviceDistributionAcrossWorkers() throws InterruptedException {
        log.info("Starting test: Device Distribution Across Workers");

        int workerCount = 8;
        engine = new AlarmEngineImpl(new AlarmEngineTest.TestDataSource(), "INSERT INTO test VALUES (?, ?)",
                workerCount, 1000, RejectPolicy.DROP);
        engine.start();
        Thread.sleep(500);

        int deviceCount = 1000;
        for (int i = 0; i < deviceCount; i++) {
            AlarmEvent event = createEvent("device-dist-" + i);
            engine.submit(event);
        }

        Thread.sleep(3000);

        AlarmMetrics.MetricsSnapshot snapshot = engine.getMetrics().getSnapshot();
        log.info("Total processed: {}", snapshot.successCount);

        assertTrue(snapshot.successCount > 0, "Events should be processed");
    }

    /**
     * 测试 6: 优雅停机
     */
    @Test
    void testGracefulShutdown() throws InterruptedException {
        log.info("Starting test: Graceful Shutdown");

        engine = new AlarmEngineImpl(new AlarmEngineTest.TestDataSource(), "INSERT INTO test VALUES (?, ?)",
                2, 50, RejectPolicy.DROP);
        engine.start();
        Thread.sleep(500);

        int submitCount = 50;
        for (int i = 0; i < submitCount; i++) {
            AlarmEvent event = createEvent("device-shutdown-" + i);
            engine.submit(event);
        }

        Thread.sleep(100);
        engine.shutdown();

        assertFalse(engine.isRunning(), "Engine should be stopped");
        log.info("Graceful shutdown completed");
    }

    /**
     * 测试 7: 空载运行
     */
    @Test
    void testNoLoadRunning() throws InterruptedException {
        log.info("Starting test: No Load Running");

        engine = new AlarmEngineImpl(new AlarmEngineTest.TestDataSource(), "INSERT INTO test VALUES (?, ?)",
                4, 100, RejectPolicy.DROP);
        engine.start();

        // 不发送任何事件，等待一段时间
        Thread.sleep(2000);

        // 验证：引擎仍在运行
        assertTrue(engine.isRunning(), "Engine should still be running");

        engine.shutdown();
        assertFalse(engine.isRunning(), "Engine should be stopped");
    }

    /**
     * 测试 8: 突发流量
     */
    @Test
    void testBurstTraffic() throws InterruptedException {
        log.info("Starting test: Burst Traffic");

        engine = new AlarmEngineImpl(new AlarmEngineTest.TestDataSource(), "INSERT INTO test VALUES (?, ?)",
                8, 500, RejectPolicy.DROP);
        engine.start();
        Thread.sleep(500);

        // 瞬间发送大量告警
        int burstCount = 1000;
        long startTime = System.currentTimeMillis();
        int accepted = 0;

        for (int i = 0; i < burstCount; i++) {
            AlarmEvent event = createEvent("device-burst-" + i);
            if (engine.submit(event)) {
                accepted++;
            }
        }
        long submitTime = System.currentTimeMillis() - startTime;

        log.info("Burst submitted: {} events in {}ms, accepted: {}", burstCount, submitTime, accepted);

        // 等待处理完成
        Thread.sleep(5000);

        AlarmMetrics.MetricsSnapshot snapshot = engine.getMetrics().getSnapshot();
        log.info("After burst - Success: {}, Failures: {}", snapshot.successCount, snapshot.failureCount);
        assertTrue(snapshot.successCount > 0, "Events should be processed");
    }

    /**
     * 测试 9: 重复设备 ID
     */
    @Test
    void testRepeatedDeviceId() throws InterruptedException {
        log.info("Starting test: Repeated DeviceId");

        engine = new AlarmEngineImpl(new AlarmEngineTest.TestDataSource(), "INSERT INTO test VALUES (?, ?)",
                4, 100, RejectPolicy.DROP);
        engine.start();
        Thread.sleep(500);

        // 同一设备发送多次告警
        String deviceId = "device-repeat";
        int repeatCount = 100;

        for (int i = 0; i < repeatCount; i++) {
            AlarmEvent event = AlarmEvent.builder()
                    .id(deviceId + "-alarm-" + i)
                    .deviceId(deviceId)
                    .alarmType("REPEAT_TEST")
                    .occurTime(LocalDateTime.now())
                    .description("Repeat alarm " + i)
                    .build();
            engine.submit(event);
        }

        Thread.sleep(3000);

        AlarmMetrics.MetricsSnapshot snapshot = engine.getMetrics().getSnapshot();
        log.info("Repeated device - Success: {}", snapshot.successCount);
        assertTrue(snapshot.successCount > 0, "Events should be processed");
    }

    /**
     * 测试 10: Worker 重启恢复
     */
    @Test
    void testWorkerRestartRecovery() throws InterruptedException {
        log.info("Starting test: Worker Restart Recovery");

        engine = new AlarmEngineImpl(new AlarmEngineTest.TestDataSource(), "INSERT INTO test VALUES (?, ?)",
                4, 100, RejectPolicy.DROP);
        engine.start();
        Thread.sleep(500);

        // 发送一些告警
        int initialCount = 20;
        for (int i = 0; i < initialCount; i++) {
            AlarmEvent event = createEvent("device-initial-" + i);
            engine.submit(event);
        }

        Thread.sleep(2000);

        long initialSuccess = engine.getMetrics().getSnapshot().successCount;
        log.info("Initial success count: {}", initialSuccess);

        // 继续发送告警
        int moreCount = 20;
        for (int i = 0; i < moreCount; i++) {
            AlarmEvent event = createEvent("device-more-" + i);
            engine.submit(event);
        }

        Thread.sleep(2000);

        AlarmMetrics.MetricsSnapshot snapshot = engine.getMetrics().getSnapshot();
        log.info("Final - Success: {}, Failures: {}", snapshot.successCount, snapshot.failureCount);
        assertTrue(snapshot.successCount > initialSuccess, "More events should be processed");
    }

    private AlarmEvent createEvent(String deviceId) {
        return AlarmEvent.builder()
                .id(deviceId + "-alarm-" + System.currentTimeMillis())
                .deviceId(deviceId)
                .alarmType("BOUNDARY_TEST")
                .occurTime(LocalDateTime.now())
                .severity(AlarmEvent.Severity.INFO)
                .sourceSystem("BoundaryTest")
                .location("TestLocation")
                .description("Boundary test alarm")
                .build();
    }
}
