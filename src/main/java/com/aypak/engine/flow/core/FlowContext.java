package com.aypak.engine.flow.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流水线上下文 - 在节点间传递状态。
 * Flow Context - passes state between nodes.
 *
 * <p>上下文用于在节点之间共享数据和状态。</p>
 * <p>Context is used to share data and state between nodes.</p>
 */
public class FlowContext {

    /** 上下文数据 / Context data */
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    /** 是否应该继续处理 / Whether to continue processing */
    private volatile boolean shouldContinue = true;

    /** 是否已丢弃 / Whether dropped */
    private volatile boolean dropped = false;

    /** 是否已持久化 / Whether persisted */
    private volatile boolean persisted = false;

    /** 节点处理时延 / Node processing latencies */
    private final Map<String, Long> nodeLatencies = new ConcurrentHashMap<>();

    /**
     * 设置上下文值。
     * Set context value.
     *
     * @param key   键 / key
     * @param value 值 / value
     */
    public void set(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 获取上下文值。
     * Get context value.
     *
     * @param key 键 / key
     * @return 值 / value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    /**
     * 获取上下文值，带默认值。
     * Get context value with default.
     *
     * @param key          键 / key
     * @param defaultValue 默认值 / default value
     * @return 值 / value, or default if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        Object value = data.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * 检查是否包含键。
     * Check if contains key.
     *
     * @param key 键 / key
     * @return true 如果包含 / true if contains
     */
    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    /**
     * 移除上下文值。
     * Remove context value.
     *
     * @param key 键 / key
     */
    public void remove(String key) {
        data.remove(key);
    }

    /**
     * 获取所有数据。
     * Get all data.
     *
     * @return 数据映射 / data map
     */
    public Map<String, Object> getAll() {
        return new ConcurrentHashMap<>(data);
    }

    /**
     * 标记停止处理。
     * Mark to stop processing.
     */
    public void stop() {
        this.shouldContinue = false;
    }

    /**
     * 是否应该继续处理。
     * Whether should continue processing.
     *
     * @return true 如果继续 / true if should continue
     */
    public boolean shouldContinue() {
        return shouldContinue;
    }

    /**
     * 标记为已丢弃。
     * Mark as dropped.
     */
    public void markDropped() {
        this.dropped = true;
        this.shouldContinue = false;
    }

    /**
     * 是否已丢弃。
     * Whether dropped.
     *
     * @return true 如果已丢弃 / true if dropped
     */
    public boolean isDropped() {
        return dropped;
    }

    /**
     * 标记为已持久化。
     * Mark as persisted.
     */
    public void markPersisted() {
        this.persisted = true;
    }

    /**
     * 是否已持久化。
     * Whether persisted.
     *
     * @return true 如果已持久化 / true if persisted
     */
    public boolean isPersisted() {
        return persisted;
    }

    /**
     * 标记为已通知。
     * Mark as notified.
     */
    public void markNotified() {
        set("notified", true);
    }

    /**
     * 是否已通知。
     * Whether notified.
     *
     * @return true 如果已通知 / true if notified
     */
    public boolean isNotified() {
        Boolean notified = get("notified");
        return notified != null && notified;
    }

    /**
     * 记录节点处理时延。
     * Record node processing latency.
     *
     * @param nodeName 节点名称 / node name
     * @param latency  时延（毫秒） / latency in milliseconds
     */
    public void recordNodeLatency(String nodeName, long latency) {
        nodeLatencies.put(nodeName, latency);
    }

    /**
     * 获取节点时延。
     * Get node latency.
     *
     * @param nodeName 节点名称 / node name
     * @return 时延 / latency, or null if not found
     */
    public Long getNodeLatency(String nodeName) {
        return nodeLatencies.get(nodeName);
    }

    /**
     * 获取所有节点时延。
     * Get all node latencies.
     *
     * @return 时延映射 / latency map
     */
    public Map<String, Long> getAllNodeLatencies() {
        return new ConcurrentHashMap<>(nodeLatencies);
    }

    /**
     * 清空上下文。
     * Clear context.
     */
    public void clear() {
        data.clear();
        nodeLatencies.clear();
        shouldContinue = true;
        dropped = false;
        persisted = false;
    }
}
