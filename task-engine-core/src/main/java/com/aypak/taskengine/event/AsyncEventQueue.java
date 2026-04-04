package com.aypak.taskengine.event;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 异步事件队列 - 使用缓冲队列实现高性能事件处理。
 * Async event queue using buffering for high-performance event processing.
 *
 * <p>特点：</p>
 * <ul>
 *     <li>基于 {@link ArrayBlockingQueue} 的有界队列</li>
 *     <li>队列满时自动丢弃，不阻塞主线程</li>
 *     <li>独立消费者线程异步处理</li>
 *     <li>支持优雅关闭</li>
 * </ul>
 *
 * @param <T> 事件类型 / event type
 */
@Slf4j
public class AsyncEventQueue<T> {

    private final BlockingQueue<T> queue;
    private final ExecutorService consumerExecutor;
    private final Consumer<T> eventHandler;
    private final String name;
    private final long discardThreshold;

    private final AtomicLong totalEvents = new AtomicLong(0);
    private final AtomicLong discardedEvents = new AtomicLong(0);
    private volatile boolean running = true;

    /**
     * 创建异步事件队列。
     * Create async event queue.
     *
     * @param name         队列名称 / queue name
     * @param capacity     队列容量（默认 10000）/ queue capacity (default 10000)
     * @param eventHandler 事件处理器 / event handler
     */
    public AsyncEventQueue(String name, int capacity, Consumer<T> eventHandler) {
        this(name, capacity, 0, eventHandler);
    }

    /**
     * 创建异步事件队列（带丢弃阈值）。
     * Create async event queue with discard threshold.
     *
     * @param name             队列名称 / queue name
     * @param capacity         队列容量 / queue capacity
     * @param discardThreshold 丢弃阈值（队列深度超过此值时开始丢弃，0=始终不丢弃直到满）/ discard threshold
     * @param eventHandler     事件处理器 / event handler
     */
    public AsyncEventQueue(String name, int capacity, long discardThreshold, Consumer<T> eventHandler) {
        this.name = name;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.eventHandler = eventHandler;
        this.discardThreshold = discardThreshold;

        // 创建单线程消费者
        this.consumerExecutor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100),
                r -> {
                    Thread thread = new Thread(r, "async-event-consumer-" + name);
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy()
        );

        startConsumer();
    }

    private void startConsumer() {
        consumerExecutor.submit(() -> {
            log.info("[{}] Event consumer started", name);
            while (running || !queue.isEmpty()) {
                try {
                    T event = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        try {
                            eventHandler.accept(event);
                        } catch (Exception e) {
                            log.warn("[{}] Error processing event: {}", name, e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("[{}] Event consumer interrupted", name);
                    break;
                }
            }
            log.info("[{}] Event consumer stopped", name);
        });
    }

    /**
     * 提交事件到队列。
     * Submit event to queue.
     *
     * <p>非阻塞操作，队列满时返回 false。</p>
     * Non-blocking operation, returns false when queue is full.
     *
     * @param event 事件 / event
     * @return 是否提交成功 / whether submitted successfully
     */
    public boolean offer(T event) {
        if (!running) {
            return false;
        }

        totalEvents.incrementAndGet();

        if (discardThreshold > 0 && queue.size() > discardThreshold) {
            discardedEvents.incrementAndGet();
            if (discardedEvents.get() % 1000 == 0) {
                log.warn("[{}] Queue depth {} exceeds threshold {}, discarding event",
                        name, queue.size(), discardThreshold);
            }
            return false;
        }

        boolean offered = queue.offer(event);
        if (!offered) {
            discardedEvents.incrementAndGet();
            if (discardedEvents.get() % 1000 == 0) {
                log.warn("[{}] Queue full, discarding event. Total discarded: {}",
                        name, discardedEvents.get());
            }
        }
        return offered;
    }

    /**
     * 获取队列深度。
     * Get queue depth.
     *
     * @return 当前队列深度 / current queue depth
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 获取队列容量。
     * Get queue capacity.
     *
     * @return 队列容量 / queue capacity
     */
    public int getCapacity() {
        return queue.remainingCapacity() + queue.size();
    }

    /**
     * 获取总事件数。
     * Get total events count.
     *
     * @return 总事件数 / total events count
     */
    public long getTotalEvents() {
        return totalEvents.get();
    }

    /**
     * 获取丢弃事件数。
     * Get discarded events count.
     *
     * @return 丢弃事件数 / discarded events count
     */
    public long getDiscardedEvents() {
        return discardedEvents.get();
    }

    /**
     * 获取丢弃率。
     * Get discard rate.
     *
     * @return 丢弃率（0-100）/ discard rate (0-100)
     */
    public double getDiscardRate() {
        long total = totalEvents.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) discardedEvents.get() * 100.0 / total;
    }

    /**
     * 停止队列处理。
     * Stop queue processing.
     */
    public void stop() {
        log.info("[{}] Stopping event queue...", name);
        running = false;

        try {
            // 等待消费者处理完剩余事件
            if (!consumerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                consumerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            consumerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("[{}] Event queue stopped. Total: {}, Discarded: {} ({:.2f}%)",
                name, totalEvents.get(), discardedEvents.get(), getDiscardRate());
    }
}
