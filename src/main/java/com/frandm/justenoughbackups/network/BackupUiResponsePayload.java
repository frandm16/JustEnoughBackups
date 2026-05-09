package com.frandm.justenoughbackups.network;

import com.frandm.justenoughbackups.WorldBackupMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record BackupUiResponsePayload(
        String requestId,
        boolean permitted,
        boolean success,
        String messageKey,
        List<String> messageArgs,
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
        return messageKey == null || messageKey.isBlank()
                ? Component.empty()
                : Component.translatable(messageKey, messageArgs.toArray());
    }

    private static void write(RegistryFriendlyByteBuf buffer, BackupUiResponsePayload payload) {
        buffer.writeUtf(value(payload.requestId()));
        buffer.writeBoolean(payload.permitted());
        buffer.writeBoolean(payload.success());
        buffer.writeUtf(value(payload.messageKey()));
        buffer.writeInt(payload.messageArgs().size());
        for (String arg : payload.messageArgs()) {
            buffer.writeUtf(value(arg));
        }
        buffer.writeInt(payload.backups().size());
        for (BackupUiBackup backup : payload.backups()) {
            BackupUiBackup.write(buffer, backup);
        }
    }

    private static BackupUiResponsePayload read(RegistryFriendlyByteBuf buffer) {
        String requestId = buffer.readUtf();
        boolean permitted = buffer.readBoolean();
        boolean success = buffer.readBoolean();
        String messageKey = buffer.readUtf();
        int argSize = buffer.readInt();
        List<String> messageArgs = new ArrayList<>(argSize);
        for (int i = 0; i < argSize; i++) {
            messageArgs.add(buffer.readUtf());
        }
        int size = buffer.readInt();
        List<BackupUiBackup> backups = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            backups.add(BackupUiBackup.read(buffer));
        }
        return new BackupUiResponsePayload(requestId, permitted, success, messageKey, List.copyOf(messageArgs), List.copyOf(backups));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
