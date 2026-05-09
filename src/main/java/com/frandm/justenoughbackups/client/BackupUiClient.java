package com.frandm.justenoughbackups.client;

import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.client.screen.BackupManagementScreen;
import com.frandm.justenoughbackups.network.BackupUiAction;
import com.frandm.justenoughbackups.network.BackupUiRequestPayload;
import com.frandm.justenoughbackups.network.BackupUiResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.UUID;

public final class BackupUiClient {
    private BackupUiClient() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(BackupUiResponsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof BackupUiResponseConsumer screen) {
                        screen.handleResponse(payload);
                    }
                })
        );
    }

    public static void openScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        minecraft.setScreen(new BackupManagementScreen());
    }

    public static void requestList() {
        send(BackupUiAction.LIST, BackupType.FULL, "", "");
    }

    public static void createBackup(BackupType type) {
        send(BackupUiAction.CREATE, type, "", "");
    }

    public static void renameBackup(String backupId, String displayName) {
        send(BackupUiAction.RENAME, BackupType.FULL, backupId, displayName);
    }

    public static void deleteBackup(String backupId) {
        send(BackupUiAction.DELETE, BackupType.FULL, backupId, "");
    }

    public static void restoreBackup(String backupId) {
        send(BackupUiAction.RESTORE, BackupType.FULL, backupId, "");
    }

    private static void send(BackupUiAction action, BackupType type, String backupId, String value) {
        if (!canSendRequests()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof BackupUiResponseConsumer screen) {
                screen.setStatus(false, "This server does not support the Advanced Backups UI channel.");
            }
            return;
        }

        ClientPlayNetworking.send(new BackupUiRequestPayload(
                UUID.randomUUID().toString(),
                action,
                type,
                backupId,
                value
        ));
    }

    private static boolean canSendRequests() {
        try {
            return ClientPlayNetworking.canSend(BackupUiRequestPayload.TYPE);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
