package com.frandm.advancedbackups.backup;

import com.frandm.advancedbackups.config.BackupConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class BackupPermissions {
    private BackupPermissions() {
    }

    public static boolean hasConfiguredPermission(CommandSourceStack source) {
        PermissionLevel level = PermissionLevel.byId(BackupConfig.get().commandPermissionLevel);
        return source.permissions().hasPermission(new Permission.HasCommandLevel(level));
    }

    public static boolean hasConfiguredPermission(ServerPlayer player) {
        return hasConfiguredPermission(player.createCommandSourceStack());
    }
}
