package com.aypak.engine.alarm.engine;

import com.aypak.engine.alarm.dispatcher.ShardDispatcher;
import com.aypak.engine.alarm.receiver.AlarmReceiver;
import com.aypak.engine.alarm.batch.BatchDBExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 优雅停机处理器
 * 确保停机时不丢失数据
 * Graceful shutdown handler.
 * Ensures no data loss during shutdown.
 */
public class GracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);

    /** 停机超时时间（秒）/ Shutdown timeout in seconds */
    private final long shutdownTimeoutSec;

    /** 告警接收器 / Alarm receiver */
    private final AlarmReceiver receiver;

    /** 分片调度器 / Shard dispatcher */
    private final ShardDispatcher dispatcher;

    /** 批量数据库执行器 / Batch database executor */
    private final BatchDBExecutor dbExecutor;

    /** 停机锁存器 / Shutdown latch */
    private CountDownLatch shutdownLatch;

    /** 运行标志 / Running flag */
    private volatile boolean shuttingDown = false;

    /**
     * 创建优雅停机处理器
     * Create graceful shutdown handler.
     */
    public GracefulShutdown(AlarmReceiver receiver, ShardDispatcher dispatcher,
                           BatchDBExecutor dbExecutor, long shutdownTimeoutSec) {
        this.receiver = receiver;
        this.dispatcher = dispatcher;
        this.dbExecutor = dbExecutor;
        this.shutdownTimeoutSec = shutdownTimeoutSec;
    }

    /**
     * 注册 JVM 停机钩子
     * Register JVM shutdown hook.
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "AlarmEngine-ShutdownHook"));
        log.info("GracefulShutdown hook registered");
    }

    /**
     * 执行优雅停机
     * Execute graceful shutdown.
     */
    public void shutdown() {
        if (shuttingDown) {
            log.warn("Shutdown already in progress");
            return;
        }

        log.info("===========================================");
        log.info("AlarmEngine starting graceful shutdown...");
        log.info("===========================================");

        shuttingDown = true;
        long startTime = System.currentTimeMillis();

        try {
            // 步骤 1: 停止接收新告警
            log.info("Step 1: Stopping AlarmReceiver...");
            receiver.stop();
            log.info("AlarmReceiver stopped. Remaining queue size: {}", receiver.getQueueSize());

            // 步骤 2: 等待 Worker 队列清空
            log.info("Step 2: Waiting for Worker queues to drain...");
            shutdownLatch = new CountDownLatch(1);

            // 启动等待线程
            Thread drainWaiter = new Thread(this::waitForDrain, "DrainWaiter");
            drainWaiter.start();

            // 等待清空完成或超时
            if (!shutdownLatch.await(shutdownTimeoutSec, TimeUnit.SECONDS)) {
                log.warn("Timeout waiting for Worker queues to drain");
            }

            drainWaiter.interrupt();

            // 步骤 3: 停止分片调度器
            log.info("Step 3: Stopping ShardDispatcher...");
            dispatcher.shutdown(shutdownTimeoutSec / 2, TimeUnit.SECONDS);

            // 步骤 4: 刷写缓冲区数据到数据库
            log.info("Step 4: Flushing batch buffer to database...");
            if (dbExecutor != null) {
                dbExecutor.flush();
                dbExecutor.shutdown();
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("===========================================");
            log.info("AlarmEngine shutdown completed in {}ms", elapsed);
            log.info("===========================================");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Shutdown interrupted", e);
        } catch (Exception e) {
            log.error("Shutdown failed", e);
        }
    }

    /**
     * 等待 Worker 队列清空
     * Wait for Worker queues to drain.
     */
    private void waitForDrain() {
        try {
            // 检查 Receiver 队列和所有 Worker 队列 / Check Receiver and all Worker queues
            while (!Thread.currentThread().isInterrupted()) {
                int receiverSize = receiver.getQueueSize();
                int[] workerDepths = dispatcher.getWorkerQueueDepths();
                int totalWorkerDepth = 0;

                for (int depth : workerDepths) {
                    totalWorkerDepth += depth;
                }

                int totalDepth = receiverSize + totalWorkerDepth;

                if (totalDepth == 0) {
                    log.info("All queues drained successfully");
                    shutdownLatch.countDown();
                    break;
                }

                log.debug("Waiting for queues to drain: Receiver[{}] Workers[{}] Total[{}]",
                        receiverSize, totalWorkerDepth, totalDepth);

                // 等待 100ms 后重试 / Wait 100ms before retry
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Drain waiter interrupted");
        } catch (Exception e) {
            log.error("Error waiting for drain", e);
            shutdownLatch.countDown();
        }
    }

    /**
     * 是否正在停机
     * Whether shutting down.
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }
}
