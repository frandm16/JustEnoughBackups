package com.frandm.justenoughbackups.backup;

import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public final class BackupPaths {
    private BackupPaths() {
    }

    public static Path worldBackupDir(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        return BackupConfig.get().resolveBackupRoot().resolve(worldDirectoryName(server, worldPath));
    }

    public static String worldName(MinecraftServer server, Path worldPath) {
        String levelName = server.getWorldData().getLevelName();
        if (levelName != null && !levelName.isBlank()) {
            return levelName;
        }

        return worldName(worldPath);
    }

    public static String worldDirectoryName(MinecraftServer server, Path worldPath) {
        return cleanWorldName(worldName(server, worldPath));
    }

    public static String worldName(Path worldPath) {
        Path fileName = worldPath.getFileName();
        if (fileName == null) {
            return "world";
        }

        String name = fileName.toString();
        return name.isBlank() || ".".equals(name) || "..".equals(name) ? "world" : name;
    }

    private static String cleanWorldName(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned) ? "world" : cleaned;
    }
}
