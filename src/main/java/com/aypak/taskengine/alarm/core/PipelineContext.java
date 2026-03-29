package com.aypak.taskengine.alarm.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流水线处理上下文。
 * 在 9 个处理节点之间传递共享状态和数据。
 * Pipeline processing context.
 * Passes shared state and data between 9 processing nodes.
 */
public class PipelineContext {

    /** 上下文数据 / Context data */
    private final Map<String, Object> data;

    /** 是否继续传递到下一节点 / Whether to continue to next node */
    private boolean continueProcessing;

    /** 当前处理的节点名称 / Current processing node name */
    private String currentNode;

    /** 节点处理时延（毫秒）/ Node processing latency in milliseconds */
    private final Map<String, Long> nodeLatencies;

    /** 是否已持久化 / Whether persisted */
    private boolean persisted;

    /** 是否已通知 / Whether notified */
    private boolean notified;

    /** 丢弃原因（如果被丢弃）/ Drop reason (if dropped) */
    private String dropReason;

    public PipelineContext() {
        this.data = new ConcurrentHashMap<>();
        this.continueProcessing = true;
        this.nodeLatencies = new ConcurrentHashMap<>();
        this.persisted = false;
        this.notified = false;
    }

    /**
     * 获取上下文数据。
     * Get context data.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    /**
     * 设置上下文数据。
     * Set context data.
     */
    public void set(String key, Object value) {
        this.data.put(key, value);
    }

    /**
     * 检查是否包含某个键。
     * Check if contains key.
     */
    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    /**
     * 是否继续处理。
     * Whether should continue processing.
     */
    public boolean shouldContinue() {
        return continueProcessing;
    }

    /**
     * 设置是否继续处理。
     * Set whether to continue processing.
     */
    public void setContinueProcessing(boolean continueProcessing) {
        this.continueProcessing = continueProcessing;
    }

    /**
     * 停止处理（用于过滤、屏蔽等场景）。
     * Stop processing (used for filtering, masking, etc.).
     */
    public void stopProcessing() {
        this.continueProcessing = false;
    }

    /**
     * 获取当前节点。
     * Get current node.
     */
    public String getCurrentNode() {
        return currentNode;
    }

    /**
     * 设置当前节点。
     * Set current node.
     */
    public void setCurrentNode(String currentNode) {
        this.currentNode = currentNode;
    }

    /**
     * 记录节点处理时延。
     * Record node processing latency.
     */
    public void recordNodeLatency(String nodeName, long latencyMs) {
        this.nodeLatencies.put(nodeName, latencyMs);
    }

    /**
     * 获取节点处理时延。
     * Get node processing latency.
     */
    public Long getNodeLatency(String nodeName) {
        return nodeLatencies.get(nodeName);
    }

    /**
     * 获取所有节点时延。
     * Get all node latencies.
     */
    public Map<String, Long> getAllNodeLatencies() {
        return new ConcurrentHashMap<>(nodeLatencies);
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
     */
    public boolean isPersisted() {
        return persisted;
    }

    /**
     * 标记为已通知。
     * Mark as notified.
     */
    public void markNotified() {
        this.notified = true;
    }

    /**
     * 是否已通知。
     * Whether notified.
     */
    public boolean isNotified() {
        return notified;
    }

    /**
     * 设置丢弃原因。
     * Set drop reason.
     */
    public void setDropReason(String dropReason) {
        this.dropReason = dropReason;
        stopProcessing();
    }

    /**
     * 获取丢弃原因。
     * Get drop reason.
     */
    public String getDropReason() {
        return dropReason;
    }

    /**
     * 是否被丢弃。
     * Whether dropped.
     */
    public boolean isDropped() {
        return dropReason != null;
    }

    /**
     * 复制上下文（用于异步处理场景）。
     * Copy context (used for async processing scenarios).
     */
    public PipelineContext copy() {
        PipelineContext copy = new PipelineContext();
        copy.data.putAll(this.data);
        copy.continueProcessing = this.continueProcessing;
        copy.currentNode = this.currentNode;
        copy.nodeLatencies.putAll(this.nodeLatencies);
        copy.persisted = this.persisted;
        copy.notified = this.notified;
        copy.dropReason = this.dropReason;
        return copy;
    }
}
