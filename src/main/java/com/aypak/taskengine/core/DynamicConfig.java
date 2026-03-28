package com.aypak.taskengine.core;

import lombok.Builder;
import lombok.Getter;

/**
 * Dynamic configuration for runtime pool adjustments.
 */
@Getter
@Builder
public class DynamicConfig {

    /**
     * New core pool size.
     */
    private final Integer corePoolSize;

    /**
     * New max pool size.
     */
    private final Integer maxPoolSize;

    /**
     * Validate configuration values.
     *
     * @throws IllegalArgumentException if values are invalid
     */
    public void validate() {
        if (corePoolSize != null && corePoolSize < 0) {
            throw new IllegalArgumentException("corePoolSize must be >= 0");
        }
        if (maxPoolSize != null && maxPoolSize < 0) {
            throw new IllegalArgumentException("maxPoolSize must be >= 0");
        }
        if (corePoolSize != null && maxPoolSize != null && corePoolSize > maxPoolSize) {
            throw new IllegalArgumentException("corePoolSize cannot exceed maxPoolSize");
        }
    }
}