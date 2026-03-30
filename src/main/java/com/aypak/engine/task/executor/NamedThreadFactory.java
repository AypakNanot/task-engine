package com.aypak.engine.task.executor;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 命名线程工厂，用于标准化线程命名。
 * 格式：{TaskType}-{TaskName}-{ThreadId}
 * Named thread factory for standardized thread naming.
 * Format: {TaskType}-{TaskName}-{ThreadId}
 */
@Slf4j
public class NamedThreadFactory implements ThreadFactory {

    private final String namePrefix;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final boolean daemon;

    public NamedThreadFactory(String taskType, String taskName, boolean daemon) {
        this.namePrefix = taskType + "-" + taskName + "-";
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
        thread.setDaemon(daemon);
        if (thread.getPriority() != Thread.NORM_PRIORITY) {
            thread.setPriority(Thread.NORM_PRIORITY);
        }
        log.debug("Created thread: {}", thread.getName());
        return thread;
    }
}