package com.aypak.flowengine.core;

import lombok.Data;

import java.util.UUID;

/**
 * 流式事件 - 通用事件数据结构。
 * Flow Event - generic event data structure.
 *
 * <p>使用示例 / Usage example:</p>
 * <pre>{@code
 * // 告警场景
 * FlowEvent<String, AlarmData> alarm = new FlowEvent<>(
 *     "device-001",  // 分片键
 *     alarmData      // 告警数据
 * );
 *
 * // 订单场景
 * FlowEvent<String, OrderData> order = new FlowEvent<>(
 *     "user-123",    // 分片键（同一用户订单有序）
 *     orderData      // 订单数据
 * );
 * }</pre>
 *
 * @param <K> 分片键类型（如 DeviceID, UserID） / shard key type
 * @param <T> 负载数据类型 / payload type
 */
@Data
public class FlowEvent<K, T> {

    /** 事件唯一标识 / Unique event ID */
    private final String id;

    /** 分片键，用于路由到特定 Worker / Shard key for routing to specific Worker */
    private final K shardKey;

    /** 业务负载数据 / Business payload data */
    private final T payload;

    /** 创建时间戳（毫秒） / Creation timestamp in milliseconds */
    private final long createTime;

    /** 接收时间戳（毫秒） / Receive timestamp in milliseconds */
    private long receiveTime;

    /** 完成时间戳（毫秒） / Completion timestamp in milliseconds */
    private long completeTime;

    /** 事件处理状态 / Event processing status */
    private ProcessingStatus status = ProcessingStatus.PENDING;

    /** 错误消息（如果有） / Error message if any */
    private String errorMessage;

    /**
     * 创建流式事件。
     * Create flow event.
     *
     * @param shardKey 分片键 / shard key
     * @param payload  业务数据 / business data
     */
    public FlowEvent(K shardKey, T payload) {
        this.id = UUID.randomUUID().toString();
        this.shardKey = shardKey;
        this.payload = payload;
        this.createTime = System.currentTimeMillis();
    }

    /**
     * 创建流式事件（带自定义 ID）。
     * Create flow event with custom ID.
     *
     * @param id       事件 ID / event ID
     * @param shardKey 分片键 / shard key
     * @param payload  业务数据 / business data
     */
    public FlowEvent(String id, K shardKey, T payload) {
        this.id = id;
        this.shardKey = shardKey;
        this.payload = payload;
        this.createTime = System.currentTimeMillis();
    }

    /**
     * 获取处理时延（毫秒）。
     * Get processing latency in milliseconds.
     *
     * @return 时延 / latency, or -1 if not completed
     */
    public long getLatency() {
        if (completeTime > 0 && receiveTime > 0) {
            return completeTime - receiveTime;
        }
        return -1;
    }

    /**
     * 处理状态枚举。
     * Processing status enum.
     */
    public enum ProcessingStatus {
        /** 待处理 / Pending */
        PENDING,
        /** 处理中 / Processing */
        PROCESSING,
        /** 已完成 / Completed */
        COMPLETED,
        /** 已持久化 / Persisted */
        PERSISTED,
        /** 已丢弃 / Dropped */
        DROPPED,
        /** 失败 / Failed */
        FAILED
    }
}
