package com.aypak.taskengine.alarm.core;

/**
 * 流水线处理节点接口
 * 所有 9 个处理节点必须实现此接口
 */
public interface PipelineNode {

    /**
     * 节点名称
     * @return 节点的唯一标识名称
     */
    String getNodeName();

    /**
     * 处理告警
     * @param event 告警事件
     * @param context 流水线上下文
     * @return 是否继续传递到下一节点
     * @throws Exception 处理异常
     */
    boolean process(AlarmEvent event, PipelineContext context) throws Exception;

    /**
     * 节点失败回调
     * 默认实现记录错误日志
     * @param event 告警事件
     * @param error 异常
     */
    default void onFailure(AlarmEvent event, Throwable error) {
        // 默认空实现，子类可重写
    }

    /**
     * 是否为关键节点
     * 关键节点失败会导致整个告警处理失败
     * 非关键节点失败会被捕获并记录，告警继续传递
     * @return true 表示关键节点
     */
    default boolean isCritical() {
        // 默认非关键，子类可根据需要重写
        return false;
    }

    /**
     * 节点初始化方法
     * 在引擎启动时调用，用于初始化节点资源
     */
    default void initialize() {
        // 默认空实现
    }

    /**
     * 节点销毁方法
     * 在引擎关闭时调用，用于释放节点资源
     */
    default void destroy() {
        // 默认空实现
    }
}
