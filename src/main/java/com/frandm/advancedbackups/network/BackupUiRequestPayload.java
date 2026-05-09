package com.frandm.advancedbackups.network;

import com.frandm.advancedbackups.WorldBackupMod;
import com.frandm.advancedbackups.backup.model.BackupType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BackupUiRequestPayload(
        String requestId,
        BackupUiAction action,
        BackupType backupType,
        String backupId,
        String value
) implements CustomPacketPayload {
    public static final Type<BackupUiRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WorldBackupMod.MOD_ID, "backup_ui_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackupUiRequestPayload> CODEC = StreamCodec.of(
            BackupUiRequestPayload::write,
            BackupUiRequestPayload::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buffer, BackupUiRequestPayload payload) {
        buffer.writeUtf(value(payload.requestId()));
        buffer.writeEnum(payload.action());
        buffer.writeEnum(payload.backupType());
        buffer.writeUtf(value(payload.backupId()));
        buffer.writeUtf(value(payload.value()));
    }

    private static BackupUiRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new BackupUiRequestPayload(
                buffer.readUtf(),
                buffer.readEnum(BackupUiAction.class),
                buffer.readEnum(BackupType.class),
                buffer.readUtf(),
                buffer.readUtf()
        );
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
