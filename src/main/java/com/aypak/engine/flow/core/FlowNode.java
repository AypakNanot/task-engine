package com.aypak.engine.flow.core;

/**
 * 流水线处理节点接口。
 * Pipeline processing node interface.
 *
 * <p>所有处理节点必须实现此接口。</p>
 * <p>All processing nodes must implement this interface.</p>
 *
 * <p>使用示例 / Usage example:</p>
 * <pre>{@code
 * public class ValidateNode implements FlowNode<String, OrderData> {
 *     @Override
 *     public String getNodeName() { return "Validate"; }
 *
 *     @Override
 *     public boolean process(FlowEvent<String, OrderData> event, FlowContext context) {
 *         if (!isValid(event.getPayload())) {
 *             context.stop();
 *             return false;
 *         }
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * @param <K> 分片键类型 / shard key type
 * @param <T> 负载类型 / payload type
 */
public interface FlowNode<K, T> {

    /**
     * 节点名称。
     * Node name.
     *
     * @return 节点的唯一标识名称 / Unique identifier name of the node
     */
    String getNodeName();

    /**
     * 处理事件。
     * Process event.
     *
     * @param event   事件 / event
     * @param context 上下文 / context
     * @return 是否继续传递到下一节点 / whether to continue to next node
     * @throws Exception 处理异常 / processing exception
     */
    boolean process(FlowEvent<K, T> event, FlowContext context) throws Exception;

    /**
     * 节点失败回调。
     * Node failure callback.
     * 默认实现记录错误日志。
     * Default implementation logs error.
     *
     * @param event 事件 / event
     * @param error 异常 / exception
     */
    default void onFailure(FlowEvent<K, T> event, Throwable error) {
        // 默认空实现，子类可重写 / Default empty implementation, subclasses can override
    }

    /**
     * 是否为关键节点。
     * Whether this is a critical node.
     * 关键节点失败会导致整个事件处理失败。
     * 非关键节点失败会被捕获并记录，事件继续传递。
     * Critical node failure causes entire event processing to fail.
     * Non-critical node failures are caught and logged, event continues.
     *
     * @return true 表示关键节点 / true if critical node
     */
    default boolean isCritical() {
        // 默认非关键，子类可根据需要重写 / Default non-critical, subclasses can override
        return false;
    }

    /**
     * 节点初始化方法。
     * Node initialization method.
     * 在引擎启动时调用，用于初始化节点资源。
     * Called when engine starts, used to initialize node resources.
     */
    default void initialize() {
        // 默认空实现 / Default empty implementation
    }

    /**
     * 节点销毁方法。
     * Node destroy method.
     * 在引擎关闭时调用，用于释放节点资源。
     * Called when engine shuts down, used to release node resources.
     */
    default void destroy() {
        // 默认空实现 / Default empty implementation
    }

    /**
     * 创建简单节点 - 仅处理逻辑，自动生成节点名称。
     * Create a simple node with only processing logic, auto-generating node name.
     *
     * <p>便捷方法，用于快速创建测试节点或简单节点。</p>
     * <p>Convenience method for quickly creating test nodes or simple nodes.</p>
     *
     * <p>使用示例 / Usage example:</p>
     * <pre>{@code
     * FlowNode<String, String> node = FlowNode.of("Validate", (event, context) -> {
     *     return event.getPayload() != null;
     * });
     * }</pre>
     *
     * @param name    节点名称 / node name
     * @param handler 处理函数 / processing function
     * @param <K>     分片键类型 / shard key type
     * @param <T>     负载类型 / payload type
     * @return 简单节点实例 / simple node instance
     */
    static <K, T> FlowNode<K, T> of(String name, FlowHandler<K, T> handler) {
        return new FlowNode<K, T>() {
            @Override
            public String getNodeName() {
                return name;
            }

            @Override
            public boolean process(FlowEvent<K, T> event, FlowContext context) throws Exception {
                return handler.process(event, context);
            }
        };
    }

    /**
     * 创建简单节点 - 使用默认节点名称。
     * Create a simple node with default node name.
     *
     * @param handler 处理函数 / processing function
     * @param <K>     分片键类型 / shard key type
     * @param <T>     负载类型 / payload type
     * @return 简单节点实例 / simple node instance
     */
    static <K, T> FlowNode<K, T> of(FlowHandler<K, T> handler) {
        return of("Node", handler);
    }

    /**
     * 流处理函数接口。
     * Flow processing function interface.
     *
     * @param <K> 分片键类型 / shard key type
     * @param <T> 负载类型 / payload type
     */
    @FunctionalInterface
    interface FlowHandler<K, T> {
        boolean process(FlowEvent<K, T> event, FlowContext context) throws Exception;
    }
}
