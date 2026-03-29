package com.aypak.taskengine.alarm.core;

/**
 * 拒绝策略枚举
 * 当队列满时采用的处理策略
 */
public enum RejectPolicy {

    /**
     * 丢弃新数据
     * 直接拒绝新提交的告警，不进入队列
     */
    DROP,

    /**
     * 丢弃最旧数据
     * 移除队列中最旧的告警，为新告警腾出空间
     */
    DROP_OLDEST,

    /**
     * 阻塞等待
     * 阻塞调用者线程直到队列有空间
     */
    BLOCK,

    /**
     * 调用者线程处理
     * 在提交告警的调用者线程中直接处理告警
     */
    CALLER_RUNS
}
