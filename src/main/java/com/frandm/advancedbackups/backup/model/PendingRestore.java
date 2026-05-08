package com.frandm.advancedbackups.backup.model;

import java.nio.file.Path;
import java.util.Map;

public record PendingRestore(
        String backupId,
        Path backupDir,
        Path worldPath,
        Path tempRestore,
        Path previousWorld,
        Map<String, BackupManifest.FileState> snapshot
) {
}
