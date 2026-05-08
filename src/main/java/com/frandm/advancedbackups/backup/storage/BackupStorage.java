package com.frandm.advancedbackups.backup.storage;

import com.frandm.advancedbackups.WorldBackupMod;
import com.frandm.advancedbackups.backup.BackupConstants;
import com.frandm.advancedbackups.backup.model.BackupManifest;
import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.config.BackupConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        BackupManifest base = findBaseManifest(manifests, type);
        Map<String, BackupManifest.FileState> snapshot = WorldSnapshotter.snapshot(worldPath);
        List<String> includedFiles = includedFiles(type, snapshot, base);

        String timestamp = LocalDateTime.now().format(BackupConstants.FILE_TIME);
        String id = type.commandName() + "-" + timestamp;
        Path backupFile = backupDir.resolve(id + ".zip");

        BackupManifest manifest = new BackupManifest();
        manifest.id = id;
        manifest.type = type;
        manifest.createdAt = Instant.now().toString();
        manifest.worldName = worldName;
        manifest.worldDirectoryName = worldDirectoryName;
        manifest.baseBackupId = base == null ? null : base.id;
        manifest.zipFileName = backupFile.getFileName().toString();
        manifest.includedFiles.addAll(includedFiles);
        manifest.snapshot.putAll(snapshot);

        writeBackupZip(worldPath, backupFile, manifest, includedFiles);
        WorldBackupMod.LOGGER.info("{} backup {} created for reason: {}", type, id, reason);
        return manifest;
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
                if (entry.isDirectory() || BackupConstants.MANIFEST_ENTRY.equals(entry.getName())) {
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
            List<String> includedFiles
    ) throws IOException {
        try (OutputStream fileOut = Files.newOutputStream(backupFile);
             ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
            for (String relativeName : includedFiles) {
                Path file = worldPath.resolve(relativeName);
                if (!Files.isRegularFile(file)) {
                    continue;
                }

                ZipEntry entry = new ZipEntry(relativeName.replace('\\', '/'));
                zipOut.putNextEntry(entry);
                manifest.includedBytes += Files.copy(file, zipOut);
                zipOut.closeEntry();
            }

            ZipEntry manifestEntry = new ZipEntry(BackupConstants.MANIFEST_ENTRY);
            zipOut.putNextEntry(manifestEntry);
            zipOut.write(GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
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
