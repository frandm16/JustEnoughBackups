package com.frandm.advancedbackups.backup.restore;

import com.frandm.advancedbackups.WorldBackupMod;
import com.frandm.advancedbackups.backup.BackupConstants;
import com.frandm.advancedbackups.backup.model.BackupManifest;
import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.backup.model.PendingRestore;
import com.frandm.advancedbackups.backup.storage.BackupStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Comparator;
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
                WorldBackupMod.LOGGER.warn(
                        "World restore {} applied. Previous world moved to {}.",
                        restore.backupId(),
                        restore.previousWorld()
                );
            } catch (IOException exception) {
                WorldBackupMod.LOGGER.error("Prepared restore failed while server was stopping.", exception);
            }
        });
    }

    public static PendingRestore prepareRestore(Path backupDir, Path worldPath, String backupId) throws IOException {
        List<BackupManifest> chain = resolveRestoreChain(backupDir, backupId);
        Path tempRestore = backupDir.resolve(".restore-" + backupId);
        Path previousWorld = backupDir.resolve(".previous-world-" + LocalDateTime.now().format(BackupConstants.FILE_TIME));

        deleteIfExists(tempRestore);
        Files.createDirectories(tempRestore);
        for (BackupManifest manifest : chain) {
            BackupStorage.extractBackup(backupDir.resolve(manifest.zipFileName), tempRestore);
        }
        pruneToSnapshot(tempRestore, chain.getLast().snapshot);

        PendingRestore restore = new PendingRestore(backupId, worldPath, tempRestore, previousWorld);
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

        deleteIfExists(restore.previousWorld());
        if (Files.exists(restore.worldPath())) {
            Files.move(restore.worldPath(), restore.previousWorld(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(restore.tempRestore(), restore.worldPath(), StandardCopyOption.REPLACE_EXISTING);
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
