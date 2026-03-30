package com.aypak.engine.alarm.nodes;

import com.aypak.engine.alarm.core.AlarmEvent;
import com.aypak.engine.alarm.core.PipelineContext;
import com.aypak.engine.alarm.core.PipelineNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 北向推送节点。
 * 通过 HTTP 或其他方式推送告警到北向系统。
 * Northbound push node.
 * Pushes alarms to northbound system via HTTP or other methods.
 */
public class NbPushNode implements PipelineNode {

    private static final Logger log = LoggerFactory.getLogger(NbPushNode.class);

    /** 推送成功计数 / Push success count */
    private final AtomicInteger successCount = new AtomicInteger(0);

    /** 推送失败计数 / Push failure count */
    private final AtomicInteger failureCount = new AtomicInteger(0);

    /** 最大重试次数 / Maximum retry attempts */
    private int maxRetries = 3;

    /** 是否启用推送 / Whether push is enabled */
    private volatile boolean enabled = true;

    @Override
    public String getNodeName() {
        return "NB-Push";
    }

    @Override
    public boolean process(AlarmEvent event, PipelineContext context) {
        if (!enabled) {
            log.debug("NB-Push disabled, skipping alarm {}", event.getId());
            return true;
        }

        long startTime = System.currentTimeMillis();

        try {
            // 执行推送（带重试）/ Execute push (with retry)
            boolean pushed = pushWithRetry(event, context);

            if (pushed) {
                successCount.incrementAndGet();
                context.markNotified();
                event.setStatus(AlarmEvent.ProcessingStatus.NOTIFIED);
                log.debug("NB-Push node successfully pushed alarm {} in {}ms",
                        event.getId(), System.currentTimeMillis() - startTime);
            } else {
                failureCount.incrementAndGet();
                log.warn("NB-Push failed after retries for alarm {}", event.getId());
            }

            return pushed;

        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.error("NB-Push failed for alarm {}", event.getId(), e);
            throw e;
        }
    }

    /**
     * 带重试的推送。
     * Push with retry.
     */
    private boolean pushWithRetry(AlarmEvent event, PipelineContext context) {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                boolean success = doPush(event, context);
                if (success) {
                    return true;
                }
                attempts++;
                if (attempts < maxRetries) {
                    log.debug("Push attempt {} failed for alarm {}, retrying...",
                            attempts, event.getId());
                    Thread.sleep(100 * attempts); // 递增延迟 / Incremental delay
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Push interrupted for alarm {}", event.getId());
                return false;
            } catch (Exception e) {
                attempts++;
                log.debug("Push attempt {} failed for alarm {}: {}",
                        attempts, event.getId(), e.getMessage());
            }
        }
        return false;
    }

    /**
     * 执行实际推送。
     * 子类可重写此方法实现具体的推送逻辑。
     * Execute actual push.
     * Subclasses can override this method to implement specific push logic.
     */
    protected boolean doPush(AlarmEvent event, PipelineContext context) {
        // 默认实现：模拟推送成功
        // Default implementation: simulate push success
        // 实际实现应该：/ Actual implementation should:
        // 1. 构建 HTTP 请求 / Build HTTP request
        // 2. 发送到北向系统 / Send to northbound system
        // 3. 处理响应 / Handle response

        log.debug("Pushing alarm {} to northbound system", event.getId());

        // 模拟推送延迟 / Simulate push delay
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return true;
    }

    /**
     * 设置最大重试次数。
     * Set maximum retry attempts.
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    /**
     * 设置是否启用推送。
     * Set whether push is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取推送成功计数。
     * Get push success count.
     */
    public int getSuccessCount() {
        return successCount.get();
    }

    /**
     * 获取推送失败计数。
     * Get push failure count.
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    @Override
    public void onFailure(AlarmEvent event, Throwable error) {
        log.error("NbPushNode failed for alarm {}: {}", event.getId(), error.getMessage());
    }
}
