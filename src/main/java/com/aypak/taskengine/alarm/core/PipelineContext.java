package com.aypak.taskengine.alarm.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流水线处理上下文
 * 在 9 个处理节点之间传递共享状态和数据
 */
public class PipelineContext {

    /** 上下文数据 */
    private final Map<String, Object> data;

    /** 是否继续传递到下一节点 */
    private boolean continueProcessing;

    /** 当前处理的节点名称 */
    private String currentNode;

    /** 节点处理时延（毫秒）*/
    private final Map<String, Long> nodeLatencies;

    /** 是否已持久化 */
    private boolean persisted;

    /** 是否已通知 */
    private boolean notified;

    /** 丢弃原因（如果被丢弃）*/
    private String dropReason;

    public PipelineContext() {
        this.data = new ConcurrentHashMap<>();
        this.continueProcessing = true;
        this.nodeLatencies = new ConcurrentHashMap<>();
        this.persisted = false;
        this.notified = false;
    }

    /**
     * 获取上下文数据
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    /**
     * 设置上下文数据
     */
    public void set(String key, Object value) {
        this.data.put(key, value);
    }

    /**
     * 检查是否包含某个键
     */
    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    /**
     * 是否继续处理
     */
    public boolean shouldContinue() {
        return continueProcessing;
    }

    /**
     * 设置是否继续处理
     */
    public void setContinueProcessing(boolean continueProcessing) {
        this.continueProcessing = continueProcessing;
    }

    /**
     * 停止处理（用于过滤、屏蔽等场景）
     */
    public void stopProcessing() {
        this.continueProcessing = false;
    }

    /**
     * 获取当前节点
     */
    public String getCurrentNode() {
        return currentNode;
    }

    /**
     * 设置当前节点
     */
    public void setCurrentNode(String currentNode) {
        this.currentNode = currentNode;
    }

    /**
     * 记录节点处理时延
     */
    public void recordNodeLatency(String nodeName, long latencyMs) {
        this.nodeLatencies.put(nodeName, latencyMs);
    }

    /**
     * 获取节点处理时延
     */
    public Long getNodeLatency(String nodeName) {
        return nodeLatencies.get(nodeName);
    }

    /**
     * 获取所有节点时延
     */
    public Map<String, Long> getAllNodeLatencies() {
        return new ConcurrentHashMap<>(nodeLatencies);
    }

    /**
     * 标记为已持久化
     */
    public void markPersisted() {
        this.persisted = true;
    }

    /**
     * 是否已持久化
     */
    public boolean isPersisted() {
        return persisted;
    }

    /**
     * 标记为已通知
     */
    public void markNotified() {
        this.notified = true;
    }

    /**
     * 是否已通知
     */
    public boolean isNotified() {
        return notified;
    }

    /**
     * 设置丢弃原因
     */
    public void setDropReason(String dropReason) {
        this.dropReason = dropReason;
        stopProcessing();
    }

    /**
     * 获取丢弃原因
     */
    public String getDropReason() {
        return dropReason;
    }

    /**
     * 是否被丢弃
     */
    public boolean isDropped() {
        return dropReason != null;
    }

    /**
     * 复制上下文（用于异步处理场景）
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
