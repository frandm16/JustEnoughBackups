package com.frandm.justenoughbackups.client.net;

import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.client.screen.backup.BackupManagementScreen;
import com.frandm.justenoughbackups.network.BackupUiAction;
import com.frandm.justenoughbackups.network.BackupUiRequestPayload;
import com.frandm.justenoughbackups.network.BackupUiResponsePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class BackupUiClient {
    private static final int RETURN_TO_TITLE_DELAY_TICKS = 10;
    private static int pendingReturnToTitleTicks = -1;
    private static Component pendingReturnMessage = Component.empty();

    private BackupUiClient() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(BackupUiResponsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.returnToTitle()) {
                        beginReturnToTitle(payload.message());
                    }
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

    public static void tick() {
        if (pendingReturnToTitleTicks < 0) {
            return;
        }

        if (pendingReturnToTitleTicks == RETURN_TO_TITLE_DELAY_TICKS) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new GenericMessageScreen(pendingReturnMessage));
        }

        pendingReturnToTitleTicks--;
        if (pendingReturnToTitleTicks <= 0) {
            disconnectToTitle();
        }
    }

    private static void send(BackupUiAction action, BackupType type, String backupId, String value) {
        if (!canSendRequests()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof BackupUiResponseConsumer screen) {
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

    private static void beginReturnToTitle(Component message) {
        pendingReturnMessage = message == null || message.getString().isBlank()
                ? Component.translatable("message.justenoughbackups.restore_prepared", "")
                : message;
        pendingReturnToTitleTicks = RETURN_TO_TITLE_DELAY_TICKS;
    }

    private static void disconnectToTitle() {
        pendingReturnToTitleTicks = -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            minecraft.setScreen(new TitleScreen());
            return;
        }
        minecraft.disconnect(new TitleScreen());
    }
}
