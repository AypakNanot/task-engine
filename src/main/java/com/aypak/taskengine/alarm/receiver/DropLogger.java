package com.aypak.taskengine.alarm.receiver;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 丢弃日志器
 * 记录被丢弃的告警并防止日志风暴
 */
public class DropLogger {

    private static final Logger log = LoggerFactory.getLogger(DropLogger.class);

    /** 丢弃计数 */
    private final AtomicLong dropCount = new AtomicLong(0);

    /** 最后告警时间 */
    private final AtomicLong lastAlertTime = new AtomicLong(0);

    /** 告警间隔（毫秒）*/
    private static final long ALERT_INTERVAL_MS = 1000;

    /** 当前秒的丢弃数 */
    private final AtomicLong currentSecondDrops = new AtomicLong(0);

    /** 当前秒的起始时间 */
    private volatile long currentSecondStart = System.currentTimeMillis();

    /**
     * 记录丢弃
     */
    public void recordDrop(AlarmEvent event) {
        dropCount.incrementAndGet();
        currentSecondDrops.incrementAndGet();

        long now = System.currentTimeMillis();

        // 检查是否需要输出告警
        if (now - lastAlertTime.get() >= ALERT_INTERVAL_MS) {
            // 重置当前秒的计数
            if (now - currentSecondStart >= 1000) {
                currentSecondStart = now;
                currentSecondDrops.set(0);
            }

            if (lastAlertTime.compareAndSet(lastAlertTime.get(), now)) {
                long dropsPerSecond = currentSecondDrops.get();
                log.warn("High drop rate detected: {} drops/sec, total drops: {}",
                        dropsPerSecond, dropCount.get());
            }
        }

        // 调试日志（可能被大量输出，生产环境建议关闭）
        log.debug("Dropped alarm: {}", event.getId());
    }

    /**
     * 获取总丢弃数
     */
    public long getTotalDrops() {
        return dropCount.get();
    }

    /**
     * 获取当前秒的丢弃数
     */
    public long getCurrentSecondDrops() {
        return currentSecondDrops.get();
    }

    /**
     * 获取最后告警时间
     */
    public long getLastAlertTime() {
        return lastAlertTime.get();
    }

    /**
     * 重置计数器
     */
    public void reset() {
        dropCount.set(0);
        currentSecondDrops.set(0);
        lastAlertTime.set(0);
    }
}
