package com.aypak.taskengine.alarm.core;

/**
 * 流水线处理节点接口。
 * 所有 9 个处理节点必须实现此接口。
 * Pipeline processing node interface.
 * All 9 processing nodes must implement this interface.
 */
public interface PipelineNode {

    /**
     * 节点名称。
     * Node name.
     * @return 节点的唯一标识名称 / Unique identifier name of the node
     */
    String getNodeName();

    /**
     * 处理告警。
     * Process alarm.
     * @param event 告警事件 / alarm event
     * @param context 流水线上下文 / pipeline context
     * @return 是否继续传递到下一节点 / whether to continue to next node
     * @throws Exception 处理异常 / processing exception
     */
    boolean process(AlarmEvent event, PipelineContext context) throws Exception;

    /**
     * 节点失败回调。
     * 默认实现记录错误日志。
     * Node failure callback.
     * Default implementation logs error.
     * @param event 告警事件 / alarm event
     * @param error 异常 / exception
     */
    default void onFailure(AlarmEvent event, Throwable error) {
        // 默认空实现，子类可重写 / Default empty implementation, subclasses can override
    }

    /**
     * 是否为关键节点。
     * 关键节点失败会导致整个告警处理失败。
     * 非关键节点失败会被捕获并记录，告警继续传递。
     * Whether this is a critical node.
     * Critical node failure causes entire alarm processing to fail.
     * Non-critical node failures are caught and logged, alarm continues.
     * @return true 表示关键节点 / true if critical node
     */
    default boolean isCritical() {
        // 默认非关键，子类可根据需要重写 / Default non-critical, subclasses can override
        return false;
    }

    /**
     * 节点初始化方法。
     * 在引擎启动时调用，用于初始化节点资源。
     * Node initialization method.
     * Called when engine starts, used to initialize node resources.
     */
    default void initialize() {
        // 默认空实现 / Default empty implementation
    }

    /**
     * 节点销毁方法。
     * 在引擎关闭时调用，用于释放节点资源。
     * Node destroy method.
     * Called when engine shuts down, used to release node resources.
     */
    default void destroy() {
        // 默认空实现 / Default empty implementation
    }
}
