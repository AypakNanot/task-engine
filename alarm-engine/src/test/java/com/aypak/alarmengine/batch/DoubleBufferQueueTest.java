package com.aypak.alarmengine.batch;

import com.aypak.alarmengine.core.AlarmEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DoubleBufferQueue 单元测试
 */
class DoubleBufferQueueTest {

    private DoubleBufferQueue queue;

    @BeforeEach
    void setUp() {
        queue = new DoubleBufferQueue(10000);
    }

    @Test
    void testOfferAndSwitch() throws InterruptedException {
        // 给定
        AlarmEvent event1 = createTestEvent("event-1");
        AlarmEvent event2 = createTestEvent("event-2");

        // 当
        boolean offered1 = queue.offer(event1);
        boolean offered2 = queue.offer(event2);

        // 则
        assertTrue(offered1);
        assertTrue(offered2);
        assertEquals(2, queue.getActiveSize());

        // 切换缓冲区
        List<AlarmEvent> events = queue.switchAndGet();

        // 验证
        assertNotNull(events);
        assertEquals(2, events.size());
        assertEquals(0, queue.getActiveSize());
    }

    @Test
    void testSwitchReturnsEmptyWhenNoEvents() {
        // 当
        List<AlarmEvent> events = queue.switchAndGet();

        // 则
        assertNull(events);
    }

    @Test
    void testConcurrentOfferAndSwitch() throws InterruptedException {
        // 给定
        int eventCount = 1000;
        CountDownLatch latch = new CountDownLatch(eventCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 当：多线程并发写入
        Thread[] writers = new Thread[10];
        for (int i = 0; i < writers.length; i++) {
            writers[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    try {
                        AlarmEvent event = createTestEvent("event-" + Thread.currentThread().getName() + "-" + j);
                        boolean offered = queue.offer(event, 1, TimeUnit.SECONDS);
                        if (offered) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                }
            });
            writers[i].start();
        }

        // 等待所有写入完成
        latch.await(10, TimeUnit.SECONDS);

        // 验证
        assertEquals(0, failCount.get(), "All offers should succeed");
        assertEquals(eventCount, successCount.get());
    }

    @Test
    void testClosePreventsFurtherOffers() throws InterruptedException {
        // 给定
        queue.close();

        // 当
        AlarmEvent event = createTestEvent("event-1");
        boolean offered = queue.offer(event, 100, TimeUnit.MILLISECONDS);

        // 则
        assertFalse(offered);
    }

    @Test
    void testMarkFlushed() {
        // 给定
        queue.offer(createTestEvent("event-1"));
        queue.offer(createTestEvent("event-2"));

        // 当
        queue.switchAndGet();
        queue.markFlushed(2);

        // 验证
        assertEquals(0, queue.getTotalSize());
    }

    /**
     * 创建测试告警事件
     */
    private AlarmEvent createTestEvent(String id) {
        return AlarmEvent.builder()
                .id(id)
                .deviceId("test-device")
                .alarmType("TEST_ALARM")
                .occurTime(LocalDateTime.now())
                .build();
    }
}
