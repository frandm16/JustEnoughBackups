package com.frandm.justenoughbackups.backup.parallel;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.progress.BackupProgress;
import com.frandm.justenoughbackups.backup.progress.BackupProgressListener;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupWatchdog {
    private static final long STALL_MILLIS = 120_000L;
    private static final long HEARTBEAT_MILLIS = 1_000L;

    private final BackupProgressListener delegate;
    private final Thread monitor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean fired = new AtomicBoolean(false);

    private volatile BackupProgress lastProgress;
    private volatile long lastAdvanceMillis = System.currentTimeMillis();
    private volatile long lastHeartbeatMillis = System.currentTimeMillis();
    private long previousBytes = -1L;
    private long previousFiles = -1L;

    public BackupWatchdog(BackupProgressListener delegate) {
        this.delegate = delegate;
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
        return progress -> {
            if (fired.get() || BackupStorage.isBackupAborted()) {
                throw new TimeoutException();
            }
            track(progress);
            delegate.onProgress(progress);
        };
    }

    private void track(BackupProgress progress) {
        lastProgress = progress;
        long now = System.currentTimeMillis();
        if (progress.bytesWritten() != previousBytes || progress.filesWritten() != previousFiles) {
            lastAdvanceMillis = now;
            lastHeartbeatMillis = now;
        }
        previousBytes = progress.bytesWritten();
        previousFiles = progress.filesWritten();
    }

    private void run() {
        while (!fired.get() && started.get()) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastAdvanceMillis > STALL_MILLIS) {
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
        if (!fired.compareAndSet(false, true)) {
            return;
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
