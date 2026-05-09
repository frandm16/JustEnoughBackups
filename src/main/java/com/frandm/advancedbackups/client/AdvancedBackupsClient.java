package com.frandm.advancedbackups.client;

import com.frandm.advancedbackups.WorldBackupMod;
import com.frandm.advancedbackups.backup.progress.BackupProgressPayload;
import com.frandm.advancedbackups.client.input.BackupKeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public final class AdvancedBackupsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                BackupProgressPayload.TYPE,
                (payload, context) -> BackupProgressHud.update(payload)
        );
        BackupUiClient.registerReceivers();
        BackupKeyBindings.register();
        HudElementRegistry.addFirst(
                Identifier.fromNamespaceAndPath(WorldBackupMod.MOD_ID, "backup_progress_hud"),
                BackupProgressHud::render
        );
    }
}
