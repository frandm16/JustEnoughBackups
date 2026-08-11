package com.frandm.justenoughbackups.backup.model;

import java.nio.file.Path;
import java.util.Map;

public record RestoreIntent(
        int version,
        String backupId,
        Path worldPath,
        Path stagingPath,
        Path oldWorldPath,
        RestoreState state,
        BackupIntegrityMode integrityMode,
        boolean strictSnapshotVerification,
        Map<String, BackupManifest.FileState> snapshot
) {
    public static final int CURRENT_VERSION = 1;

    public enum RestoreState {
        PREPARED,
        OLD_MOVED,
        STAGING_INSTALLED,
        APPLIED,
        FAILED
    }

    public RestoreIntent withState(RestoreState newState) {
        return new RestoreIntent(version, backupId, worldPath, stagingPath, oldWorldPath, newState, integrityMode, strictSnapshotVerification, snapshot);
    }
}
