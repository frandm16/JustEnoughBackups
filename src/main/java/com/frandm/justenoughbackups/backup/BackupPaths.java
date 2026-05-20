package com.frandm.justenoughbackups.backup;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

public final class BackupPaths {
    private static final String DEFAULT_WORLD_NAME = "Default-World(WORLD NAME NOT FOUND)";

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
        String levelId = levelId(server);
        if (levelId != null && !levelId.isBlank()) {
            return levelId;
        }

        String fallback = worldName(worldPath);
        WorldBackupMod.LOGGER.debug("Falling back to world path for backup directory name: {}", fallback);
        return fallback;
    }

    public static String worldName(Path worldPath) {
        Path fileName = worldPath.getFileName();
        if (fileName == null) {
            WorldBackupMod.LOGGER.warn("Unable to resolve world folder name from world path. Using fallback {}.", DEFAULT_WORLD_NAME);
            return DEFAULT_WORLD_NAME;
        }

        String name = fileName.toString();
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            WorldBackupMod.LOGGER.warn("World path file name was invalid ({}). Using fallback {}.", name, DEFAULT_WORLD_NAME);
            return DEFAULT_WORLD_NAME;
        }
        return name;
    }

    private static String levelId(MinecraftServer server) {
        try {
            Field storageField = MinecraftServer.class.getDeclaredField("storageSource");
            storageField.setAccessible(true);
            Object storageAccess = storageField.get(server);
            if (storageAccess == null) {
                return null;
            }

            Method getLevelId = storageAccess.getClass().getMethod("getLevelId");
            Object levelId = getLevelId.invoke(storageAccess);
            return levelId instanceof String value ? value : null;
        } catch (ReflectiveOperationException | SecurityException exception) {
            WorldBackupMod.LOGGER.debug("Unable to read world levelId from MinecraftServer storageSource; using path fallback.", exception);
            return null;
        }
    }
}
