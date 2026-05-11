package com.frandm.justenoughbackups.backup.progress;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class BackupProgressBroadcaster {
    private BackupProgressBroadcaster() {
    }

    public static void registerPayloadType() {
        PayloadTypeRegistry.playS2C().register(BackupProgressPayload.TYPE, BackupProgressPayload.CODEC);
    }

    public static void broadcast(MinecraftServer server, BackupProgress progress) {
        BackupProgressPayload payload = new BackupProgressPayload(progress);
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ServerPlayNetworking.canSend(player, BackupProgressPayload.TYPE)) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        });
    }
}
