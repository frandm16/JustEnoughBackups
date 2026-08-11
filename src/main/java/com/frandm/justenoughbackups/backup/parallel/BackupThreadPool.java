package com.frandm.justenoughbackups.backup.parallel;

import com.frandm.justenoughbackups.config.BackupConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class BackupThreadPool {
    private static volatile ExecutorService executor;
    private static volatile int activeThreadCount = -1;

    private BackupThreadPool() {
    }

    public static void shutdownNow() {
        ExecutorService existing = executor;
        if (existing != null) {
            existing.shutdownNow();
        }
    }

    public static synchronized ExecutorService getExecutor() {
        int targetThreads = Math.clamp(
                BackupConfig.get().threadCount,
                1,
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );

        if (executor == null || executor.isShutdown() || activeThreadCount != targetThreads) {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
            }
            activeThreadCount = targetThreads;
            AtomicInteger counter = new AtomicInteger(1);
            executor = Executors.newFixedThreadPool(targetThreads, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "JEB-Worker-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                    return thread;
                }
            });
        }
        return executor;
    }
}
