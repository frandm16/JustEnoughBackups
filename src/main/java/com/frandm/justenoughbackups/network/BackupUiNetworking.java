package com.frandm.justenoughbackups.network;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.BackupPermissions;
import com.frandm.justenoughbackups.backup.BackupService;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;
import com.frandm.justenoughbackups.text.ServerTranslations;
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
            send(player, payload, false, false, "text.justenoughbackups.backup_ui.no_permission", List.of(), List.of());
            return;
        }

        try {
            switch (payload.action()) {
                case LIST -> sendList(server, player, payload, true, "text.justenoughbackups.backup_ui.refreshed");
                case CREATE -> createBackup(payload, server, player);
                case RENAME -> {
                    BackupService.renameBackup(server, payload.backupId(), payload.value());
                    sendList(server, player, payload, true, "text.justenoughbackups.backup_ui.renamed");
                }
                case DELETE -> {
                    BackupService.deleteBackup(server, payload.backupId());
                    sendList(server, player, payload, true, "text.justenoughbackups.backup_ui.deleted");
                }
                case RESTORE -> restoreBackup(payload, server, player);
            }
        } catch (IOException | RuntimeException exception) {
            WorldBackupMod.LOGGER.warn("Backup UI action failed: {}", payload.action(), exception);
            sendFailure(server, player, payload, "text.justenoughbackups.backup_ui.failed", exception);
        }
    }

    private static void createBackup(BackupUiRequestPayload payload, MinecraftServer server, ServerPlayer player) {
        BackupService.createBackup(server, payload.backupType(), "manual", payload.value())
                .thenAccept(manifest -> server.execute(() -> sendList(server, player, payload, true, "message.justenoughbackups.backup_completed", BackupStorage.displayName(manifest))))
                .exceptionally(exception -> {
                    WorldBackupMod.LOGGER.error("Backup UI create failed.", exception);
                    server.execute(() -> sendFailure(server, player, payload, "text.justenoughbackups.backup_ui.backup_failed", exception));
                    return null;
                });
        sendList(server, player, payload, true, "message.justenoughbackups.backup_started", "manual", payload.backupType().commandName());
    }

    private static void restoreBackup(BackupUiRequestPayload payload, MinecraftServer server, ServerPlayer player) {
        BackupService.restoreBackupById(server, payload.backupId())
                .thenAccept(restore -> server.execute(() -> {
                    send(player, payload, true, true, "message.justenoughbackups.restore_prepared", List.of(restore.backupId()), List.of());
                    server.halt(false);
                }))
                .exceptionally(exception -> {
                    WorldBackupMod.LOGGER.error("Backup UI restore failed.", exception);
                    server.execute(() -> sendFailure(server, player, payload, "text.justenoughbackups.backup_ui.restore_failed", exception));
                    return null;
                });
        sendList(server, player, payload, true, "message.justenoughbackups.restore_started", payload.backupId());
    }

    private static void sendList(MinecraftServer server, ServerPlayer player, BackupUiRequestPayload request, boolean success, String messageKey, String... args) {
        try {
            List<BackupUiBackup> backups = BackupService.listBackupSummaries(server).stream()
                    .map(BackupUiBackup::new)
                    .filter(backup -> isSendable(backup, server))
                    .toList();
            send(player, request, true, success, messageKey, List.of(args), backups);
        } catch (IOException exception) {
            sendFailure(player, request, "text.justenoughbackups.backup_ui.failed", exception);
        }
    }

    private static void send(ServerPlayer player, BackupUiRequestPayload request, boolean permitted, boolean success, String messageKey, List<String> messageArgs, List<BackupUiBackup> backups) {
        if (ServerPlayNetworking.canSend(player, BackupUiResponsePayload.TYPE)) {
            ServerPlayNetworking.send(player, new BackupUiResponsePayload(
                    request.requestId(),
                    permitted,
                    success,
                    ServerTranslations.text(messageKey, messageArgs.toArray()),
                    backups
            ));
        }
    }

    private static void sendFailure(MinecraftServer server, ServerPlayer player, BackupUiRequestPayload request, String defaultMessageKey, Throwable throwable) {
        try {
            List<BackupUiBackup> backups = BackupService.listBackupSummaries(server).stream()
                    .map(BackupUiBackup::new)
                    .filter(backup -> isSendable(backup, server))
                    .toList();
            sendFailure(player, request, defaultMessageKey, throwable, backups);
        } catch (IOException exception) {
            sendFailure(player, request, defaultMessageKey, exception, List.of());
        }
    }

    private static void sendFailure(ServerPlayer player, BackupUiRequestPayload request, String defaultMessageKey, Throwable throwable) {
        sendFailure(player, request, defaultMessageKey, throwable, List.of());
    }

    private static void sendFailure(ServerPlayer player, BackupUiRequestPayload request, String defaultMessageKey, Throwable throwable, List<BackupUiBackup> backups) {
        Throwable root = rootCause(throwable);
        send(player, request, true, false, defaultMessageKey, List.of(rootMessage(root)), backups);
    }

    private static String rootMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isSendable(BackupUiBackup backup, MinecraftServer server) {
        if (backup == null) {
            WorldBackupMod.LOGGER.warn("Skipping null backup entry in UI response.");
            return false;
        }
        if (backup.id() == null || backup.id().isBlank()) {
            WorldBackupMod.LOGGER.warn("Skipping backup with missing id in UI response for world {}.", server.getWorldData().getLevelName());
            return false;
        }
        if (backup.type() == null) {
            WorldBackupMod.LOGGER.warn("Skipping backup {} with missing type in UI response.", backup.id());
            return false;
        }
        return true;
    }
}
