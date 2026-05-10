package com.frandm.justenoughbackups.client;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.progress.BackupProgressPayload;
import com.frandm.justenoughbackups.client.input.BackupKeyBindings;
import com.frandm.justenoughbackups.client.net.BackupUiClient;
import com.frandm.justenoughbackups.client.ui.popup.BackupProgressHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public final class JEBClient implements ClientModInitializer {
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
