package com.frandm.justenoughbackups.backup.progress;

@FunctionalInterface
public interface BackupProgressListener {
    void onProgress(BackupProgress progress);

    default void onActivity() {
    }

    default void commit(CommitAction action) throws java.io.IOException {
        action.run();
    }

    @FunctionalInterface
    interface CommitAction {
        void run() throws java.io.IOException;
    }

    static BackupProgressListener noop() {
        return progress -> {
        };
    }
}
