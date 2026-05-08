package com.frandm.advancedbackups.backup.progress;

@FunctionalInterface
public interface BackupProgressListener {
    void onProgress(BackupProgress progress);

    static BackupProgressListener noop() {
        return progress -> {
        };
    }
}
