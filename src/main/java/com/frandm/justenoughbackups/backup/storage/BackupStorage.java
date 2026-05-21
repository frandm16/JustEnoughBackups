package com.frandm.justenoughbackups.backup.storage;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.BackupConstants;
import com.frandm.justenoughbackups.backup.BackupSizeFormatter;
import com.frandm.justenoughbackups.backup.model.BackupIntegrityMode;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupMetadata;
import com.frandm.justenoughbackups.backup.model.BackupStatus;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.progress.BackupProgress;
import com.frandm.justenoughbackups.backup.progress.BackupProgressListener;
import com.frandm.justenoughbackups.backup.progress.BackupProgressState;
import com.frandm.justenoughbackups.backup.retention.RetentionPolicy;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.FileStore;
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
        return createBackup(worldPath, worldName, worldDirectoryName, config, type, reason, "", BackupProgressListener.noop());
    }

    public static BackupManifest createBackup(
            Path worldPath,
            String worldName,
            String worldDirectoryName,
            BackupConfig config,
            BackupType type,
            String reason,
            String requestedName,
            BackupProgressListener progressListener
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
                    config,
                    "",
                    progressListener
            );
            manifests = new ArrayList<>(manifests);
            manifests.add(fullBase);
        }

        BackupManifest base = findBaseManifest(manifests, type);
        return writeBackup(worldPath, backupDir, worldName, worldDirectoryName, type, base, snapshot, reason, config, requestedName, progressListener);
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
            BackupConfig config,
            String requestedName,
            BackupProgressListener progressListener
    ) throws IOException {
        List<String> includedFiles = includedFiles(type, snapshot, base);

        String timestamp = LocalDateTime.now().format(BackupConstants.FILE_TIME);
        String id = type.commandName() + "-" + timestamp;
        String zipFileName = resolveBackupFileName(backupDir, requestedName, id + ".zip");
        Path backupFile = backupDir.resolve(zipFileName);
        Path tempBackupFile = backupDir.resolve(zipFileName + ".tmp");

        BackupManifest manifest = new BackupManifest();
        manifest.id = id;
        manifest.type = type;
        manifest.createdAt = Instant.now().toString();
        manifest.worldName = worldName;
        manifest.worldDirectoryName = worldDirectoryName;
        manifest.baseBackupId = base == null ? null : base.id;
        manifest.zipFileName = zipFileName;
        manifest.reason = reason;
        manifest.integrityMode = config.integrityMode;
        manifest.includedFiles.addAll(includedFiles);
        manifest.snapshot.putAll(snapshot);

        ensureSufficientDiskSpace(backupDir, snapshot, config);
        writeBackupZipToTemp(worldPath, backupDir, tempBackupFile, backupFile, manifest, includedFiles, snapshot, config, progressListener);
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
                        || BackupConstants.DATA_ENTRY.equals(entry.getName())
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
            BackupIntegrityMode integrityMode,
            boolean includeSummaryFile,
            BackupProgressListener progressListener
    ) throws IOException {
        BackupStatus status = new BackupStatus();
        status.backupId = manifest.id;
        status.type = manifest.type;
        status.baseBackupId = manifest.baseBackupId;
        status.createdAt = manifest.createdAt;

        Map<String, BackupManifest.FileState> finalSnapshot = new LinkedHashMap<>(currentSnapshot);
        ProgressTracker progress = new ProgressTracker(
                manifest.id,
                manifest.type,
                manifest.reason,
                totalBytes(worldPath, includedFiles),
                includedFiles.size(),
                progressListener
        );
        progress.emit(BackupProgressState.STARTED, true);
        try (OutputStream fileOut = Files.newOutputStream(backupFile);
             ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
            for (String relativeName : includedFiles) {
                Path file = worldPath.resolve(relativeName);
                try {
                    if (!Files.isRegularFile(file)) {
                        throw new IOException("Included file is missing: " + file);
                    }
                    BackupManifest.FileState writtenState = writeFileEntry(file, relativeName, zipOut, progress);
                    manifest.includedBytes += writtenState.size;
                    status.totalBytes += writtenState.size;
                    status.files.add(new BackupStatus.FileEntry(relativeName, writtenState.size, writtenState.sha256));
                    finalSnapshot.put(relativeName, writtenState);
                    progress.fileCompleted();
                } catch (IOException exception) {
                    status.completed = false;
                    status.brokenFiles.add(new BackupStatus.BrokenFile(relativeName, rootMessage(exception)));
                    progress.emit(BackupProgressState.FAILED, true);
                    if (integrityMode == BackupIntegrityMode.STRICT) {
                        throw exception;
                    }
                    WorldBackupMod.LOGGER.warn("Keeping partial backup after failing to write {}", relativeName, exception);
                }
            }

            manifest.snapshot.clear();
            manifest.snapshot.putAll(finalSnapshot);

            if (includeSummaryFile) {
                ZipEntry summaryEntry = new ZipEntry(BackupConstants.SUMMARY_ENTRY);
                zipOut.putNextEntry(summaryEntry);
                zipOut.write(BackupSummaryFile.build(manifest).getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
            }

            ZipEntry dataEntry = new ZipEntry(BackupConstants.DATA_ENTRY);
            zipOut.putNextEntry(dataEntry);
            zipOut.write(GSON.toJson(new BackupMetadata(manifest, status)).getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
        }
        progress.emit(status.completed ? BackupProgressState.COMPLETED : BackupProgressState.FAILED, true);
    }

    private static void writeBackupZipToTemp(
            Path worldPath,
            Path backupDir,
            Path tempBackupFile,
            Path backupFile,
            BackupManifest manifest,
            List<String> includedFiles,
            Map<String, BackupManifest.FileState> currentSnapshot,
            BackupConfig config,
            BackupProgressListener progressListener
    ) throws IOException {
        Files.deleteIfExists(tempBackupFile);
        try {
            writeBackupZip(worldPath, tempBackupFile, manifest, includedFiles, currentSnapshot, config.integrityMode, config.includeSummaryFile, progressListener);
            enforceSpaceLimitBeforePublish(backupDir, tempBackupFile, manifest, config);
            moveCompletedBackup(tempBackupFile, backupFile);
        } catch (IOException exception) {
            Files.deleteIfExists(tempBackupFile);
            throw exception;
        }
    }

    private static void enforceSpaceLimitBeforePublish(Path backupDir, Path tempBackupFile, BackupManifest pendingManifest, BackupConfig config) throws IOException {
        long pendingBytes = Files.exists(tempBackupFile) ? Files.size(tempBackupFile) : 0L;
        RetentionPolicy.RetentionDecision decision = RetentionPolicy.planWithPending(
                backupDir,
                readManifests(backupDir),
                config,
                pendingManifest,
                pendingBytes
        );
        if (decision.exceedsSpaceLimit()) {
            throw new IOException("Backup exceeds the configured per-world size limit (" + config.retention.maxTotalSizeMb + " MB).");
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
        BackupMetadata metadata = readMetadata(backupFile);
        if (metadata == null) {
            return null;
        }

        BackupManifest manifest = metadata.manifest;
        if (manifest == null) {
            WorldBackupMod.LOGGER.warn("Skipping backup with empty manifest: {}", backupFile);
            return null;
        }
        if (manifest.id == null || manifest.id.isBlank()) {
            WorldBackupMod.LOGGER.warn("Skipping backup with missing id in manifest: {}", backupFile);
            return null;
        }
        if (manifest.type == null) {
            WorldBackupMod.LOGGER.warn("Skipping backup with missing type in manifest: {}", backupFile);
            return null;
        }
        return manifest;
    }

    public static BackupStatus readStatus(Path backupFile) {
        BackupMetadata metadata = readMetadata(backupFile);
        return metadata == null ? null : metadata.status;
    }

    public static boolean hasSummaryFile(Path backupFile) {
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            return zipFile.getEntry(BackupConstants.SUMMARY_ENTRY) != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void ensureSufficientDiskSpace(Path backupDir, Map<String, BackupManifest.FileState> snapshot, BackupConfig config) throws IOException {
        long worldBytes = snapshot.values().stream()
                .mapToLong(fileState -> Math.max(0L, fileState.size))
                .sum();
        long reserveBytes = Math.max(0L, config.minimumFreeSpaceReserveMb) * 1024L * 1024L;
        long requiredBytes = safeAdd(safeMultiply(worldBytes, 2L), reserveBytes);
        FileStore fileStore = Files.getFileStore(backupDir);
        long availableBytes = Math.max(0L, fileStore.getUsableSpace());

        if (availableBytes < requiredBytes) {
            throw new IOException("Insufficient disk space for backup. Required "
                    + BackupSizeFormatter.formatBytes(requiredBytes)
                    + ", available "
                    + BackupSizeFormatter.formatBytes(availableBytes)
                    + ", destination "
                    + backupDir.toAbsolutePath().normalize());
        }
    }

    private static BackupMetadata readMetadata(Path backupFile) {
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            ZipEntry entry = zipFile.getEntry(BackupConstants.DATA_ENTRY);
            if (entry == null) {
                return null;
            }

            try (Reader reader = new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, BackupMetadata.class);
            }
        } catch (IOException | RuntimeException exception) {
            WorldBackupMod.LOGGER.warn("Skipping unreadable backup metadata: {}", backupFile, exception);
            return null;
        }
    }

    public static void renameBackup(Path backupDir, String backupId, String requestedName) throws IOException {
        BackupManifest manifest = findById(backupDir, backupId);
        String newFileName = sanitizeBackupFileName(requestedName);
        Path normalizedBackupDir = backupDir.toAbsolutePath().normalize();
        Path current = normalizedBackupDir.resolve(manifest.zipFileName).normalize();
        Path target = normalizedBackupDir.resolve(newFileName).normalize();
        if (!target.getParent().equals(normalizedBackupDir)) {
            throw new IOException("Backup name escapes backup directory.");
        }
        if (Files.exists(target)) {
            throw new IOException("Backup already exists: " + newFileName);
        }
        Files.move(current, target);
    }

    public static void deleteBackup(Path backupDir, String backupId) throws IOException {
        BackupManifest manifest = findById(backupDir, backupId);
        Files.delete(backupDir.resolve(manifest.zipFileName));
    }

    public static String displayName(BackupManifest manifest) {
        String fileName = manifest.zipFileName == null || manifest.zipFileName.isBlank()
                ? manifest.id
                : manifest.zipFileName;
        return fileName.endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }

    public static BackupManifest findByZipName(Path backupDir, String input) throws IOException {
        String value = input == null ? "" : input.trim();
        if (value.isBlank()) {
            throw new IOException("Backup not found: " + input);
        }

        List<BackupManifest> matches = new ArrayList<>();
        for (BackupManifest manifest : readManifests(backupDir)) {
            if (Objects.equals(manifest.zipFileName, value) || Objects.equals(displayName(manifest), value)) {
                matches.add(manifest);
            }
        }
        if (matches.isEmpty()) {
            throw new IOException("Backup not found: " + input);
        }
        if (matches.size() > 1) {
            throw new IOException("Backup name is ambiguous: " + input);
        }
        return matches.getFirst();
    }

    public static BackupManifest findById(Path backupDir, String backupId) throws IOException {
        for (BackupManifest manifest : readManifests(backupDir)) {
            if (Objects.equals(manifest.id, backupId)) {
                return manifest;
            }
        }
        throw new IOException("Backup not found: " + backupId);
    }

    private static String resolveBackupFileName(Path backupDir, String requestedName, String fallbackFileName) throws IOException {
        if (requestedName == null || requestedName.isBlank()) {
            return fallbackFileName;
        }
        String fileName = sanitizeBackupFileName(requestedName);
        if (Files.exists(backupDir.resolve(fileName))) {
            throw new IOException("Backup already exists: " + fileName);
        }
        return fileName;
    }

    private static String sanitizeBackupFileName(String requestedName) throws IOException {
        String value = requestedName == null ? "" : requestedName.trim();
        if (value.endsWith(".zip")) {
            value = value.substring(0, value.length() - 4);
        }
        if (value.isBlank()
                || value.equals(".")
                || value.equals("..")
                || value.contains("..")
                || value.contains("/")
                || value.contains("\\")
                || value.endsWith(".zip.tmp")) {
            throw new IOException("Invalid backup name.");
        }

        String cleaned = value.replaceAll("[^a-zA-Z0-9._ -]", "_").trim();
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..") || cleaned.contains("..")) {
            throw new IOException("Invalid backup name.");
        }
        return cleaned + ".zip";
    }

    private static BackupManifest.FileState writeFileEntry(Path file, String relativeName, ZipOutputStream zipOut, ProgressTracker progress) throws IOException {
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
                progress.bytesWritten(read);
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

    private static long totalBytes(Path worldPath, List<String> includedFiles) {
        long total = 0L;
        for (String relativeName : includedFiles) {
            Path file = worldPath.resolve(relativeName);
            if (Files.isRegularFile(file)) {
                try {
                    total += Files.size(file);
                } catch (IOException ignored) {
                    // The write loop will report the concrete failure for this file.
                }
            }
        }
        return total;
    }

    private static long safeMultiply(long value, long multiplier) {
        if (value <= 0L || multiplier <= 0L) {
            return 0L;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private static long safeAdd(long left, long right) {
        if (left >= Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static BackupManifest findBaseManifest(List<BackupManifest> manifests, BackupType type) {
        if (type == BackupType.FULL || manifests.isEmpty()) {
            return null;
        }

        return manifests.stream()
                .filter(manifest -> type == BackupType.PARTIAL || manifest.type == BackupType.FULL)
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

    private static final class ProgressTracker {
        private static final long MIN_UPDATE_INTERVAL_MILLIS = 500L;

        private final String backupId;
        private final BackupType type;
        private final String reason;
        private final long totalBytes;
        private final int totalFiles;
        private final BackupProgressListener listener;
        private long bytesWritten;
        private int filesWritten;
        private long lastUpdateMillis;
        private int lastPercent = -1;

        private ProgressTracker(
                String backupId,
                BackupType type,
                String reason,
                long totalBytes,
                int totalFiles,
                BackupProgressListener listener
        ) {
            this.backupId = backupId;
            this.type = type;
            this.reason = reason;
            this.totalBytes = totalBytes;
            this.totalFiles = totalFiles;
            this.listener = listener;
        }

        private void bytesWritten(long bytes) {
            bytesWritten += bytes;
            emit(BackupProgressState.RUNNING, false);
        }

        private void fileCompleted() {
            filesWritten++;
            emit(BackupProgressState.RUNNING, false);
        }

        private void emit(BackupProgressState state, boolean force) {
            long now = System.currentTimeMillis();
            int percent = totalBytes <= 0L ? 100 : (int) Math.min(100L, (bytesWritten * 100L) / totalBytes);
            if (!force && now - lastUpdateMillis < MIN_UPDATE_INTERVAL_MILLIS && percent == lastPercent) {
                return;
            }

            lastUpdateMillis = now;
            lastPercent = percent;
            listener.onProgress(new BackupProgress(
                    backupId,
                    type,
                    reason,
                    bytesWritten,
                    totalBytes,
                    filesWritten,
                    totalFiles,
                    state
            ));
        }
    }
}
