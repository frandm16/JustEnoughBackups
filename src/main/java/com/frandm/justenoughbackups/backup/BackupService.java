package com.frandm.justenoughbackups.backup;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupStatus;
import com.frandm.justenoughbackups.backup.model.BackupSummary;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.model.PendingRestore;
import com.frandm.justenoughbackups.backup.progress.BackupProgress;
import com.frandm.justenoughbackups.backup.parallel.BackupWatchdog;
import com.frandm.justenoughbackups.backup.progress.BackupProgressBroadcaster;
import com.frandm.justenoughbackups.backup.progress.BackupProgressListener;
import com.frandm.justenoughbackups.backup.progress.BackupProgressState;
import com.frandm.justenoughbackups.backup.progress.BackupProgressPhase;
import com.frandm.justenoughbackups.backup.restore.RestoreService;
import com.frandm.justenoughbackups.backup.retention.RetentionPolicy;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;
import com.frandm.justenoughbackups.backup.storage.remote.RemoteBackupCatalog;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.frandm.justenoughbackups.scheduler.BackupScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

        return performBackup(server, type, reason, requestedName, true, false);
    }

    private static CompletableFuture<BackupManifest> performBackup(MinecraftServer server, BackupType type, String reason, String requestedName, boolean resetLock, boolean isSafetyBackup) {
        BackupMessages.broadcastBackupStarted(server, type, reason);
        Path worldPath;
        String worldName;
        String worldDirectoryName;
        boolean previousAutoSave = server.isAutoSave();
        boolean savingDisabled = false;
        WorldSavingState previousWorldSavingState = null;
        try {
            server.saveEverything(true, true, true);
            server.setAutoSave(false);
            savingDisabled = true;
            previousWorldSavingState = WorldSavingState.captureAndDisable(server);
            worldPath = server.getWorldPath(LevelResource.ROOT);
            worldName = BackupPaths.worldName(server, worldPath);
            worldDirectoryName = BackupPaths.worldDirectoryName(server, worldPath);
        } catch (RuntimeException | Error exception) {
            restoreSavingBestEffort(server, previousAutoSave, savingDisabled, previousWorldSavingState);
            if (resetLock) {
                BACKUP_RUNNING.set(false);
            }
            BackupMessages.broadcastBackupFailed(server, type, reason, exception);
            return CompletableFuture.failedFuture(exception);
        }
        BackupConfig config = BackupConfig.get();
        AtomicReference<BackupProgress> lastProgress = new AtomicReference<>();
        boolean autoSaveToRestore = previousAutoSave;
        WorldSavingState worldSavingStateToRestore = previousWorldSavingState;

        return CompletableFuture.supplyAsync(() -> {
            BackupWatchdog watchdog = new BackupWatchdog(progress -> {
                lastProgress.set(progress);
                BackupProgressBroadcaster.broadcast(server, progress);
            });
            watchdog.start();
            try {
                lastProgress.set(initialProgress(type, reason));
                BackupProgressBroadcaster.broadcast(server, lastProgress.get());
                BackupStorage.resetAbortState();
                BackupStorage.BackupCreation creation;
                if (config.remoteStorage != null && config.remoteStorage.enabled) {
                    try (RemoteBackupCatalog catalog = new RemoteBackupCatalog(config.remoteStorage, worldDirectoryName)) {
                        List<BackupManifest> remoteManifests = catalog.list().stream().map(RemoteBackupCatalog.CatalogEntry::manifest).toList();
                        creation = createRemoteBackupWithRetries(worldPath, worldName, worldDirectoryName, config,
                                type, reason, requestedName, isSafetyBackup, remoteManifests, catalog, watchdog.listener());
                        try { catalog.applyRetention(config); }
                        catch (IOException retentionFailure) {
                            WorldBackupMod.LOGGER.error("Remote backup succeeded, but remote retention could not be completed.", retentionFailure);
                        }
                        try {
                            int cleaned = catalog.cleanupAbandonedUploads();
                            if (cleaned > 0) WorldBackupMod.LOGGER.info("Removed {} abandoned remote upload(s).", cleaned);
                        } catch (IOException cleanupFailure) {
                            WorldBackupMod.LOGGER.warn("Unable to clean abandoned remote uploads.", cleanupFailure);
                        }
                    }
                } else {
                    creation = BackupStorage.createBackupWithManifests(worldPath, worldName, worldDirectoryName,
                            config, type, reason, requestedName, isSafetyBackup, watchdog.listener());
                    RetentionPolicy.apply(worldDirectoryName, config, creation.manifests());
                }
                BackupManifest manifest = creation.manifest();
                watchdog.stop();
                broadcastTerminal(server, manifest, creation.status());
                WorldBackupMod.LOGGER.info("Backup created: {}", manifest.id);
                BackupMessages.broadcastBackupCompleted(server, manifest);
                return manifest;
            } catch (BackupWatchdog.TimeoutException exception) {
                broadcastFailed(server, type, reason, lastProgress);
                BackupMessages.broadcastBackupFailed(server, type, reason, exception);
                throw new RuntimeException("Backup aborted after watchdog timeout.", exception);
            } catch (IOException exception) {
                broadcastFailed(server, type, reason, lastProgress);
                BackupMessages.broadcastBackupFailed(server, type, reason, exception);
                throw new RuntimeException("Failed to create " + type + " backup.", exception);
            } catch (RuntimeException exception) {
                broadcastFailed(server, type, reason, lastProgress);
                BackupMessages.broadcastBackupFailed(server, type, reason, exception);
                throw exception;
            } finally {
                watchdog.stop();
                restoreSaving(server, autoSaveToRestore, worldSavingStateToRestore);
                if (resetLock) {
                    BACKUP_RUNNING.set(false);
                }
            }
        });
    }

    private static BackupStorage.BackupCreation createRemoteBackupWithRetries(
            Path worldPath, String worldName, String worldDirectoryName, BackupConfig config, BackupType type,
            String reason, String requestedName, boolean isSafetyBackup, List<BackupManifest> manifests,
            RemoteBackupCatalog catalog, BackupProgressListener progress
    ) throws IOException {
        int retries = Math.clamp(config.remoteStorage.maxRetries, 0, 10);
        IOException lastFailure = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return BackupStorage.createRemoteBackup(worldPath, worldName, worldDirectoryName, config, type,
                        reason, requestedName, isSafetyBackup, manifests, catalog, progress);
            } catch (IOException exception) {
                lastFailure = exception;
                if (attempt == retries) break;
                WorldBackupMod.LOGGER.warn("Remote backup upload failed (attempt {} of {}). Retrying the streamed archive.",
                        attempt + 1, retries + 1);
                try { Thread.sleep(Math.min(1000L << attempt, 15_000L)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Remote backup retry interrupted.", interrupted);
                }
            }
        }
        throw new IOException("Remote backup upload failed after " + (retries + 1) + " attempt(s).", lastFailure);
    }

    public static List<BackupManifest> listBackups(MinecraftServer server) throws IOException {
        BackupConfig config = BackupConfig.get();
        if (config.remoteStorage != null && config.remoteStorage.enabled) {
            try (RemoteBackupCatalog catalog = new RemoteBackupCatalog(config.remoteStorage, BackupPaths.worldDirectoryName(server, server.getWorldPath(LevelResource.ROOT)))) {
                return catalog.list().stream().map(RemoteBackupCatalog.CatalogEntry::manifest)
                        .sorted(Comparator.comparing(manifest -> manifest.createdAt)).toList();
            }
        }
        return BackupStorage.readManifests(BackupPaths.worldBackupDir(server)).stream()
                .sorted(Comparator.comparing(manifest -> manifest.createdAt))
                .toList();
    }

    private static BackupProgress initialProgress(BackupType type, String reason) {
        return new BackupProgress(
                "",
                type,
                reason,
                BackupProgressPhase.SCANNING,
                0L,
                0L,
                0,
                0,
                BackupProgressState.STARTED
        );
    }

    private static void broadcastTerminal(MinecraftServer server, BackupManifest manifest, BackupStatus status) {
        boolean completed = status == null || status.completed;
        int files = manifest.includedFiles == null ? 0 : manifest.includedFiles.size();
        long bytes = archiveBytes(server, manifest);
        BackupProgressBroadcaster.broadcast(server, new BackupProgress(
                manifest.id,
                manifest.type,
                manifest.reason,
                BackupProgressPhase.WRITING,
                bytes,
                bytes,
                files,
                files,
                completed ? BackupProgressState.COMPLETED : BackupProgressState.FAILED
        ));
        if (!completed) {
            WorldBackupMod.LOGGER.warn("Backup {} was published with broken files: {}",
                    manifest.id, status.brokenFiles.size());
        }
    }

    private static long archiveBytes(MinecraftServer server, BackupManifest manifest) {
        try {
            Path backupFile = BackupPaths.worldBackupDir(server).resolve(manifest.zipFileName);
            if (backupFile != null && Files.isRegularFile(backupFile)) {
                return Files.size(backupFile);
            }
        } catch (IOException exception) {
            WorldBackupMod.LOGGER.debug("Unable to read published backup size for {}", manifest.id, exception);
        }
        return Math.max(0L, manifest.includedBytes);
    }

    private static void broadcastFailed(MinecraftServer server, BackupType type, String reason, AtomicReference<BackupProgress> lastProgress) {
        BackupProgress last = lastProgress.get();
        BackupProgress failed = new BackupProgress(
                last == null ? "" : last.backupId(),
                type,
                reason,
                last == null ? BackupProgressPhase.SCANNING : last.phase(),
                last == null ? 0L : last.bytesWritten(),
                last == null ? 0L : last.totalBytes(),
                last == null ? 0 : last.filesWritten(),
                last == null ? 0 : last.totalFiles(),
                BackupProgressState.FAILED
        );
        BackupProgressBroadcaster.broadcast(server, failed);
    }

    public static List<BackupSummary> listBackupSummaries(MinecraftServer server) throws IOException {
        List<BackupManifest> manifests = listBackups(server).stream()
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
        BackupConfig config = BackupConfig.get();
        if (config.remoteStorage != null && config.remoteStorage.enabled) {
            String worldDirectoryName = BackupPaths.worldDirectoryName(server, server.getWorldPath(LevelResource.ROOT));
            try (RemoteBackupCatalog catalog = new RemoteBackupCatalog(config.remoteStorage, worldDirectoryName)) {
                BackupManifest manifest = catalog.list().stream().map(RemoteBackupCatalog.CatalogEntry::manifest)
                        .filter(item -> Objects.equals(item.id, backupId)).findFirst()
                        .orElseThrow(() -> new IOException("Backup not found: " + backupId));
                catalog.rename(manifest, requestedName);
                return;
            }
        }
        BackupStorage.renameBackup(BackupPaths.worldBackupDir(server), backupId, requestedName);
    }

    public static void deleteBackup(MinecraftServer server, String backupId) throws IOException {
        BackupConfig config = BackupConfig.get();
        if (config.remoteStorage != null && config.remoteStorage.enabled) {
            String worldDirectoryName = BackupPaths.worldDirectoryName(server, server.getWorldPath(LevelResource.ROOT));
            try (RemoteBackupCatalog catalog = new RemoteBackupCatalog(config.remoteStorage, worldDirectoryName)) {
                List<RemoteBackupCatalog.CatalogEntry> entries = catalog.list();
                BackupManifest dependent = entries.stream().map(RemoteBackupCatalog.CatalogEntry::manifest)
                        .filter(manifest -> Objects.equals(manifest.baseBackupId, backupId)).findFirst().orElse(null);
                if (dependent != null) throw new IOException("Required by backup: " + BackupStorage.displayName(dependent));
                BackupManifest target = entries.stream().map(RemoteBackupCatalog.CatalogEntry::manifest)
                        .filter(manifest -> Objects.equals(manifest.id, backupId)).findFirst()
                        .orElseThrow(() -> new IOException("Backup not found: " + backupId));
                catalog.delete(target.id, target.zipFileName);
                return;
            }
        }
        List<BackupManifest> manifests = BackupStorage.readManifests(BackupPaths.worldBackupDir(server));
        BackupManifest dependent = manifests.stream()
                .filter(manifest -> Objects.equals(manifest.baseBackupId, backupId))
                .findFirst()
                .orElse(null);
        if (dependent != null) {
            throw new IOException("Required by backup: " + BackupStorage.displayName(dependent));
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
        Path worldPath;
        Path backupDir;
        try {
            server.saveEverything(true, true, true);
            worldPath = server.getWorldPath(LevelResource.ROOT);
            backupDir = BackupPaths.worldBackupDir(server);
        } catch (RuntimeException | Error exception) {
            BACKUP_RUNNING.set(false);
            BackupMessages.broadcastRestoreFailed(server, rootMessage(exception));
            return CompletableFuture.failedFuture(exception);
        }

        BackupProgressListener restoreProgress = progress -> BackupProgressBroadcaster.broadcast(server, progress);

        CompletableFuture<PendingRestore> prepared = CompletableFuture.supplyAsync(() -> {
            try {
                if (BackupConfig.get().remoteStorage != null && BackupConfig.get().remoteStorage.enabled) {
                    return prepareRemoteRestore(server, worldPath, backup, byId, restoreProgress);
                }
                BackupManifest target = byId
                        ? BackupStorage.findById(backupDir, backup)
                        : BackupStorage.findByZipName(backupDir, backup);
                return RestoreService.prepareRestore(backupDir, worldPath, target, backup, restoreProgress);
            } catch (IOException exception) {
                throw new RuntimeException("Failed to prepare restore: " + backup, exception);
            }
        });

        return prepared.thenCompose(restore -> runSafetyBackup(server, restore))
                .handle((restore, error) -> {
                    if (error != null) {
                        BACKUP_RUNNING.set(false);
                        prepared.thenAccept(RestoreService::cancelPreparedRestore);
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        BackupMessages.broadcastRestoreFailed(server, rootMessage(cause));
                        throw new CompletionException(cause);
                    }
                    BACKUP_RUNNING.set(false);
                    BackupMessages.broadcastRestorePrepared(server, restore.backupId());
                    return restore;
                });
    }

    private static PendingRestore prepareRemoteRestore(MinecraftServer server, Path worldPath, String requested,
                                                       boolean byId, BackupProgressListener progress) throws IOException {
        BackupConfig config = BackupConfig.get();
        String worldDirectoryName = BackupPaths.worldDirectoryName(server, worldPath);
        try (RemoteBackupCatalog catalog = new RemoteBackupCatalog(config.remoteStorage, worldDirectoryName)) {
            List<RemoteBackupCatalog.CatalogEntry> entries = catalog.list();
            RemoteBackupCatalog.CatalogEntry selected = entries.stream()
                    .filter(entry -> byId ? entry.manifest().id.equals(requested)
                            : entry.manifest().zipFileName.equals(requested)
                            || BackupStorage.displayName(entry.manifest()).equals(requested))
                    .findFirst().orElseThrow(() -> new IOException("Remote backup not found: " + requested));
            Map<String, RemoteBackupCatalog.CatalogEntry> byBackupId = new HashMap<>();
            entries.forEach(entry -> byBackupId.put(entry.manifest().id, entry));
            List<BackupManifest> chain = new java.util.ArrayList<>();
            BackupManifest current = selected.manifest();
            while (current != null) {
                RemoteBackupCatalog.CatalogEntry entry = byBackupId.get(current.id);
                if (entry == null || entry.sha256() == null || entry.sha256().isBlank())
                    throw new IOException("Remote backup is missing its integrity metadata: " + current.id);
                chain.add(0, current);
                if (current.type == BackupType.FULL) break;
                if (current.baseBackupId == null) throw new IOException("Remote backup chain has no full base: " + current.id);
                RemoteBackupCatalog.CatalogEntry base = byBackupId.get(current.baseBackupId);
                if (base == null) throw new IOException("Missing remote base backup: " + current.baseBackupId);
                current = base.manifest();
            }
            if (chain.isEmpty() || chain.getFirst().type != BackupType.FULL)
                throw new IOException("Remote restore chain does not start with a full backup.");
            Path localBackupDir = BackupPaths.worldBackupDir(server);
            return RestoreService.prepareRemoteRestore(localBackupDir, worldPath, selected.manifest(), chain,
                    byBackupId, catalog, requested, progress);
        }
    }

    private static CompletableFuture<PendingRestore> runSafetyBackup(MinecraftServer server, PendingRestore restore) {
        CompletableFuture<BackupManifest> backup = new CompletableFuture<>();
        server.execute(() -> {
            try {
                performBackup(server, BackupType.FULL, "Old_World restore " + restore.backupId(), "", false, true)
                        .whenComplete((manifest, error) -> {
                            if (error != null) {
                                backup.completeExceptionally(error);
                            } else {
                                backup.complete(manifest);
                            }
                        });
            } catch (Throwable error) {
                backup.completeExceptionally(error);
            }
        });
        return backup.thenApply(ignored -> restore);
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
        String deleteBlockedReason = dependent == null ? "" : "Required by backup: " + displayNames.getOrDefault(dependent.id, dependent.id);
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

    private static void restoreSaving(MinecraftServer server, boolean previousAutoSave, WorldSavingState previousWorldSavingState) {
        server.execute(() -> {
            previousWorldSavingState.restore(server);
            server.setAutoSave(previousAutoSave);
        });
    }

    private static void restoreSavingBestEffort(MinecraftServer server, boolean previousAutoSave, boolean savingDisabled, WorldSavingState previousWorldSavingState) {
        try {
            if (savingDisabled) {
                server.setAutoSave(previousAutoSave);
            }
            if (previousWorldSavingState != null) {
                previousWorldSavingState.restore(server);
            }
        } catch (RuntimeException exception) {
            WorldBackupMod.LOGGER.error("Failed to restore world saving state after backup setup failure.", exception);
        }
    }
}
