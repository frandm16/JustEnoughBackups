package com.frandm.advancedbackups.backup.progress;

import com.frandm.advancedbackups.backup.model.BackupType;

public record BackupProgress(
        String backupId,
        BackupType type,
        String reason,
        long bytesWritten,
        long totalBytes,
        int filesWritten,
        int totalFiles,
        BackupProgressState state
) {
}
