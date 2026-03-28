package com.aypak.taskengine.core;

/**
 * Rejection policy enumeration for handling task rejection when queue is full.
 */
public enum RejectionPolicy {

    /**
     * Abort with alert - drop task, log error, increment failure metric.
     * Use for: critical alert tasks where data loss must be notified.
     */
    ABORT_WITH_ALERT,

    /**
     * Caller runs - execute task in caller thread.
     * Use for: cleanup tasks where caller can handle execution.
     */
    CALLER_RUNS,

    /**
     * Block wait - block caller until queue space available.
     * Use for: guaranteed delivery scenarios.
     */
    BLOCK_WAIT,

    /**
     * Discard oldest - remove oldest queued task, add new one.
     * Use for: non-critical data where freshness is preferred.
     */
    DISCARD_OLDEST
}