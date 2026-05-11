package com.frandm.justenoughbackups.backup.progress;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.model.BackupType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BackupProgressPayload(
        String backupId,
        BackupType backupType,
        String reason,
        long bytesWritten,
        long totalBytes,
        int filesWritten,
        int totalFiles,
        BackupProgressState state
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackupProgressPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WorldBackupMod.MOD_ID, "backup_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackupProgressPayload> CODEC = StreamCodec.of(
            BackupProgressPayload::write,
            BackupProgressPayload::read
    );

    public BackupProgressPayload(BackupProgress progress) {
        this(
                progress.backupId(),
                progress.type(),
                progress.reason(),
                progress.bytesWritten(),
                progress.totalBytes(),
                progress.filesWritten(),
                progress.totalFiles(),
                progress.state()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, BackupProgressPayload payload) {
        buffer.writeUtf(payload.backupId());
        buffer.writeEnum(payload.backupType());
        buffer.writeUtf(payload.reason());
        buffer.writeLong(payload.bytesWritten());
        buffer.writeLong(payload.totalBytes());
        buffer.writeInt(payload.filesWritten());
        buffer.writeInt(payload.totalFiles());
        buffer.writeEnum(payload.state());
    }

    private static BackupProgressPayload read(RegistryFriendlyByteBuf buffer) {
        return new BackupProgressPayload(
                buffer.readUtf(),
                buffer.readEnum(BackupType.class),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readEnum(BackupProgressState.class)
        );
    }
}
