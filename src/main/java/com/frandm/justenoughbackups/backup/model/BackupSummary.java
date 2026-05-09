package com.frandm.justenoughbackups.backup.model;

public record BackupSummary(
        String id,
        String displayName,
        BackupType type,
        String createdAt,
        String worldName,
        String reason,
        String baseBackupId,
        long includedBytes,
        int includedFiles,
        boolean restorable,
        boolean canDelete,
        String deleteBlockedReason
) {
}
