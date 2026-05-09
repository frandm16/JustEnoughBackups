package com.frandm.advancedbackups.network;

import com.frandm.advancedbackups.WorldBackupMod;
import com.frandm.advancedbackups.backup.BackupPermissions;
import com.frandm.advancedbackups.backup.BackupService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;

public final class BackupUiNetworking {
    private BackupUiNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.serverboundPlay().register(BackupUiRequestPayload.TYPE, BackupUiRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BackupUiResponsePayload.TYPE, BackupUiResponsePayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(BackupUiRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> handle(payload, context.server(), context.player()))
        );
    }

    private static void handle(BackupUiRequestPayload payload, MinecraftServer server, ServerPlayer player) {
        if (!BackupPermissions.hasConfiguredPermission(player)) {
            send(player, payload, false, false, "You do not have permission to use Advanced Backups.", List.of());
            return;
        }

        try {
            switch (payload.action()) {
                case LIST -> sendList(server, player, payload, true, "Backups refreshed.");
                case CREATE -> createBackup(payload, server, player);
                case RENAME -> {
                    BackupService.renameBackup(server, payload.backupId(), payload.value());
                    sendList(server, player, payload, true, "Backup renamed.");
                }
                case DELETE -> {
                    BackupService.deleteBackup(server, payload.backupId());
                    sendList(server, player, payload, true, "Backup deleted.");
                }
                case RESTORE -> restoreBackup(payload, server, player);
            }
        } catch (IOException | RuntimeException exception) {
            WorldBackupMod.LOGGER.warn("Backup UI action failed: {}", payload.action(), exception);
            sendList(server, player, payload, false, rootMessage(exception));
        }
    }

    private static void createBackup(BackupUiRequestPayload payload, MinecraftServer server, ServerPlayer player) {
        BackupService.createBackup(server, payload.backupType(), "manual")
                .thenAccept(manifest -> server.execute(() -> sendList(server, player, payload, true, "Backup created: " + manifest.id)))
                .exceptionally(exception -> {
                    WorldBackupMod.LOGGER.error("Backup UI create failed.", exception);
                    server.execute(() -> sendList(server, player, payload, false, "Backup failed: " + rootMessage(exception)));
                    return null;
                });
        sendList(server, player, payload, true, "Backup started.");
    }

    private static void restoreBackup(BackupUiRequestPayload payload, MinecraftServer server, ServerPlayer player) {
        BackupService.restoreBackup(server, payload.backupId())
                .thenAccept(restore -> server.execute(() -> {
                    send(player, payload, true, true, "Restore prepared. Stopping server now.", List.of());
                    server.halt(false);
                }))
                .exceptionally(exception -> {
                    WorldBackupMod.LOGGER.error("Backup UI restore failed.", exception);
                    server.execute(() -> sendList(server, player, payload, false, "Restore failed: " + rootMessage(exception)));
                    return null;
                });
        sendList(server, player, payload, true, "Restore started.");
    }

    private static void sendList(MinecraftServer server, ServerPlayer player, BackupUiRequestPayload request, boolean success, String message) {
        try {
            List<BackupUiBackup> backups = BackupService.listBackupSummaries(server).stream()
                    .map(BackupUiBackup::new)
                    .toList();
            send(player, request, true, success, message, backups);
        } catch (IOException exception) {
            send(player, request, true, false, rootMessage(exception), List.of());
        }
    }

    private static void send(ServerPlayer player, BackupUiRequestPayload request, boolean permitted, boolean success, String message, List<BackupUiBackup> backups) {
        if (ServerPlayNetworking.canSend(player, BackupUiResponsePayload.TYPE)) {
            ServerPlayNetworking.send(player, new BackupUiResponsePayload(
                    request.requestId(),
                    permitted,
                    success,
                    message,
                    backups
            ));
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
