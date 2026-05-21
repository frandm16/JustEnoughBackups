package com.frandm.justenoughbackups.backup;

import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class BackupPermissions {
    private BackupPermissions() {
    }

    public static boolean hasConfiguredPermission(CommandSourceStack source) {
        return hasConfiguredPermissionLevel(source, BackupConfig.get().commandPermissionLevel);
    }

    public static boolean hasConfiguredPermission(ServerPlayer player) {
        return hasConfiguredPermissionLevel(player.createCommandSourceStack(), BackupConfig.get().commandPermissionLevel);
    }

    private static boolean hasConfiguredPermissionLevel(CommandSourceStack source, int level) {
        try {
            return source.getClass()
                    .getMethod("hasPermission", int.class)
                    .invoke(source, level) instanceof Boolean allowed && allowed;
        } catch (ReflectiveOperationException ignored) {
            // 1.21.11 replaced the integer-based helper, so fall through to the new permission model.
        }

        try {
            Object permissions = source.getClass().getMethod("permissions").invoke(source);
            Object currentLevel = permissions.getClass().getMethod("level").invoke(permissions);
            Class<?> permissionLevelClass = currentLevel.getClass();
            Object requiredLevel = permissionLevelClass.getMethod("byId", int.class).invoke(null, level);

            return permissionLevelClass
                    .getMethod("isEqualOrHigherThan", permissionLevelClass)
                    .invoke(currentLevel, requiredLevel) instanceof Boolean allowed && allowed;
        } catch (ReflectiveOperationException ignored) {
            return level <= 0 || !source.isPlayer();
        }
    }
}
