package com.aypak.engine.alarm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test for AlarmEngine under memory pressure conditions
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class AlarmEngineMemoryStressTest {

    @Autowired
    private AlarmEngine alarmEngine;
    
    private static final int TOTAL_ALARMS = 100_000;
    private static final int CONCURRENT_THREADS = 50;
    
    @BeforeEach
    void setUp() {
        // Clear any existing state
    }
    
    @Test
    public void testMemoryPressureWithHighThroughput() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(TOTAL_ALARMS);
        AtomicLong processedCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);
        
        // Submit alarms from multiple threads
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            new Thread(() -> {
                for (int j = 0; j < TOTAL_ALARMS / CONCURRENT_THREADS; j++) {
                    try {
                        AlarmData alarm = AlarmData.builder()
                            .deviceId("device-" + (j % 1000)) // Create 1000 unique devices
                            .alarmType("TEST_ALARM")
                            .severity(AlarmSeverity.WARNING)
                            .occurTime(System.currentTimeMillis())
                            .build();
                            
                        alarmEngine.submit(alarm);
                        processedCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            }).start();
        }
        
        // Wait for all submissions to complete
        latch.await();
        
        // Verify results
        assertEquals(0, errorCount.get(), "No errors should occur during submission");
        assertEquals(TOTAL_ALARMS, processedCount.get(), "All alarms should be submitted successfully");
        
        // Allow time for processing to complete
        Thread.sleep(10000);
        
        // Check that queue is not overwhelmed
        assertTrue(alarmEngine.getQueueDepth() < 1000, 
            "Queue depth should not grow unbounded under memory pressure");
    }
    
    @Test
    public void testGcPressureWithLargePayloads() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(10_000);
        
        // Submit alarms with large payloads to create GC pressure
        for (int i = 0; i < 10_000; i++) {
            new Thread(() -> {
                try {
                    // Create large payload (1KB)
                    StringBuilder largePayload = new StringBuilder();
                    for (int j = 0; j < 1024; j++) {
                        largePayload.append("x");
                    }
                    
                    AlarmData alarm = AlarmData.builder()
                        .deviceId("gc-device-" + System.nanoTime())
                        .alarmType("LARGE_PAYLOAD_TEST")
                        .severity(AlarmSeverity.MINOR)
                        .occurTime(System.currentTimeMillis())
                        .data(largePayload.toString())
                        .build();
                        
                    alarmEngine.submit(alarm);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        latch.await();
        Thread.sleep(5000); // Allow processing
        
        // Should not crash or lose data under GC pressure
        assertTrue(alarmEngine.getProcessedCount() > 0, "Some alarms should be processed");
    }
}