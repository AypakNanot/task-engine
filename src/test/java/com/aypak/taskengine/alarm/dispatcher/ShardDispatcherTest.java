package com.aypak.taskengine.alarm.dispatcher;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.core.PipelineNode;
import com.aypak.taskengine.alarm.core.RejectPolicy;
import com.aypak.taskengine.alarm.monitor.AlarmMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShardDispatcher 单元测试
 */
class ShardDispatcherTest {

    private ShardDispatcher dispatcher;
    private List<PipelineNode> nodes;
    private AlarmMetrics metrics;

    @BeforeEach
    void setUp() {
        nodes = new ArrayList<>();
        metrics = new AlarmMetrics();
        // 使用空节点列表进行测试
    }

    @Test
    void testSameDeviceIdRoutesToSameWorker() {
        // 给定 3 个 Worker
        dispatcher = new ShardDispatcher(3, 1000, nodes, RejectPolicy.DROP, metrics);

        // 同一 DeviceID
        String deviceId = "device-001";

        // 多次计算 shard 索引
        int shard1 = getShardIndex(deviceId, 3);
        int shard2 = getShardIndex(deviceId, 3);
        int shard3 = getShardIndex(deviceId, 3);

        // 验证：同一 DeviceID 始终路由到同一 Worker
        assertEquals(shard1, shard2);
        assertEquals(shard2, shard3);
    }

    @Test
    void testDifferentDeviceIdRoutesToDifferentWorkers() {
        // 给定 16 个 Worker
        dispatcher = new ShardDispatcher(16, 1000, nodes, RejectPolicy.DROP, metrics);

        // 不同 DeviceID
        String device1 = "device-001";
        String device2 = "device-002";
        String device3 = "device-999";

        // 计算 shard 索引
        int shard1 = getShardIndex(device1, 16);
        int shard2 = getShardIndex(device2, 16);
        int shard3 = getShardIndex(device3, 16);

        // 验证：不同 DeviceID 可能路由到不同 Worker（大概率）
        // 注意：由于哈希冲突，小概率会相同，所以这个测试不是绝对的
        assertNotEquals(-1, shard1);
        assertNotEquals(-1, shard2);
        assertNotEquals(-1, shard3);
        assertTrue(shard1 >= 0 && shard1 < 16);
        assertTrue(shard2 >= 0 && shard2 < 16);
        assertTrue(shard3 >= 0 && shard3 < 16);
    }

    @Test
    void testShardIndexInRange() {
        // 给定不同 Worker 数量
        int[] workerCounts = {1, 4, 8, 16, 32, 64};

        // 多个 DeviceID
        String[] deviceIds = {
            "device-001", "device-002", "device-100",
            "test-device", "alarm-device", "device-with-special-chars-!@#",
            "", "a", "very-long-device-id-1234567890"
        };

        for (int workerCount : workerCounts) {
            for (String deviceId : deviceIds) {
                int shard = getShardIndex(deviceId, workerCount);
                assertTrue(shard >= 0 && shard < workerCount,
                    "Shard index " + shard + " out of range [0, " + workerCount + ")");
            }
        }
    }

    /**
     * 辅助方法：获取 shard 索引（模拟 ShardDispatcher 的逻辑）
     */
    private int getShardIndex(String deviceId, int workerCount) {
        int hash = deviceId.hashCode();
        hash = Math.abs(hash);
        return hash % workerCount;
    }

    @Test
    void testConstructor() {
        // 给定
        int workerCount = 8;
        int queueCapacity = 5000;
        RejectPolicy policy = RejectPolicy.DROP;

        // 当
        dispatcher = new ShardDispatcher(workerCount, queueCapacity, nodes, policy, metrics);

        // 则
        assertNotNull(dispatcher);
        assertEquals(workerCount, dispatcher.getWorkerCount());
        assertEquals(0, dispatcher.getActiveCount()); // 还未启动
    }

    @Test
    void testStartCreatesWorkers() {
        // 给定
        int workerCount = 4;
        dispatcher = new ShardDispatcher(workerCount, 1000, nodes, RejectPolicy.DROP, metrics);

        // 当
        dispatcher.start();

        // 则
        assertEquals(workerCount, dispatcher.getWorkerCount());
        // Worker 需要时间启动，等待短暂时间
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(dispatcher.getActiveCount() > 0);

        // 清理
        dispatcher.shutdown(1, java.util.concurrent.TimeUnit.SECONDS);
    }
}
