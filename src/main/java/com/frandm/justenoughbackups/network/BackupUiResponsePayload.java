package com.frandm.justenoughbackups.network;

import com.frandm.justenoughbackups.WorldBackupMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.List;

public record BackupUiResponsePayload(
        String requestId,
        boolean permitted,
        boolean success,
        String messageText,
        List<BackupUiBackup> backups
) implements CustomPacketPayload {
    public static final Type<BackupUiResponsePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(WorldBackupMod.MOD_ID, "backup_ui_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackupUiResponsePayload> CODEC = StreamCodec.of(
            BackupUiResponsePayload::write,
            BackupUiResponsePayload::read
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public Component message() {
        return messageText == null || messageText.isBlank()
                ? Component.empty()
                : Component.literal(messageText);
    }

    private static void write(RegistryFriendlyByteBuf buffer, BackupUiResponsePayload payload) {
        buffer.writeUtf(value(payload.requestId()));
        buffer.writeBoolean(payload.permitted());
        buffer.writeBoolean(payload.success());
        buffer.writeUtf(value(payload.messageText()));
        buffer.writeInt(payload.backups().size());
        for (BackupUiBackup backup : payload.backups()) {
            BackupUiBackup.write(buffer, backup);
        }
    }

    private static BackupUiResponsePayload read(RegistryFriendlyByteBuf buffer) {
        String requestId = buffer.readUtf();
        boolean permitted = buffer.readBoolean();
        boolean success = buffer.readBoolean();
        String messageText = buffer.readUtf();
        int size = buffer.readInt();
        List<BackupUiBackup> backups = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            backups.add(BackupUiBackup.read(buffer));
        }
        return new BackupUiResponsePayload(requestId, permitted, success, messageText, List.copyOf(backups));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
