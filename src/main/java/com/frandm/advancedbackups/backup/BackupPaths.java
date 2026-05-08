package com.frandm.advancedbackups.backup;

import com.frandm.advancedbackups.config.BackupConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public final class BackupPaths {
    private BackupPaths() {
    }

    public static Path worldBackupDir(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        return BackupConfig.get().resolveBackupRoot().resolve(worldName(worldPath));
    }

    public static String worldName(Path worldPath) {
        Path fileName = worldPath.getFileName();
        if (fileName == null) {
            return "world";
        }

        String cleaned = fileName.toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "world" : cleaned;
    }
}
