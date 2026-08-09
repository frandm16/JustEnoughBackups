package com.frandm.justenoughbackups.client.screen.config;

import com.frandm.justenoughbackups.backup.progress.BackupProgressState;
import com.frandm.justenoughbackups.backup.progress.BackupProgressPhase;

public enum ConfigPreviewState {
    SCANNING("screen.justenoughbackups.config.preview_state.scanning", BackupProgressPhase.SCANNING, BackupProgressState.RUNNING),
    RUNNING("screen.justenoughbackups.config.preview_state.running", BackupProgressPhase.COPYING, BackupProgressState.RUNNING),
    COMPLETED("screen.justenoughbackups.config.preview_state.done", BackupProgressPhase.COPYING, BackupProgressState.COMPLETED),
    FAILED("screen.justenoughbackups.config.preview_state.failed", BackupProgressPhase.COPYING, BackupProgressState.FAILED);

    private final String key;
    private final BackupProgressPhase phase;
    private final BackupProgressState progressState;

    ConfigPreviewState(String key, BackupProgressPhase phase, BackupProgressState progressState) {
        this.key = key;
        this.phase = phase;
        this.progressState = progressState;
    }

    public String key() {
        return key;
    }

    public BackupProgressState progressState() {
        return progressState;
    }

    public BackupProgressPhase phase() {
        return phase;
    }
}
