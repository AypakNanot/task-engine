package com.aypak.engine.task.core;

import lombok.Builder;
import lombok.Getter;

/**
 * 动态配置，用于运行时线程池调整。
 * Dynamic configuration for runtime pool adjustments.
 */
@Getter
@Builder
public class DynamicConfig {

    /**
     * 新的核心线程池大小。
     * New core pool size.
     */
    private final Integer corePoolSize;

    /**
     * 新的最大线程池大小。
     * New max pool size.
     */
    private final Integer maxPoolSize;

    /**
     * 验证配置值。
     * 如果值无效则抛出 IllegalArgumentException。
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