package com.aypak.taskengine.alarm.engine;

import com.aypak.taskengine.alarm.core.AlarmEvent;
import com.aypak.taskengine.alarm.monitor.AlarmMetrics;

/**
 * 告警引擎对外接口
 * Alarm engine interface.
 * Main entry point for alarm processing engine.
 */
public interface AlarmEngine {

    /**
     * 提交告警事件
     * Submit alarm event.
     * @param event 告警事件 / alarm event
     * @return true 表示成功接收，false 表示被拒绝 / true if successfully received, false if rejected
     */
    boolean submit(AlarmEvent event);

    /**
     * 获取告警指标
     * Get alarm metrics.
     * @return 告警指标 / alarm metrics
     */
    AlarmMetrics getMetrics();

    /**
     * 获取引擎状态
     * Get engine status.
     * @return true 表示运行中 / true if running
     */
    boolean isRunning();

    /**
     * 停止引擎
     * Shutdown engine.
     */
    void shutdown();
}
