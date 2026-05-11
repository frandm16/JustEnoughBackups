package com.frandm.justenoughbackups.backup;

import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class BackupPermissions {
    private BackupPermissions() {
    }

    public static boolean hasConfiguredPermission(CommandSourceStack source) {
        return source.hasPermission(BackupConfig.get().commandPermissionLevel);
    }

    public static boolean hasConfiguredPermission(ServerPlayer player) {
        return hasConfiguredPermission(player.createCommandSourceStack());
    }
}
