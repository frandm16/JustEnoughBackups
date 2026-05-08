package com.frandm.advancedbackups.backup.storage;

import com.frandm.advancedbackups.WorldBackupMod;
import com.frandm.advancedbackups.backup.BackupConstants;
import com.frandm.advancedbackups.backup.model.BackupIntegrityMode;
import com.frandm.advancedbackups.backup.model.BackupManifest;
import com.frandm.advancedbackups.backup.model.BackupStatus;
import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.config.BackupConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class BackupStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BackupStorage() {
    }

    public static BackupManifest createBackup(
            Path worldPath,
            String worldName,
            String worldDirectoryName,
            BackupConfig config,
            BackupType type,
            String reason
    ) throws IOException {
        Path backupDir = config.resolveBackupRoot().resolve(worldDirectoryName);
        Files.createDirectories(backupDir);

        List<BackupManifest> manifests = readManifests(backupDir);
        Map<String, BackupManifest.FileState> snapshot = WorldSnapshotter.snapshot(worldPath);
        if (type != BackupType.FULL && hasNoFullBackup(manifests)) {
            BackupManifest fullBase = writeBackup(
                    worldPath,
                    backupDir,
                    worldName,
                    worldDirectoryName,
                    BackupType.FULL,
                    null,
                    snapshot,
                    reason + " base",
                    config.integrityMode
            );
            manifests = new ArrayList<>(manifests);
            manifests.add(fullBase);
        }

        BackupManifest base = findBaseManifest(manifests, type);
        return writeBackup(worldPath, backupDir, worldName, worldDirectoryName, type, base, snapshot, reason, config.integrityMode);
    }

    private static BackupManifest writeBackup(
            Path worldPath,
            Path backupDir,
            String worldName,
            String worldDirectoryName,
            BackupType type,
            BackupManifest base,
            Map<String, BackupManifest.FileState> snapshot,
            String reason,
            BackupIntegrityMode integrityMode
    ) throws IOException {
        List<String> includedFiles = includedFiles(type, snapshot, base);

        String timestamp = LocalDateTime.now().format(BackupConstants.FILE_TIME);
        String id = type.commandName() + "-" + timestamp;
        Path backupFile = backupDir.resolve(id + ".zip");
        Path tempBackupFile = backupDir.resolve(id + ".zip.tmp");

        BackupManifest manifest = new BackupManifest();
        manifest.id = id;
        manifest.type = type;
        manifest.createdAt = Instant.now().toString();
        manifest.worldName = worldName;
        manifest.worldDirectoryName = worldDirectoryName;
        manifest.baseBackupId = base == null ? null : base.id;
        manifest.zipFileName = backupFile.getFileName().toString();
        manifest.reason = reason;
        manifest.integrityStatusEntry = BackupConstants.STATUS_ENTRY;
        manifest.integrityMode = integrityMode;
        manifest.includedFiles.addAll(includedFiles);
        manifest.snapshot.putAll(snapshot);

        writeBackupZipToTemp(worldPath, tempBackupFile, backupFile, manifest, includedFiles, snapshot, integrityMode);
        WorldBackupMod.LOGGER.info("{} backup {} created for reason: {}", type, id, reason);
        return manifest;
    }

    private static boolean hasNoFullBackup(List<BackupManifest> manifests) {
        return manifests.stream().noneMatch(manifest -> manifest.type == BackupType.FULL);
    }

    public static List<BackupManifest> readManifests(Path backupDir) throws IOException {
        if (!Files.isDirectory(backupDir)) {
            return List.of();
        }

        List<BackupManifest> manifests = new ArrayList<>();
        try (var stream = Files.list(backupDir)) {
            for (Path backupFile : stream.filter(path -> path.getFileName().toString().endsWith(".zip")).toList()) {
                BackupManifest manifest = readManifest(backupFile);
                if (manifest != null) {
                    manifest.zipFileName = backupFile.getFileName().toString();
                    manifests.add(manifest);
                }
            }
        }

        return manifests;
    }

    public static void extractBackup(Path backupFile, Path targetDir) throws IOException {
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()
                        || BackupConstants.MANIFEST_ENTRY.equals(entry.getName())
                        || BackupConstants.STATUS_ENTRY.equals(entry.getName())) {
                    continue;
                }

                Path target = normalizedTarget.resolve(entry.getName()).normalize();
                if (!target.startsWith(normalizedTarget)) {
                    throw new IOException("Backup contains an unsafe path: " + entry.getName());
                }

                Files.createDirectories(target.getParent());
                Files.copy(zipFile.getInputStream(entry), target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void writeBackupZip(
            Path worldPath,
            Path backupFile,
            BackupManifest manifest,
            List<String> includedFiles,
            Map<String, BackupManifest.FileState> currentSnapshot,
            BackupIntegrityMode integrityMode
    ) throws IOException {
        BackupStatus status = new BackupStatus();
        status.backupId = manifest.id;
        status.type = manifest.type;
        status.baseBackupId = manifest.baseBackupId;
        status.createdAt = manifest.createdAt;

        Map<String, BackupManifest.FileState> finalSnapshot = new LinkedHashMap<>(currentSnapshot);
        try (OutputStream fileOut = Files.newOutputStream(backupFile);
             ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
            for (String relativeName : includedFiles) {
                Path file = worldPath.resolve(relativeName);
                try {
                    if (!Files.isRegularFile(file)) {
                        throw new IOException("Included file is missing: " + file);
                    }
                    BackupManifest.FileState writtenState = writeFileEntry(file, relativeName, zipOut);
                    manifest.includedBytes += writtenState.size;
                    status.totalBytes += writtenState.size;
                    status.files.add(new BackupStatus.FileEntry(relativeName, writtenState.size, writtenState.sha256));
                    finalSnapshot.put(relativeName, writtenState);
                } catch (IOException exception) {
                    status.completed = false;
                    status.brokenFiles.add(new BackupStatus.BrokenFile(relativeName, rootMessage(exception)));
                    if (integrityMode == BackupIntegrityMode.STRICT) {
                        throw exception;
                    }
                    WorldBackupMod.LOGGER.warn("Keeping partial backup after failing to write {}", relativeName, exception);
                }
            }

            manifest.snapshot.clear();
            manifest.snapshot.putAll(finalSnapshot);

            ZipEntry statusEntry = new ZipEntry(BackupConstants.STATUS_ENTRY);
            zipOut.putNextEntry(statusEntry);
            zipOut.write(GSON.toJson(status).getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();

            ZipEntry manifestEntry = new ZipEntry(BackupConstants.MANIFEST_ENTRY);
            zipOut.putNextEntry(manifestEntry);
            zipOut.write(GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
        }
    }

    private static void writeBackupZipToTemp(
            Path worldPath,
            Path tempBackupFile,
            Path backupFile,
            BackupManifest manifest,
            List<String> includedFiles,
            Map<String, BackupManifest.FileState> currentSnapshot,
            BackupIntegrityMode integrityMode
    ) throws IOException {
        Files.deleteIfExists(tempBackupFile);
        try {
            writeBackupZip(worldPath, tempBackupFile, manifest, includedFiles, currentSnapshot, integrityMode);
            moveCompletedBackup(tempBackupFile, backupFile);
        } catch (IOException exception) {
            Files.deleteIfExists(tempBackupFile);
            throw exception;
        }
    }

    private static void moveCompletedBackup(Path tempBackupFile, Path backupFile) throws IOException {
        try {
            Files.move(
                    tempBackupFile,
                    backupFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempBackupFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static BackupManifest readManifest(Path backupFile) {
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            ZipEntry entry = zipFile.getEntry(BackupConstants.MANIFEST_ENTRY);
            if (entry == null) {
                return null;
            }

            try (Reader reader = new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, BackupManifest.class);
            }
        } catch (IOException | RuntimeException exception) {
            WorldBackupMod.LOGGER.warn("Skipping unreadable backup manifest: {}", backupFile, exception);
            return null;
        }
    }

    public static BackupStatus readStatus(Path backupFile) {
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            ZipEntry entry = zipFile.getEntry(BackupConstants.STATUS_ENTRY);
            if (entry == null) {
                return null;
            }

            try (Reader reader = new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, BackupStatus.class);
            }
        } catch (IOException | RuntimeException exception) {
            WorldBackupMod.LOGGER.warn("Skipping unreadable backup status: {}", backupFile, exception);
            return null;
        }
    }

    private static BackupManifest.FileState writeFileEntry(Path file, String relativeName, ZipOutputStream zipOut) throws IOException {
        MessageDigest digest = newDigest();
        long bytes = 0L;

        ZipEntry entry = new ZipEntry(relativeName.replace('\\', '/'));
        zipOut.putNextEntry(entry);
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                zipOut.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                bytes += read;
            }
        } finally {
            zipOut.closeEntry();
        }

        return new BackupManifest.FileState(
                bytes,
                Files.getLastModifiedTime(file).toMillis(),
                toHex(digest.digest())
        );
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value));
        }
        return builder.toString();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return Objects.requireNonNullElse(message, current.getClass().getSimpleName());
    }

    private static BackupManifest findBaseManifest(List<BackupManifest> manifests, BackupType type) {
        if (type == BackupType.FULL || manifests.isEmpty()) {
            return null;
        }

        return manifests.stream()
                .filter(manifest -> type == BackupType.INCREMENTAL || manifest.type == BackupType.FULL)
                .max(Comparator.comparing(manifest -> manifest.createdAt))
                .orElse(null);
    }

    private static List<String> includedFiles(
            BackupType type,
            Map<String, BackupManifest.FileState> snapshot,
            BackupManifest base
    ) {
        if (type == BackupType.FULL || base == null) {
            return snapshot.keySet().stream().sorted().toList();
        }

        return snapshot.entrySet().stream()
                .filter(entry -> {
                    BackupManifest.FileState previous = base.snapshot.get(entry.getKey());
                    return !entry.getValue().sameContent(previous);
                })
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
