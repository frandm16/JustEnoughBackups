package com.frandm.justenoughbackups.client.net;

import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.client.screen.backup.BackupManagementScreen;
import com.frandm.justenoughbackups.network.BackupUiAction;
import com.frandm.justenoughbackups.network.BackupUiRequestPayload;
import com.frandm.justenoughbackups.network.BackupUiResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.ref.WeakReference;
import java.util.UUID;

public final class BackupUiClient {
    private static WeakReference<BackupUiResponseConsumer> activeScreen = new WeakReference<>(null);

    private BackupUiClient() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(BackupUiResponsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    BackupUiResponseConsumer screen = activeScreen.get();
                    if (screen != null) {
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
        minecraft.setScreenAndShow(new BackupManagementScreen());
    }

    public static void requestList() {
        send(BackupUiAction.LIST, BackupType.FULL, "", "");
    }

    public static void createBackup(BackupType type, String requestedName) {
        send(BackupUiAction.CREATE, type, "", requestedName);
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
            BackupUiResponseConsumer screen = activeScreen.get();
            if (screen != null) {
                screen.setStatus(false, Component.translatable("text.justenoughbackups.backup_ui.unsupported_channel"));
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

    public static void setActiveScreen(BackupUiResponseConsumer screen) {
        activeScreen = new WeakReference<>(screen);
    }

    public static void clearActiveScreen(BackupUiResponseConsumer screen) {
        BackupUiResponseConsumer current = activeScreen.get();
        if (current == screen) {
            activeScreen.clear();
        }
    }
}
