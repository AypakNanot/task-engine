package com.aypak.taskengine.alarm.engine;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.monitor.AlarmMetrics;

/**
 * 告警引擎对外接口
 */
public interface AlarmEngine {

    /**
     * 提交告警事件
     * @param event 告警事件
     * @return true 表示成功接收，false 表示被拒绝
     */
    boolean submit(AlarmEvent event);

    /**
     * 获取告警指标
     * @return 告警指标
     */
    AlarmMetrics getMetrics();

    /**
     * 获取引擎状态
     * @return true 表示运行中
     */
    boolean isRunning();

    /**
     * 停止引擎
     */
    void shutdown();
}
