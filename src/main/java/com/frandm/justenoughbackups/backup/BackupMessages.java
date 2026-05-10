package com.frandm.justenoughbackups.backup;

import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

public final class BackupMessages {
    private BackupMessages() {
    }

    public static void broadcastBackupStarted(MinecraftServer server, BackupType type, String reason) {
        broadcast(server, Component.translatable("message.justenoughbackups.backup_started", reason, type.commandName()));
    }

    public static void broadcastBackupCompleted(MinecraftServer server, BackupManifest manifest) {
        broadcast(server, Component.translatable(
                "message.justenoughbackups.backup_completed",
                BackupStorage.displayName(manifest)
        ));
    }

    public static void broadcastBackupFailed(MinecraftServer server, BackupType type, String reason) {
        broadcast(server, Component.translatable("message.justenoughbackups.backup_failed", reason, type.commandName()));
    }

    public static void broadcastRestoreStarted(MinecraftServer server, String backupId) {
        broadcast(server, Component.translatable("message.justenoughbackups.restore_started", backupId));
    }

    public static void broadcastRestorePrepared(MinecraftServer server, String backupId) {
        broadcast(server, Component.translatable("message.justenoughbackups.restore_prepared", backupId));
    }

    public static void broadcastRestoreFailed(MinecraftServer server, String reason) {
        broadcast(server, Component.translatable("message.justenoughbackups.restore_failed", reason));
    }

    public static MutableComponent withTitle(Component message) {
        return Component.translatable("message.justenoughbackups.title")
                .withStyle(style -> style.withColor(0x6F00FF))
                .append(message.copy().withStyle(ChatFormatting.WHITE));
    }

    private static void broadcast(MinecraftServer server, Component message) {
        BackupMessageChannel channel = BackupConfig.get().messageChannel;
        if (channel == BackupMessageChannel.OFF) {
            return;
        }

        MutableComponent formatted = withTitle(message);
        Runnable send = () -> server.getPlayerList().broadcastSystemMessage(
                formatted,
                channel == BackupMessageChannel.ACTION_BAR
        );
        if (server.isSameThread()) {
            send.run();
        } else {
            server.execute(send);
        }
    }
}
