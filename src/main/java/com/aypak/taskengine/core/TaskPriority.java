package com.aypak.taskengine.core;

/**
 * Task priority enumeration for scheduling priority.
 */
public enum TaskPriority {

    /**
     * High priority - critical tasks that need immediate processing.
     */
    HIGH(3),

    /**
     * Medium priority - standard business tasks.
     */
    MEDIUM(2),

    /**
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