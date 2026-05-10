package com.frandm.justenoughbackups.client.screen.config;

import com.frandm.justenoughbackups.backup.progress.BackupProgressState;

public enum ConfigPreviewState {
    RUNNING("screen.justenoughbackups.config.preview_state.running", BackupProgressState.RUNNING),
    COMPLETED("screen.justenoughbackups.config.preview_state.done", BackupProgressState.COMPLETED),
    FAILED("screen.justenoughbackups.config.preview_state.failed", BackupProgressState.FAILED);

    private final String key;
    private final BackupProgressState progressState;

    ConfigPreviewState(String key, BackupProgressState progressState) {
        this.key = key;
        this.progressState = progressState;
    }

    public String key() {
        return key;
    }

    public BackupProgressState progressState() {
        return progressState;
    }
}
