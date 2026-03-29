package com.aypak.taskengine.core;

/**
 * 任务优先级枚举，用于调度优先级。
 * Task priority enumeration for scheduling priority.
 */
public enum TaskPriority {

    /**
     * 高优先级 - 需要立即处理的关键任务。
     * High priority - critical tasks that need immediate processing.
     */
    HIGH(3),

    /**
     * 中优先级 - 标准业务任务。
     * Medium priority - standard business tasks.
     */
    MEDIUM(2),

    /**
     * 低优先级 - 后台维护任务。
     * Low priority - background maintenance tasks.
     */
    LOW(1);

    private final int level;

    TaskPriority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}