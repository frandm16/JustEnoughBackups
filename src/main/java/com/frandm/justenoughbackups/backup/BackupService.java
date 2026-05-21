package com.frandm.justenoughbackups.backup;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupSummary;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.model.PendingRestore;
import com.frandm.justenoughbackups.backup.progress.BackupProgress;
import com.frandm.justenoughbackups.backup.progress.BackupProgressBroadcaster;
import com.frandm.justenoughbackups.backup.progress.BackupProgressState;
import com.frandm.justenoughbackups.backup.restore.RestoreService;
import com.frandm.justenoughbackups.backup.retention.RetentionPolicy;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.frandm.justenoughbackups.scheduler.BackupScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        return createBackup(server, type, reason, "");
    }

    public static CompletableFuture<BackupManifest> createBackup(MinecraftServer server, BackupType type, String reason, String requestedName) {
        if (!BACKUP_RUNNING.compareAndSet(false, true)) {
            IllegalStateException exception = new IllegalStateException("A backup is already running.");
            BackupMessages.broadcastBackupFailed(server, type, reason, exception);
            return CompletableFuture.failedFuture(exception);
        }

        BackupMessages.broadcastBackupStarted(server, type, reason);
        server.saveEverything(true, true, true);
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
                        reason,
                        requestedName,
                        progress -> BackupProgressBroadcaster.broadcast(server, progress)
                );
                RetentionPolicy.apply(worldDirectoryName, config);
                WorldBackupMod.LOGGER.info("Backup created: {}", manifest.id);
                BackupMessages.broadcastBackupCompleted(server, manifest);
                return manifest;
            } catch (IOException exception) {
                BackupProgressBroadcaster.broadcast(server, new BackupProgress(
                        "",
                        type,
                        reason,
                        0L,
                        0L,
                        0,
                        0,
                        BackupProgressState.FAILED
                ));
                BackupMessages.broadcastBackupFailed(server, type, reason, exception);
                throw new RuntimeException("Failed to create " + type + " backup.", exception);
            } finally {
                restoreSaving(server, previousWorldSavingState);
                BACKUP_RUNNING.set(false);
            }
        });
    }

    public static List<BackupManifest> listBackups(MinecraftServer server) throws IOException {
        return BackupStorage.readManifests(BackupPaths.worldBackupDir(server)).stream()
                .sorted(Comparator.comparing(manifest -> manifest.createdAt))
                .toList();
    }

    public static List<BackupSummary> listBackupSummaries(MinecraftServer server) throws IOException {
        List<BackupManifest> manifests = BackupStorage.readManifests(BackupPaths.worldBackupDir(server)).stream()
                .sorted(Comparator.comparing((BackupManifest manifest) -> value(manifest.createdAt)).reversed())
                .toList();
        Map<String, String> displayNames = new HashMap<>();
        for (BackupManifest manifest : manifests) {
            displayNames.put(manifest.id, BackupStorage.displayName(manifest));
        }

        return manifests.stream()
                .map(manifest -> summary(manifest, manifests, displayNames))
                .toList();
    }

    public static void renameBackup(MinecraftServer server, String backupId, String requestedName) throws IOException {
        BackupStorage.renameBackup(BackupPaths.worldBackupDir(server), backupId, requestedName);
    }

    public static void deleteBackup(MinecraftServer server, String backupId) throws IOException {
        List<BackupManifest> manifests = BackupStorage.readManifests(BackupPaths.worldBackupDir(server));
        BackupManifest dependent = manifests.stream()
                .filter(manifest -> Objects.equals(manifest.baseBackupId, backupId))
                .findFirst()
                .orElse(null);
        if (dependent != null) {
            throw new IOException("Required by backup " + BackupStorage.displayName(dependent));
        }
        BackupStorage.deleteBackup(BackupPaths.worldBackupDir(server), backupId);
    }

    public static CompletableFuture<PendingRestore> restoreBackupByName(MinecraftServer server, String backupName) {
        return restoreBackup(server, backupName, false);
    }

    public static CompletableFuture<PendingRestore> restoreBackupById(MinecraftServer server, String backupId) {
        return restoreBackup(server, backupId, true);
    }

    private static CompletableFuture<PendingRestore> restoreBackup(MinecraftServer server, String backup, boolean byId) {
        if (!BACKUP_RUNNING.compareAndSet(false, true)) {
            BackupMessages.broadcastRestoreFailed(server, "A backup or restore is already running.");
            return CompletableFuture.failedFuture(new IllegalStateException("A backup or restore is already running."));
        }

        BackupMessages.broadcastRestoreStarted(server, backup);
        server.saveEverything(true, true, true);
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path backupDir = BackupPaths.worldBackupDir(server);

        return CompletableFuture.supplyAsync(() -> {
            try {
                BackupManifest target = byId
                        ? BackupStorage.findById(backupDir, backup)
                        : BackupStorage.findByZipName(backupDir, backup);
                String displayName = BackupStorage.displayName(target);
                PendingRestore restore = RestoreService.prepareRestore(backupDir, worldPath, target, backup);
                BackupMessages.broadcastRestorePrepared(server, displayName);
                return restore;
            } catch (IOException exception) {
                BackupMessages.broadcastRestoreFailed(server, rootMessage(exception));
                throw new RuntimeException("Failed to prepare restore: " + backup, exception);
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

    private static BackupSummary summary(BackupManifest manifest, List<BackupManifest> allManifests, Map<String, String> displayNames) {
        BackupManifest dependent = allManifests.stream()
                .filter(candidate -> Objects.equals(candidate.baseBackupId, manifest.id))
                .findFirst()
                .orElse(null);
        boolean canDelete = dependent == null;
        String deleteBlockedReason = dependent == null ? "" : "Required by backup " + displayNames.getOrDefault(dependent.id, dependent.id);
        return new BackupSummary(
                manifest.id,
                BackupStorage.displayName(manifest),
                manifest.type,
                manifest.createdAt,
                manifest.worldName,
                manifest.reason,
                manifest.baseBackupId,
                manifest.includedBytes,
                manifest.includedFiles == null ? 0 : manifest.includedFiles.size(),
                true,
                canDelete,
                deleteBlockedReason
        );
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static void restoreSaving(MinecraftServer server, WorldSavingState previousWorldSavingState) {
        server.execute(() -> {
            previousWorldSavingState.restore(server);
        });
    }
}
