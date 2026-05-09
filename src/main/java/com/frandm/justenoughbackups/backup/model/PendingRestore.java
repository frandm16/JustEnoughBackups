package com.frandm.justenoughbackups.backup.model;

import java.nio.file.Path;
import java.util.Map;

public record PendingRestore(
        String backupId,
        Path backupDir,
        Path worldPath,
        Path tempRestore,
        String worldName,
        String worldDirectoryName,
        BackupIntegrityMode integrityMode,
        boolean strictSnapshotVerification,
        Map<String, BackupManifest.FileState> snapshot
) {
}
