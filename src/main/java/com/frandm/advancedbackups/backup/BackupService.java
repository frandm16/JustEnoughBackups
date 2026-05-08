package com.frandm.advancedbackups.backup;

import com.frandm.advancedbackups.WorldBackupMod;
import com.frandm.advancedbackups.backup.model.BackupManifest;
import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.backup.model.PendingRestore;
import com.frandm.advancedbackups.backup.restore.RestoreService;
import com.frandm.advancedbackups.backup.retention.RetentionPolicy;
import com.frandm.advancedbackups.backup.storage.BackupStorage;
import com.frandm.advancedbackups.config.BackupConfig;
import com.frandm.advancedbackups.scheduler.BackupScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupService {
    private static final AtomicBoolean BACKUP_RUNNING = new AtomicBoolean(false);

    private BackupService() {
    }

    public static boolean isBackupRunning() {
        return BACKUP_RUNNING.get();
    }

    public static void registerRestoreHandler() {
        RestoreService.registerRestoreHandler();
    }

    public static CompletableFuture<BackupManifest> createBackup(MinecraftServer server, BackupType type, String reason) {
        if (!BACKUP_RUNNING.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A backup is already running."));
        }

        server.saveEverything(true, true, true);
        boolean previousAutoSave = server.isAutoSave();
        server.setAutoSave(false);
        WorldSavingState previousWorldSavingState = WorldSavingState.captureAndDisable(server);
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        String worldName = BackupPaths.worldName(server, worldPath);
        String worldDirectoryName = BackupPaths.worldDirectoryName(server, worldPath);
        BackupConfig config = BackupConfig.get();

        return CompletableFuture.supplyAsync(() -> {
            try {
                BackupManifest manifest = BackupStorage.createBackup(
                        worldPath,
                        worldName,
                        worldDirectoryName,
                        config,
                        type,
                        reason
                );
                RetentionPolicy.apply(worldDirectoryName, config);
                WorldBackupMod.LOGGER.info("Backup created: {}", manifest.id);
                return manifest;
            } catch (IOException exception) {
                throw new RuntimeException("Failed to create " + type + " backup.", exception);
            } finally {
                restoreSaving(server, previousAutoSave, previousWorldSavingState);
                BACKUP_RUNNING.set(false);
            }
        });
    }

    public static List<BackupManifest> listBackups(MinecraftServer server) throws IOException {
        return BackupStorage.readManifests(BackupPaths.worldBackupDir(server)).stream()
                .sorted(Comparator.comparing(manifest -> manifest.createdAt))
                .toList();
    }

    public static CompletableFuture<PendingRestore> restoreBackup(MinecraftServer server, String backupId) {
        if (!BACKUP_RUNNING.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A backup or restore is already running."));
        }

        server.saveEverything(true, true, true);
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path backupDir = BackupPaths.worldBackupDir(server);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return RestoreService.prepareRestore(backupDir, worldPath, backupId);
            } catch (IOException exception) {
                throw new RuntimeException("Failed to prepare restore: " + backupId, exception);
            } finally {
                BACKUP_RUNNING.set(false);
            }
        });
    }

    public static BackupConfig reloadConfig() {
        BackupConfig config = BackupConfig.reload();
        BackupScheduler.resetTimer();
        return config;
    }

    private static void restoreSaving(MinecraftServer server, boolean previousAutoSave, WorldSavingState previousWorldSavingState) {
        server.execute(() -> {
            previousWorldSavingState.restore(server);
            server.setAutoSave(previousAutoSave);
        });
    }
}
