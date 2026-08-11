package com.frandm.justenoughbackups.backup.progress;

import com.frandm.justenoughbackups.backup.model.BackupType;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RestoreProgressTracker {
    private static final long MIN_UPDATE_INTERVAL_MILLIS = 500L;

    private final String backupId;
    private final BackupType type;
    private final String reason;
    private final long totalBytes;
    private final int totalFiles;
    private final BackupProgressListener listener;
    private final AtomicLong bytes = new AtomicLong();
    private final AtomicInteger files = new AtomicInteger();
    private final AtomicLong lastUpdateMillis = new AtomicLong();
    private volatile int lastPercent = -1;

    public RestoreProgressTracker(
            String backupId,
            BackupType type,
            String reason,
            long totalBytes,
            int totalFiles,
            BackupProgressListener listener
    ) {
        this.backupId = backupId;
        this.type = type;
        this.reason = reason;
        this.totalBytes = Math.max(0L, totalBytes);
        this.totalFiles = Math.max(0, totalFiles);
        this.listener = listener;
    }

    public void start() {
        emit(BackupProgressState.STARTED, true);
    }

    public void advance(long count) {
        bytes.addAndGet(Math.max(0L, count));
        emit(BackupProgressState.RUNNING, false);
    }

    public void fileCompleted() {
        files.incrementAndGet();
        emit(BackupProgressState.RUNNING, false);
    }

    public void complete() {
        bytes.set(totalBytes);
        files.set(totalFiles);
        emit(BackupProgressState.RUNNING, true);
    }

    private void emit(BackupProgressState state, boolean force) {
        long now = System.currentTimeMillis();
        long written = bytes.get();
        int percent = totalBytes <= 0L ? 100 : (int) Math.min(100L, (written * 100L) / totalBytes);
        if (!force && now - lastUpdateMillis.get() < MIN_UPDATE_INTERVAL_MILLIS && percent == lastPercent) {
            return;
        }

        lastUpdateMillis.set(now);
        lastPercent = percent;
        listener.onProgress(new BackupProgress(
                backupId,
                type,
                reason,
                BackupProgressPhase.COPYING,
                written,
                totalBytes,
                files.get(),
                totalFiles,
                state
        ));
    }
}
