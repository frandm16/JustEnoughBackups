package com.frandm.justenoughbackups.backup;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

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
            return cleanWorldName(levelId);
        }

        String fallback = pathName(worldPath);
        WorldBackupMod.LOGGER.debug("Falling back to world path for backup directory name: {}", fallback);
        return cleanWorldName(fallback);
    }

    public static String worldName(Path worldPath) {
        return pathName(worldPath);
    }

    private static String cleanWorldName(String name) {
        if (name == null) {
            return "world";
        }

        String cleaned = name.strip()
                .replaceAll("[\\x00-\\x1F]", "_")
                .replaceAll("[<>:\"/\\\\|?*]", "_");

        return cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned) ? "world" : cleaned;
    }

    private static String levelId(MinecraftServer server) {
        try {
            for (Class<?> type = server.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!LevelStorageSource.LevelStorageAccess.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object storageAccess = field.get(server);
                    if (storageAccess == null) {
                        continue;
                    }

                    Method getLevelId = storageAccess.getClass().getMethod("getLevelId");
                    Object levelId = getLevelId.invoke(storageAccess);
                    if (levelId instanceof String value && !value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (ReflectiveOperationException | SecurityException exception) {
            WorldBackupMod.LOGGER.debug("Unable to read world levelId from MinecraftServer storageSource; using path fallback.", exception);
        }
        return null;
    }

    private static String pathName(Path worldPath) {
        if (worldPath == null) {
            WorldBackupMod.LOGGER.warn("World path was null. Using fallback {}.", DEFAULT_WORLD_NAME);
            return DEFAULT_WORLD_NAME;
        }

        String direct = validPathPart(worldPath.getFileName());
        if (direct != null) {
            return direct;
        }

        Path normalized = worldPath.toAbsolutePath().normalize();
        String normalizedName = validPathPart(normalized.getFileName());
        if (normalizedName != null) {
            return normalizedName;
        }

        Path parent = normalized.getParent();
        String parentName = parent == null ? null : validPathPart(parent.getFileName());
        if (parentName != null) {
            WorldBackupMod.LOGGER.debug("Using parent directory name {} for world path {}.", parentName, worldPath);
            return parentName;
        }

        WorldBackupMod.LOGGER.warn("Unable to resolve world folder name from world path {}. Using fallback {}.", worldPath, DEFAULT_WORLD_NAME);
        return DEFAULT_WORLD_NAME;
    }

    private static String validPathPart(Path fileName) {
        if (fileName == null) {
            return null;
        }
        String name = fileName.toString();
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            return null;
        }
        return name;
    }
}
