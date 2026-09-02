package com.frandm.justenoughbackups.backup.restore;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.BackupConstants;
import com.frandm.justenoughbackups.backup.BackupPaths;
import com.frandm.justenoughbackups.backup.model.BackupIntegrityMode;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupStatus;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.model.PendingRestore;
import com.frandm.justenoughbackups.backup.model.RestoreIntent;
import com.frandm.justenoughbackups.backup.progress.BackupProgressListener;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;
import com.frandm.justenoughbackups.backup.storage.WorldSnapshotter;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RestoreService {
    private RestoreService() {
    }

    public static void registerRestoreHandler() {
        ServerLifecycleEvents.SERVER_STOPPED.register(RestoreService::applyPendingRestore);
        ServerLifecycleEvents.SERVER_STARTING.register(RestoreService::recoverPendingRestore);
        ServerLifecycleEvents.SERVER_STARTED.register(RestoreService::cleanupRestoreArtifacts);
    }

    public static PendingRestore prepareRestore(
            Path backupDir,
            Path worldPath,
            BackupManifest targetManifest,
            String requestedName,
            BackupProgressListener progressListener
    ) throws IOException {
        List<BackupManifest> chain = resolveRestoreChain(backupDir, targetManifest);
        String backupId = targetManifest.id;
        String reason = "Restore " + backupId;
        BackupManifest target = chain.getLast();
        Path normalizedWorld = worldPath.toAbsolutePath().normalize();
        Path worldParent = normalizedWorld.getParent();
        if (worldParent == null) {
            throw new IOException("Unable to resolve the parent directory of the world: " + normalizedWorld);
        }
        Path container = worldParent.resolve(BackupConstants.RESTORE_CONTAINER);
        Path staging = container.resolve(BackupConstants.RESTORE_STAGING_PREFIX + backupId);
        Path oldWorld = container.resolve(BackupConstants.RESTORE_OLD_PREFIX + backupId);

        deleteRecursively(staging);
        deleteRecursively(oldWorld);
        Files.createDirectories(container);
        Files.createDirectories(staging);
        for (BackupManifest manifest : chain) {
            BackupStorage.extractBackup(backupDir.resolve(manifest.zipFileName), staging, backupId, reason, progressListener);
        }
        Path targetBackupFile = backupDir.resolve(target.zipFileName);
        if (!BackupStorage.hasSummaryFile(targetBackupFile)) {
            Files.deleteIfExists(staging.resolve(BackupConstants.SUMMARY_ENTRY));
        }
        pruneToSnapshot(staging, target.snapshot);

        BackupIntegrityMode integrityMode = target.integrityMode == null ? BackupConfig.get().integrityMode : target.integrityMode;
        boolean strict = hasStatusForChain(backupDir, chain);
        Map<String, BackupManifest.FileState> snapshot = Map.copyOf(target.snapshot);

        verifyRestoreSnapshot(staging, integrityMode, strict, snapshot);

        RestoreJournal.write(new RestoreIntent(
                RestoreIntent.CURRENT_VERSION,
                backupId,
                normalizedWorld,
                staging,
                oldWorld,
                RestoreIntent.RestoreState.PREPARED,
                integrityMode,
                strict,
                snapshot
        ));
        WorldBackupMod.LOGGER.warn("Restore {} prepared from request {}. It will be applied when the server stops.", backupId, requestedName);
        return new PendingRestore(
                backupId,
                backupDir,
                normalizedWorld,
                staging,
                target.worldName == null ? backupDir.getFileName().toString() : target.worldName,
                target.worldDirectoryName == null ? backupDir.getFileName().toString() : target.worldDirectoryName,
                integrityMode,
                strict,
                snapshot
        );
    }

    public static void cancelPreparedRestore(PendingRestore restore) {
        try {
            if (restore.stagingPath() != null) {
                deleteRecursively(restore.stagingPath());
            }
            RestoreJournal.delete(restore.worldPath().getParent(), restore.worldPath());
            WorldBackupMod.LOGGER.warn("Cancelled pending restore {}.", restore.backupId());
        } catch (IOException exception) {
            WorldBackupMod.LOGGER.warn("Failed to cancel pending restore {}.", restore.backupId(), exception);
        }
    }

    private static List<BackupManifest> resolveRestoreChain(Path backupDir, BackupManifest target) throws IOException {
        List<BackupManifest> manifests = BackupStorage.readManifests(backupDir);
        Map<String, BackupManifest> byId = new LinkedHashMap<>();
        for (BackupManifest manifest : manifests) {
            byId.put(manifest.id, manifest);
        }

        ArrayDeque<BackupManifest> chain = new ArrayDeque<>();
        BackupManifest current = target;
        while (current != null) {
            Path zipFile = backupDir.resolve(current.zipFileName);
            if (!Files.isRegularFile(zipFile)) {
                throw new IOException("Missing backup file: " + zipFile);
            }

            chain.addFirst(current);
            if (current.type == BackupType.FULL) {
                break;
            }
            if (current.baseBackupId == null) {
                throw new IOException("Backup chain has no full base for: " + current.id);
            }
            current = byId.get(current.baseBackupId);
            if (current == null) {
                throw new IOException("Missing base backup: " + chain.peekFirst().baseBackupId);
            }
        }

        if (chain.isEmpty() || chain.peekFirst().type != BackupType.FULL) {
            throw new IOException("Restore chain does not start with a full backup.");
        }

        return List.copyOf(chain);
    }

    private static void applyPendingRestore(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path worldParent = worldPath.getParent();
        if (worldParent == null) {
            return;
        }
        try {
            Optional<RestoreIntent> intent = RestoreJournal.read(worldParent, worldPath);
            if (intent.isPresent() && isPendingState(intent.get().state())) {
                applySwap(intent.get());
            }
        } catch (IOException exception) {
            WorldBackupMod.LOGGER.error("Prepared restore failed while the server was stopping.", exception);
        }
    }

    private static void recoverPendingRestore(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path worldParent = worldPath.getParent();
        if (worldParent == null) {
            return;
        }
        try {
            Optional<RestoreIntent> intent = RestoreJournal.read(worldParent, worldPath);
            if (intent.isEmpty()) {
                return;
            }
            RestoreIntent restore = intent.get();
            if (isPendingState(restore.state())) {
                WorldBackupMod.LOGGER.warn("Resuming pending restore {} (state {}).", restore.backupId(), restore.state());
                applySwap(restore);
                return;
            }
            switch (restore.state()) {
                case FAILED -> {
                    restorePreviousWorldIfPossible(restore);
                    RestoreJournal.delete(worldParent, worldPath);
                }
                case APPLIED -> {
                    // Cleanup runs on SERVER_STARTED.
                }
                default -> {
                }
            }
        } catch (IOException exception) {
            WorldBackupMod.LOGGER.error("Failed to recover a pending restore.", exception);
        }
    }

    private static boolean isPendingState(RestoreIntent.RestoreState state) {
        return state == RestoreIntent.RestoreState.PREPARED
                || state == RestoreIntent.RestoreState.OLD_MOVED
                || state == RestoreIntent.RestoreState.STAGING_INSTALLED;
    }

    private static void applySwap(RestoreIntent intent) throws IOException {
        Path world = intent.worldPath();
        Path staging = intent.stagingPath();
        Path old = intent.oldWorldPath();

        if (intent.state() == RestoreIntent.RestoreState.PREPARED
                && Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS)
                && Files.exists(old, LinkOption.NOFOLLOW_LINKS)) {
            deleteRecursively(old);
        }

        if (Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS) && !Files.exists(old, LinkOption.NOFOLLOW_LINKS)) {
            Files.move(world, old);
            persist(intent, RestoreIntent.RestoreState.OLD_MOVED);
        }
        if (Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS) && !Files.exists(world, LinkOption.NOFOLLOW_LINKS)) {
            Files.move(staging, world);
            persist(intent, RestoreIntent.RestoreState.STAGING_INSTALLED);
        }

        boolean worldInPlace = Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS);
        boolean oldInPlace = Files.isDirectory(old, LinkOption.NOFOLLOW_LINKS);
        boolean stagingInPlace = Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS);

        if (!worldInPlace) {
            if (oldInPlace) {
                Files.move(old, world);
                WorldBackupMod.LOGGER.error("Restore {} aborted; the previous world was restored because the new world is missing.", intent.backupId());
            } else {
                WorldBackupMod.LOGGER.error("Restore {} failed and no previous world was available to restore.", intent.backupId());
            }
            if (stagingInPlace) {
                deleteRecursively(staging);
            }
            persist(intent, RestoreIntent.RestoreState.FAILED);
            throw new IOException("Restore could not be applied: world directory is missing.");
        }

        if (stagingInPlace && oldInPlace) {
            deleteRecursively(world);
            Files.move(old, world);
            persist(intent, RestoreIntent.RestoreState.FAILED);
            throw new IOException("Restore could not be applied: conflicting restore directories were found.");
        }

        try {
            verifySnapshotFileList(world, intent.snapshot());
        } catch (IOException failure) {
            if (intent.integrityMode() == BackupIntegrityMode.VERY_PERMISSIVE) {
                WorldBackupMod.LOGGER.warn(
                        "Restore {} failed verification, but VERY_PERMISSIVE mode allows keeping the restored world.",
                        intent.backupId(), failure
                );
            } else {
                if (oldInPlace) {
                    deleteRecursively(world);
                    Files.move(old, world);
                    WorldBackupMod.LOGGER.error("Restore {} failed verification; the previous world was restored.", intent.backupId(), failure);
                } else {
                    WorldBackupMod.LOGGER.error("Restore {} failed verification and no previous world was available.", intent.backupId(), failure);
                }
                if (stagingInPlace) {
                    deleteRecursively(staging);
                }
                persist(intent, RestoreIntent.RestoreState.FAILED);
                throw new IOException("Restore verification failed for " + intent.backupId(), failure);
            }
        }

        persist(intent, RestoreIntent.RestoreState.APPLIED);
        WorldBackupMod.LOGGER.info("Restore {} applied to world {}.", intent.backupId(), world);

        try {
            deleteRecursively(old);
            RestoreJournal.delete(world.getParent(), world);
            WorldBackupMod.LOGGER.info("Deleted the previous world after restore {}.", intent.backupId());
        } catch (IOException failure) {
            WorldBackupMod.LOGGER.warn(
                    "Restore {} applied, but the previous world could not be deleted; it will be cleaned up on the next start.",
                    intent.backupId(), failure
            );
        }
    }

    private static void restorePreviousWorldIfPossible(RestoreIntent intent) throws IOException {
        Path world = intent.worldPath();
        Path old = intent.oldWorldPath();
        Path staging = intent.stagingPath();
        if (old != null && !Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS) && Files.isDirectory(old, LinkOption.NOFOLLOW_LINKS)) {
            Files.move(old, world);
            WorldBackupMod.LOGGER.info("Restored the previous world after failed restore {}.", intent.backupId());
        }
        if (staging != null && Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS)) {
            deleteRecursively(staging);
        }
    }

    private static void persist(RestoreIntent intent, RestoreIntent.RestoreState state) throws IOException {
        RestoreJournal.write(intent.withState(state));
    }

    private static void cleanupRestoreArtifacts(MinecraftServer server) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                cleanupRestoreArtifactsSync(server);
            } catch (IOException exception) {
                WorldBackupMod.LOGGER.warn("Failed to clean up restore artifacts.", exception);
            }
        });
    }

    private static void cleanupRestoreArtifactsSync(MinecraftServer server) throws IOException {
        Path worldPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path worldParent = worldPath.getParent();
        if (worldParent == null) {
            return;
        }
        Path container = worldParent.resolve(BackupConstants.RESTORE_CONTAINER);

        Set<Path> activeArtifacts = new HashSet<>();
        for (RestoreIntent intent : RestoreJournal.readAll(worldParent)) {
            switch (intent.state()) {
                case APPLIED -> {
                    if (intent.oldWorldPath() != null) {
                        deleteRecursively(intent.oldWorldPath());
                    }
                    RestoreJournal.delete(worldParent, intent.worldPath());
                    WorldBackupMod.LOGGER.info("Cleaned up the old world after restore {}.", intent.backupId());
                }
                case PREPARED, OLD_MOVED, STAGING_INSTALLED -> {
                    if (intent.stagingPath() != null) {
                        activeArtifacts.add(intent.stagingPath());
                    }
                    if (intent.oldWorldPath() != null) {
                        activeArtifacts.add(intent.oldWorldPath());
                    }
                }
                case FAILED -> {
                    restorePreviousWorldIfPossible(intent);
                    RestoreJournal.delete(worldParent, intent.worldPath());
                }
            }
        }

        if (Files.isDirectory(container)) {
            try (var stream = Files.list(container)) {
                for (Path candidate : stream
                        .filter(Files::isDirectory)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return (name.startsWith(BackupConstants.RESTORE_STAGING_PREFIX)
                                    || name.startsWith(BackupConstants.RESTORE_OLD_PREFIX))
                                    && !activeArtifacts.contains(path.toAbsolutePath().normalize());
                        })
                        .toList()) {
                    deleteRecursively(candidate);
                    WorldBackupMod.LOGGER.info("Removed orphaned restore artifact: {}", candidate);
                }
            }
        }

        try (var stream = Files.list(worldParent)) {
            for (Path candidate : stream
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(BackupConstants.RESTORE_STAGING_PREFIX)
                                || name.startsWith(BackupConstants.RESTORE_OLD_PREFIX);
                    })
                    .toList()) {
                deleteRecursively(candidate);
                WorldBackupMod.LOGGER.info("Removed legacy restore artifact: {}", candidate);
            }
        }

        Path backupDir = BackupPaths.worldBackupDir(server).toAbsolutePath().normalize();
        if (Files.isDirectory(backupDir)) {
            try (var stream = Files.list(backupDir)) {
                for (Path candidate : stream
                        .filter(path -> path.getFileName().toString().startsWith(".restore-"))
                        .toList()) {
                    deleteRecursively(candidate);
                    WorldBackupMod.LOGGER.info("Removed orphaned restore temporary directory: {}", candidate);
                }
            }
        }

        if (Files.isDirectory(container) && isEmptyDirectory(container)) {
            Files.deleteIfExists(container);
        }
    }

    private static void pruneToSnapshot(Path targetDir, Map<String, BackupManifest.FileState> snapshot) throws IOException {
        Set<String> expectedFiles = snapshot.keySet();
        Files.walkFileTree(targetDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relativeName = targetDir.relativize(file).toString().replace('\\', '/');
                if (!expectedFiles.contains(relativeName) && !BackupConstants.SUMMARY_ENTRY.equals(relativeName)) {
                    makeDeletable(file);
                    deleteWithRetry(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                if (!dir.equals(targetDir) && isEmptyDirectory(dir)) {
                    makeDeletable(dir);
                    deleteWithRetry(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isEmptyDirectory(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        }
    }

    private static void verifyRestoreSnapshot(
            Path dir,
            BackupIntegrityMode integrityMode,
            boolean strict,
            Map<String, BackupManifest.FileState> snapshot
    ) throws IOException {
        try {
            if (strict) {
                verifySnapshotContent(dir, snapshot);
            } else {
                verifySnapshotFileList(dir, snapshot);
            }
        } catch (IOException exception) {
            if (integrityMode == BackupIntegrityMode.VERY_PERMISSIVE) {
                WorldBackupMod.LOGGER.warn(
                        "Restore integrity verification failed, but VERY_PERMISSIVE mode allows continuing.",
                        exception
                );
                return;
            }
            throw exception;
        }
    }

    private static void verifySnapshotFileList(Path dir, Map<String, BackupManifest.FileState> expectedSnapshot) throws IOException {
        Map<String, BackupManifest.FileState> actualSnapshot = WorldSnapshotter.snapshot(
                dir, BackupType.FULL, "Restore verify", BackupProgressListener.noop());
        Set<String> missingFiles = new HashSet<>(expectedSnapshot.keySet());
        missingFiles.removeAll(actualSnapshot.keySet());
        if (!missingFiles.isEmpty()) {
            throw new IOException("Restored world is missing files: " + missingFiles);
        }

        Set<String> extraFiles = new HashSet<>(actualSnapshot.keySet());
        extraFiles.removeAll(expectedSnapshot.keySet());
        if (!extraFiles.isEmpty()) {
            throw new IOException("Restored world has unexpected files: " + extraFiles);
        }
    }

    private static void verifySnapshotContent(Path dir, Map<String, BackupManifest.FileState> expectedSnapshot) throws IOException {
        verifySnapshotFileList(dir, expectedSnapshot);
        Map<String, BackupManifest.FileState> actualSnapshot = WorldSnapshotter.snapshot(dir);
        for (Map.Entry<String, BackupManifest.FileState> entry : expectedSnapshot.entrySet()) {
            BackupManifest.FileState expected = entry.getValue();
            BackupManifest.FileState actual = actualSnapshot.get(entry.getKey());
            if (actual == null || (expected.sha256 != null && !expected.sha256.equals(actual.sha256))) {
                throw new IOException("Restored world file does not match backup snapshot: " + entry.getKey());
            }
        }
    }

    private static boolean hasStatusForChain(Path backupDir, List<BackupManifest> chain) throws IOException {
        for (BackupManifest manifest : chain) {
            BackupStatus status = BackupStorage.readStatus(backupDir.resolve(manifest.zipFileName));
            if (status == null) {
                return false;
            }
            if (!status.completed || !status.brokenFiles.isEmpty()) {
                BackupIntegrityMode mode = manifest.integrityMode == null ? BackupConfig.get().integrityMode : manifest.integrityMode;
                if (mode != BackupIntegrityMode.VERY_PERMISSIVE) {
                    throw new IOException("Backup integrity status is incomplete or damaged: " + manifest.id);
                }
                WorldBackupMod.LOGGER.warn("Backup {} has damaged status, but VERY_PERMISSIVE mode allows restore.", manifest.id);
            }
        }
        return true;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                makeDeletable(file);
                deleteWithRetry(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                makeDeletable(dir);
                deleteWithRetry(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void makeDeletable(Path path) {
        try {
            DosFileAttributeView view = Files.getFileAttributeView(path, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view != null && view.readAttributes().isReadOnly()) {
                view.setReadOnly(false);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static void deleteWithRetry(Path path) throws IOException {
        int attempts = 3;
        long delayMillis = 150L;
        IOException lastFailure = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException exception) {
                lastFailure = exception;
                if (attempt < attempts - 1) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw exception;
                    }
                }
            }
        }
        throw lastFailure;
    }
}
