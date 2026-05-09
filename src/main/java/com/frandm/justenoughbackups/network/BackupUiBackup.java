package com.frandm.justenoughbackups.network;

import com.frandm.justenoughbackups.backup.model.BackupSummary;
import com.frandm.justenoughbackups.backup.model.BackupType;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record BackupUiBackup(
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
    public BackupUiBackup(BackupSummary summary) {
        this(
                summary.id(),
                summary.displayName(),
                summary.type(),
                summary.createdAt(),
                summary.worldName(),
                summary.reason(),
                summary.baseBackupId(),
                summary.includedBytes(),
                summary.includedFiles(),
                summary.restorable(),
                summary.canDelete(),
                summary.deleteBlockedReason()
        );
    }

    static void write(RegistryFriendlyByteBuf buffer, BackupUiBackup backup) {
        buffer.writeUtf(value(backup.id()));
        buffer.writeUtf(value(backup.displayName()));
        buffer.writeEnum(backup.type() == null ? BackupType.FULL : backup.type());
        buffer.writeUtf(value(backup.createdAt()));
        buffer.writeUtf(value(backup.worldName()));
        buffer.writeUtf(value(backup.reason()));
        buffer.writeUtf(value(backup.baseBackupId()));
        buffer.writeLong(backup.includedBytes());
        buffer.writeInt(backup.includedFiles());
        buffer.writeBoolean(backup.restorable());
        buffer.writeBoolean(backup.canDelete());
        buffer.writeUtf(value(backup.deleteBlockedReason()));
    }

    static BackupUiBackup read(RegistryFriendlyByteBuf buffer) {
        return new BackupUiBackup(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readEnum(BackupType.class),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf()
        );
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
