package com.frandm.justenoughbackups.backup.progress;

import java.util.Objects;

public final class RemoteUploadProgressListener implements BackupProgressListener {
    private final BackupProgressListener delegate;
    private boolean uploadStarted;

    public RemoteUploadProgressListener(BackupProgressListener delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void onActivity() {
        delegate.onActivity();
    }

    @Override
    public void commit(CommitAction action) throws java.io.IOException {
        delegate.commit(action);
    }

    @Override
    public synchronized void onProgress(BackupProgress progress) {
        if (progress.phase() == BackupProgressPhase.UPLOADING) {
            uploadStarted = true;
        } else if (uploadStarted) {
            return;
        }
        delegate.onProgress(progress);
    }
}
