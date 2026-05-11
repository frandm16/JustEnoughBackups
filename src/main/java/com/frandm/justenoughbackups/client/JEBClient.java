package com.frandm.justenoughbackups.client;

import com.frandm.justenoughbackups.backup.progress.BackupProgressPayload;
import com.frandm.justenoughbackups.client.input.BackupKeyBindings;
import com.frandm.justenoughbackups.client.net.BackupUiClient;
import com.frandm.justenoughbackups.client.ui.popup.BackupProgressHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class JEBClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                BackupProgressPayload.TYPE,
                (payload, context) -> BackupProgressHud.update(payload)
        );

        BackupUiClient.registerReceivers();
        BackupKeyBindings.register();

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            BackupProgressHud.render(drawContext, tickDelta);
        });
    }
}