package com.frandm.advancedbackups;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BackupManager {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private BackupManager() {
    }

    public static CompletableFuture<Path> createManualBackup(MinecraftServer server) {
        server.saveEverything(true, true, true);

        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        String worldName = getWorldName(worldPath);
        Path backupFile = FabricLoader.getInstance().getGameDir()
                .resolve("backups")
                .resolve("advancedbackups")
                .resolve(worldName)
                .resolve("manual-" + LocalDateTime.now().format(FILE_TIME) + ".zip");

        return CompletableFuture.supplyAsync(() -> {
            try {
                zipWorld(worldPath, backupFile);
                WorldBackupMod.LOGGER.info("Backup created: {}", backupFile);
                return backupFile;
            } catch (IOException exception) {
                throw new RuntimeException("Failed to create backup: " + backupFile, exception);
            }
        });
    }

    private static String getWorldName(Path worldPath) {
        Path fileName = worldPath.getFileName();
        if (fileName == null) {
            return "world";
        }

        String cleaned = fileName.toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "world" : cleaned;
    }

    private static void zipWorld(Path worldPath, Path backupFile) throws IOException {
        Files.createDirectories(backupFile.getParent());

        try (OutputStream fileOut = Files.newOutputStream(backupFile);
             ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
            Files.walkFileTree(worldPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (shouldSkip(worldPath.relativize(dir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = worldPath.relativize(file);
                    if (shouldSkip(relative)) {
                        return FileVisitResult.CONTINUE;
                    }

                    ZipEntry entry = new ZipEntry(relative.toString().replace('\\', '/'));
                    zipOut.putNextEntry(entry);
                    Files.copy(file, zipOut);
                    zipOut.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static boolean shouldSkip(Path relativePath) {
        for (Path part : relativePath) {
            if ("backups".equalsIgnoreCase(part.toString())) {
                return true;
            }
        }

        String fileName = relativePath.getFileName() == null
                ? ""
                : relativePath.getFileName().toString().toLowerCase(Locale.ROOT);

        return "session.lock".equals(fileName) || fileName.endsWith(".lock");
    }
}
