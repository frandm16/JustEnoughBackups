package com.frandm.justenoughbackups.backup.progress;

import com.frandm.justenoughbackups.backup.model.BackupType;

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
