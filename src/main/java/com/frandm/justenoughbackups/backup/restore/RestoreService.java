package com.frandm.justenoughbackups.backup.restore;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupIntegrityMode;
import com.frandm.justenoughbackups.backup.model.BackupStatus;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.model.PendingRestore;
import com.frandm.justenoughbackups.backup.retention.RetentionPolicy;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;
import com.frandm.justenoughbackups.backup.storage.WorldSnapshotter;
import com.frandm.justenoughbackups.config.BackupConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RestoreService {
    private static volatile PendingRestore pendingRestore;

    private RestoreService() {
    }

    public static void registerRestoreHandler() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PendingRestore restore = pendingRestore;
            if (restore == null) {
                return;
            }

            pendingRestore = null;
            try {
                applyPreparedRestore(restore);
                RetentionPolicy.apply(restore.worldDirectoryName(), BackupConfig.get());
                WorldBackupMod.LOGGER.warn(
                        "World restore {} applied. Previous world was saved as a normal Old_World backup.",
                        restore.backupId()
                );
            } catch (IOException exception) {
                WorldBackupMod.LOGGER.error("Prepared restore failed while server was stopping.", exception);
            }
        });
    }

    public static PendingRestore prepareRestore(Path backupDir, Path worldPath, String backupId) throws IOException {
        List<BackupManifest> chain = resolveRestoreChain(backupDir, backupId);
        Path tempRestore = backupDir.resolve(".restore-" + backupId);
        BackupManifest target = chain.getLast();

        deleteIfExists(tempRestore);
        Files.createDirectories(tempRestore);
        for (BackupManifest manifest : chain) {
            BackupStorage.extractBackup(backupDir.resolve(manifest.zipFileName), tempRestore);
        }
        pruneToSnapshot(tempRestore, chain.getLast().snapshot);

        PendingRestore restore = new PendingRestore(
                backupId,
                backupDir,
                worldPath,
                tempRestore,
                target.worldName == null ? backupDir.getFileName().toString() : target.worldName,
                target.worldDirectoryName == null ? backupDir.getFileName().toString() : target.worldDirectoryName,
                target.integrityMode == null ? BackupConfig.get().integrityMode : target.integrityMode,
                hasStatusForChain(backupDir, chain),
                Map.copyOf(chain.getLast().snapshot)
        );
        pendingRestore = restore;
        WorldBackupMod.LOGGER.warn("Restore {} prepared. It will be applied when the server stops.", backupId);
        return restore;
    }

    private static List<BackupManifest> resolveRestoreChain(Path backupDir, String backupId) throws IOException {
        List<BackupManifest> manifests = BackupStorage.readManifests(backupDir);
        Map<String, BackupManifest> byId = new LinkedHashMap<>();
        for (BackupManifest manifest : manifests) {
            byId.put(manifest.id, manifest);
        }

        BackupManifest target = byId.get(backupId);
        if (target == null) {
            throw new IOException("Backup not found: " + backupId);
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

    private static void applyPreparedRestore(PendingRestore restore) throws IOException {
        if (!Files.isDirectory(restore.tempRestore())) {
            throw new IOException("Prepared restore directory is missing: " + restore.tempRestore());
        }

        Path stagingWorld = stagingWorldPath(restore);
        deleteIfExists(stagingWorld);
        copyDirectory(restore.tempRestore(), stagingWorld);
        verifyRestoreSnapshot(stagingWorld, restore);

        if (Files.exists(restore.worldPath())) {
            BackupStorage.createBackup(
                    restore.worldPath(),
                    restore.worldName(),
                    restore.worldDirectoryName(),
                    BackupConfig.get(),
                    BackupType.FULL,
                    "Old_World restore " + restore.backupId()
            );
            clearDirectoryContents(restore.worldPath(), restore.backupDir());
        } else {
            Files.createDirectories(restore.worldPath());
        }

        copyDirectory(stagingWorld, restore.worldPath());
        verifyRestoreSnapshot(restore.worldPath(), restore);
        deleteIfExists(stagingWorld);
        deleteIfExists(restore.tempRestore());
    }

    private static Path stagingWorldPath(PendingRestore restore) {
        Path normalizedWorld = restore.worldPath().toAbsolutePath().normalize();
        Path parent = normalizedWorld.getParent();
        if (parent == null) {
            parent = normalizedWorld;
        }
        return parent.resolve(".justenoughbackups-staging-" + restore.backupId());
    }

    private static void copyDirectory(Path source, Path target, Path... skippedRoots) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        List<Path> normalizedSkippedRoots = normalizeSkippedRoots(skippedRoots);

        Files.walkFileTree(normalizedSource, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path normalizedDir = dir.toAbsolutePath().normalize();
                if (!normalizedDir.equals(normalizedSource) && isInsideAny(normalizedDir, normalizedSkippedRoots)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                Path relative = normalizedSource.relativize(dir);
                Files.createDirectories(normalizedTarget.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path normalizedFile = file.toAbsolutePath().normalize();
                if (isInsideAny(normalizedFile, normalizedSkippedRoots)) {
                    return FileVisitResult.CONTINUE;
                }

                Path relative = normalizedSource.relativize(file);
                Files.copy(file, normalizedTarget.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static List<Path> normalizeSkippedRoots(Path... skippedRoots) {
        List<Path> normalized = new ArrayList<>();
        for (Path skippedRoot : skippedRoots) {
            if (skippedRoot != null) {
                normalized.add(skippedRoot.toAbsolutePath().normalize());
            }
        }
        return normalized;
    }

    private static boolean isInsideAny(Path path, List<Path> roots) {
        return roots.stream().anyMatch(path::startsWith);
    }

    private static void clearDirectoryContents(Path target, Path backupRootToPreserve) throws IOException {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path normalizedBackupRoot = backupRootToPreserve.toAbsolutePath().normalize();

        Files.walkFileTree(normalizedTarget, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path normalizedDir = dir.toAbsolutePath().normalize();
                if (!normalizedDir.equals(normalizedTarget) && normalizedDir.startsWith(normalizedBackupRoot)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path normalizedFile = file.toAbsolutePath().normalize();
                if (!normalizedFile.startsWith(normalizedBackupRoot)) {
                    Files.deleteIfExists(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }

                Path normalizedDir = dir.toAbsolutePath().normalize();
                if (!normalizedDir.equals(normalizedTarget) && !normalizedDir.startsWith(normalizedBackupRoot)) {
                    Files.deleteIfExists(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void verifyRestoreSnapshot(Path dir, PendingRestore restore) throws IOException {
        try {
            if (restore.strictSnapshotVerification()) {
                verifySnapshotContent(dir, restore.snapshot());
            } else {
                verifySnapshotFileList(dir, restore.snapshot());
            }
        } catch (IOException exception) {
            if (restore.integrityMode() == BackupIntegrityMode.VERY_PERMISSIVE) {
                WorldBackupMod.LOGGER.warn("Restore integrity verification failed, but VERY_PERMISSIVE mode allows continuing.", exception);
                return;
            }
            throw exception;
        }
    }

    private static void verifySnapshotFileList(Path dir, Map<String, BackupManifest.FileState> expectedSnapshot) throws IOException {
        Map<String, BackupManifest.FileState> actualSnapshot = WorldSnapshotter.snapshot(dir);
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
            if (actual == null || actual.size != expected.size || !actual.sha256.equals(expected.sha256)) {
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

    private static void pruneToSnapshot(Path targetDir, Map<String, BackupManifest.FileState> snapshot) throws IOException {
        Set<String> expectedFiles = snapshot.keySet();
        Files.walkFileTree(targetDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relativeName = targetDir.relativize(file).toString().replace('\\', '/');
                if (!expectedFiles.contains(relativeName)) {
                    Files.deleteIfExists(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                if (!dir.equals(targetDir) && isEmptyDirectory(dir)) {
                    Files.deleteIfExists(dir);
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

    private static void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (var stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path item : paths) {
                Files.deleteIfExists(item);
            }
        }
    }
}
