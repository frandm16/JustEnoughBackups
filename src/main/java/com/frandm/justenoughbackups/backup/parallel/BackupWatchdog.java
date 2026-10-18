package com.frandm.justenoughbackups.backup.parallel;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.progress.BackupProgress;
import com.frandm.justenoughbackups.backup.progress.BackupProgressListener;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupWatchdog {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final long HEARTBEAT_MILLIS = 1_000L;

    private final BackupProgressListener delegate;
    private final long stallNanos;
    private final Thread monitor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean fired = new AtomicBoolean(false);
    private final Object commitLock = new Object();
    private volatile boolean committed;

    private volatile BackupProgress lastProgress;
    private volatile long lastAdvanceNanos = System.nanoTime();
    private volatile long lastHeartbeatMillis = System.currentTimeMillis();
    private long previousBytes = -1L;
    private long previousFiles = -1L;

    public BackupWatchdog(BackupProgressListener delegate) {
        this(delegate, DEFAULT_TIMEOUT_SECONDS);
    }

    public BackupWatchdog(BackupProgressListener delegate, int timeoutSeconds) {
        this.delegate = delegate;
        this.stallNanos = Math.multiplyExact((long) Math.clamp(timeoutSeconds, 1, 86_400), 1_000_000_000L);
        this.monitor = new Thread(this::run, "JEB-Watchdog");
        this.monitor.setDaemon(true);
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            monitor.start();
        }
    }

    public void stop() {
        started.set(false);
        monitor.interrupt();
    }


    public BackupProgressListener listener() {
        return new BackupProgressListener() {
            @Override
            public void onProgress(BackupProgress progress) {
                if (!committed && (fired.get() || BackupStorage.isBackupAborted())) {
                    throw new TimeoutException();
                }
                track(progress);
                delegate.onProgress(progress);
            }

            @Override
            public void onActivity() {
                if (fired.get() || BackupStorage.isBackupAborted()) {
                    throw new TimeoutException();
                }
                recordActivity();
            }

            @Override
            public void commit(CommitAction action) throws java.io.IOException {
                synchronized (commitLock) {
                    if (fired.get() || BackupStorage.isBackupAborted()) {
                        throw new TimeoutException();
                    }
                    action.run();
                    committed = true;
                }
            }
        };
    }

    private void track(BackupProgress progress) {
        lastProgress = progress;
        long now = System.currentTimeMillis();
        if (progress.bytesWritten() != previousBytes || progress.filesWritten() != previousFiles) {
            lastAdvanceNanos = System.nanoTime();
            lastHeartbeatMillis = now;
        }
        previousBytes = progress.bytesWritten();
        previousFiles = progress.filesWritten();
    }

    private void recordActivity() {
        lastAdvanceNanos = System.nanoTime();
    }

    private void run() {
        while (!fired.get() && !committed && started.get()) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            long now = System.currentTimeMillis();
            if (System.nanoTime() - lastAdvanceNanos > stallNanos) {
                fire();
            } else if (now - lastHeartbeatMillis >= HEARTBEAT_MILLIS) {
                BackupProgress progress = lastProgress;
                if (progress != null) {
                    lastHeartbeatMillis = now;
                    delegate.onProgress(progress);
                }
            }
        }
    }

    private void fire() {
        synchronized (commitLock) {
            if (committed || !fired.compareAndSet(false, true)) {
                return;
            }
        }
        WorldBackupMod.LOGGER.error(
                "Backup watchdog fired: no progress for a while. Dumping all threads for diagnosis."
        );
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            StringBuilder stack = new StringBuilder();
            stack.append("Thread: ").append(entry.getKey().getName())
                    .append(" (daemon=").append(entry.getKey().isDaemon())
                    .append(", state=").append(entry.getKey().getState()).append(')');
            for (StackTraceElement element : entry.getValue()) {
                stack.append(System.lineSeparator()).append("    at ").append(element);
            }
            WorldBackupMod.LOGGER.error(stack.toString());
        }
        BackupStorage.abortActiveBackup();
    }

    public static final class TimeoutException extends RuntimeException {
        public TimeoutException() {
            super("Backup aborted by the watchdog after a timeout.");
        }
    }
}
