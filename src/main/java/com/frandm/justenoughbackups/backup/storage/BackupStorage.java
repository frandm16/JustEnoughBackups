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
import com.frandm.justenoughbackups.backup.progress.BackupProgressPhase;
import com.frandm.justenoughbackups.backup.progress.BackupProgressState;
import com.frandm.justenoughbackups.backup.retention.RetentionPolicy;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.frandm.justenoughbackups.backup.parallel.BackupThreadPool;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipMethod;

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
        Map<String, BackupManifest.FileState> snapshot = WorldSnapshotter.snapshot(worldPath, type, reason, progressListener);
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

    public static BackupManifest writeBackup(
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

        ensureSufficientDiskSpace(backupDir, snapshot, includedFiles, config);
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
            List<ZipEntry> validEntries = new ArrayList<>();
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()
                        || BackupConstants.DATA_ENTRY.equals(entry.getName())
                        || BackupConstants.MANIFEST_ENTRY.equals(entry.getName())
                        || BackupConstants.STATUS_ENTRY.equals(entry.getName())) {
                    continue;
                }
                validEntries.add(entry);
            }

            int threadCount = Math.clamp(BackupConfig.get().threadCount, 1, Math.max(1, Runtime.getRuntime().availableProcessors()));
            if (threadCount <= 1 || validEntries.size() <= 1) {
                for (ZipEntry entry : validEntries) {
                    Path target = normalizedTarget.resolve(entry.getName()).normalize();
                    if (!target.startsWith(normalizedTarget)) {
                        throw new IOException("Backup contains an unsafe path: " + entry.getName());
                    }
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } else {
                ExecutorService executor = BackupThreadPool.getExecutor();
                List<Callable<Void>> tasks = new ArrayList<>(validEntries.size());
                for (ZipEntry entry : validEntries) {
                    tasks.add(() -> {
                        Path target = normalizedTarget.resolve(entry.getName()).normalize();
                        if (!target.startsWith(normalizedTarget)) {
                            throw new IOException("Backup contains an unsafe path: " + entry.getName());
                        }
                        Files.createDirectories(target.getParent());
                        try (InputStream in = zipFile.getInputStream(entry)) {
                            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        return null;
                    });
                }
                try {
                    var futures = executor.invokeAll(tasks);
                    for (var future : futures) {
                        future.get();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Extraction interrupted", e);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException ioEx) {
                        throw ioEx;
                    }
                    throw new IOException("Failed during parallel extraction", e.getCause());
                }
            }
        }
    }

    private record ReadResult(String relativeName, String sha256, long bytes, long lastModified) {
    }

    private static final class FileFailure extends IOException {
        private final String relativeName;

        private FileFailure(String relativeName, IOException cause) {
            super(cause);
            this.relativeName = relativeName;
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

        List<String> orderedFiles = new ArrayList<>(includedFiles);
        Collections.sort(orderedFiles);

        int threadCount = Math.clamp(BackupConfig.get().threadCount, 1, Math.max(1, Runtime.getRuntime().availableProcessors()));

        Map<String, ReadResult> hashedFiles;
        if (threadCount <= 1 || orderedFiles.size() <= 1) {
            hashedFiles = hashFilesSerially(worldPath, orderedFiles, status, integrityMode, progress);
        } else {
            hashedFiles = hashFilesInParallel(worldPath, orderedFiles, status, integrityMode, progress);
        }

        List<String> toWrite = new ArrayList<>();
        for (String relativeName : orderedFiles) {
            if (hashedFiles.containsKey(relativeName)) {
                toWrite.add(relativeName);
            }
        }

        try (OutputStream fileOut = Files.newOutputStream(backupFile);
             ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(fileOut)) {
            zipOut.setUseZip64(Zip64Mode.AsNeeded);
            if (threadCount <= 1 || toWrite.size() <= 1) {
                writeEntriesSerially(worldPath, zipOut, toWrite, hashedFiles, manifest, status, finalSnapshot, integrityMode, progress);
            } else {
                writeEntriesInParallel(worldPath, zipOut, toWrite, hashedFiles, manifest, status, finalSnapshot, progress);
            }

            manifest.snapshot.clear();
            manifest.snapshot.putAll(finalSnapshot);
            writeSummaryAndMetadata(zipOut, manifest, status, includeSummaryFile);
        }

        progress.emit(status.completed ? BackupProgressState.COMPLETED : BackupProgressState.FAILED, true);
    }

    private static Map<String, ReadResult> hashFilesSerially(
            Path worldPath,
            List<String> orderedFiles,
            BackupStatus status,
            BackupIntegrityMode integrityMode,
            ProgressTracker progress
    ) throws IOException {
        Map<String, ReadResult> hashedFiles = new LinkedHashMap<>();
        for (String relativeName : orderedFiles) {
            try {
                hashedFiles.put(relativeName, hashFile(worldPath, relativeName));
            } catch (IOException exception) {
                handleBrokenFile(status, relativeName, exception, integrityMode, progress);
            }
        }
        return hashedFiles;
    }

    private static Map<String, ReadResult> hashFilesInParallel(
            Path worldPath,
            List<String> orderedFiles,
            BackupStatus status,
            BackupIntegrityMode integrityMode,
            ProgressTracker progress
    ) throws IOException {
        ExecutorService executor = BackupThreadPool.getExecutor();
        ExecutorCompletionService<ReadResult> completionService = new ExecutorCompletionService<>(executor);
        Map<String, ReadResult> hashedFiles = new ConcurrentHashMap<>();

        int total = orderedFiles.size();
        int submitted = 0;
        int inFlight = 0;
        int maxInFlight = Math.max(2, BackupConfig.get().threadCount) * 2;

        while (submitted < total || inFlight > 0) {
            while (submitted < total && inFlight < maxInFlight) {
                String relativeName = orderedFiles.get(submitted++);
                completionService.submit(() -> {
                    try {
                        return hashFile(worldPath, relativeName);
                    } catch (IOException exception) {
                        throw new FileFailure(relativeName, exception);
                    }
                });
                inFlight++;
            }

            Future<ReadResult> future;
            try {
                future = completionService.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Hash calculation interrupted", exception);
            }
            inFlight--;
            try {
                ReadResult result = future.get();
                hashedFiles.put(result.relativeName(), result);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Hash calculation interrupted", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof FileFailure failure) {
                    handleBrokenFile(status, failure.relativeName, (IOException) failure.getCause(), integrityMode, progress);
                    continue;
                }
                if (cause instanceof IOException ioException) {
                    throw new IOException("Failed during parallel hashing", ioException);
                }
                throw new IOException("Failed during parallel hashing", cause);
            }
        }
        return hashedFiles;
    }

    private static ReadResult hashFile(Path worldPath, String relativeName) throws IOException {
        Path file = worldPath.resolve(relativeName);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Included file is missing: " + file);
        }

        long bytes = Files.size(file);
        long lastModified = Files.getLastModifiedTime(file).toMillis();

        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        return new ReadResult(relativeName, toHex(digest.digest()), bytes, lastModified);
    }

    private static void handleBrokenFile(
            BackupStatus status,
            String relativeName,
            IOException exception,
            BackupIntegrityMode integrityMode,
            ProgressTracker progress
    ) throws IOException {
        status.completed = false;
        status.brokenFiles.add(new BackupStatus.BrokenFile(relativeName, rootMessage(exception)));
        progress.emit(BackupProgressState.FAILED, true);
        if (integrityMode == BackupIntegrityMode.STRICT) {
            throw exception;
        }
        WorldBackupMod.LOGGER.warn("Keeping partial backup after failing to write {}", relativeName, exception);
    }

    private static void writeEntriesSerially(
            Path worldPath,
            ZipArchiveOutputStream zipOut,
            List<String> files,
            Map<String, ReadResult> hashedFiles,
            BackupManifest manifest,
            BackupStatus status,
            Map<String, BackupManifest.FileState> finalSnapshot,
            BackupIntegrityMode integrityMode,
            ProgressTracker progress
    ) throws IOException {
        for (String relativeName : files) {
            ReadResult result = hashedFiles.get(relativeName);
            try {
                writeSingleEntry(worldPath, zipOut, result, manifest, status, finalSnapshot, progress);
            } catch (IOException exception) {
                handleBrokenFile(status, relativeName, exception, integrityMode, progress);
            }
        }
    }

    private static void writeEntriesInParallel(
            Path worldPath,
            ZipArchiveOutputStream zipOut,
            List<String> files,
            Map<String, ReadResult> hashedFiles,
            BackupManifest manifest,
            BackupStatus status,
            Map<String, BackupManifest.FileState> finalSnapshot,
            ProgressTracker progress
    ) throws IOException {
        int threads = Math.clamp(BackupConfig.get().threadCount, 1, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = newCompressExecutor(threads);
        try {
            ParallelScatterZipCreator creator = new ParallelScatterZipCreator(executor);
            for (String relativeName : files) {
                Path file = worldPath.resolve(relativeName);
                ZipArchiveEntry entry = new ZipArchiveEntry(relativeName.replace('\\', '/'));
                entry.setMethod(ZipMethod.DEFLATED.getCode());
                creator.addArchiveEntry(entry, () -> {
                    try {
                        return Files.newInputStream(file);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
            }
            try {
                creator.writeTo(zipOut);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Compression interrupted", exception);
            } catch (ExecutionException exception) {
                if (exception.getCause() instanceof IOException ioException) {
                    throw ioException;
                }
                throw new IOException("Failed during parallel compression", exception.getCause());
            }
            for (String relativeName : files) {
                recordWrittenFile(manifest, status, finalSnapshot, progress, hashedFiles.get(relativeName));
            }
        } finally {
            executor.shutdown();
        }
    }

    private static ExecutorService newCompressExecutor(int threads) {
        return Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "JEB-Compress-" + counter.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                return thread;
            }
        });
    }

    private static void writeSingleEntry(
            Path worldPath,
            ZipArchiveOutputStream zipOut,
            ReadResult result,
            BackupManifest manifest,
            BackupStatus status,
            Map<String, BackupManifest.FileState> finalSnapshot,
            ProgressTracker progress
    ) throws IOException {
        Path file = worldPath.resolve(result.relativeName());
        if (!Files.isRegularFile(file)) {
            throw new IOException("Included file is missing: " + file);
        }

        ZipArchiveEntry entry = new ZipArchiveEntry(result.relativeName().replace('\\', '/'));
        zipOut.putArchiveEntry(entry);
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(zipOut);
        } finally {
            zipOut.closeArchiveEntry();
        }
        recordWrittenFile(manifest, status, finalSnapshot, progress, result);
    }

    private static void recordWrittenFile(
            BackupManifest manifest,
            BackupStatus status,
            Map<String, BackupManifest.FileState> finalSnapshot,
            ProgressTracker progress,
            ReadResult result
    ) {
        manifest.includedBytes += result.bytes();
        status.totalBytes += result.bytes();
        status.files.add(new BackupStatus.FileEntry(result.relativeName(), result.bytes(), result.sha256()));
        finalSnapshot.put(result.relativeName(), new BackupManifest.FileState(result.bytes(), result.lastModified(), result.sha256()));
        progress.fileCompleted();
    }

    private static void writeSummaryAndMetadata(
            ZipArchiveOutputStream zipOut,
            BackupManifest manifest,
            BackupStatus status,
            boolean includeSummaryFile
    ) throws IOException {
        if (includeSummaryFile) {
            ZipArchiveEntry summaryEntry = new ZipArchiveEntry(BackupConstants.SUMMARY_ENTRY);
            zipOut.putArchiveEntry(summaryEntry);
            zipOut.write(BackupSummaryFile.build(manifest).getBytes(StandardCharsets.UTF_8));
            zipOut.closeArchiveEntry();
        }

        ZipArchiveEntry dataEntry = new ZipArchiveEntry(BackupConstants.DATA_ENTRY);
        zipOut.putArchiveEntry(dataEntry);
        zipOut.write(GSON.toJson(new BackupMetadata(manifest, status)).getBytes(StandardCharsets.UTF_8));
        zipOut.closeArchiveEntry();
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

    private static void ensureSufficientDiskSpace(
            Path backupDir,
            Map<String, BackupManifest.FileState> snapshot,
            List<String> includedFiles,
            BackupConfig config
    ) throws IOException {
        long includedBytes = includedBytes(snapshot, includedFiles);
        double compressionRatio = lastFullCompressionRatio(backupDir);
        long estimatedZipBytes = (long) Math.ceil(includedBytes * compressionRatio);
        long reserveBytes = Math.max(0L, config.minimumFreeSpaceReserveMb) * 1024L * 1024L;
        long requiredBytes = safeAdd(safeMultiply(estimatedZipBytes, 2L), reserveBytes);
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

    private static long includedBytes(Map<String, BackupManifest.FileState> snapshot, List<String> includedFiles) {
        long total = 0L;
        for (String relativeName : includedFiles) {
            BackupManifest.FileState state = snapshot.get(relativeName);
            if (state != null) {
                total = safeAdd(total, Math.max(0L, state.size));
            }
        }
        return total;
    }

    private static double lastFullCompressionRatio(Path backupDir) {
        try {
            BackupManifest lastFull = readManifests(backupDir).stream()
                    .filter(manifest -> manifest.type == BackupType.FULL)
                    .filter(manifest -> manifest.createdAt != null)
                    .filter(manifest -> manifest.zipFileName != null)
                    .max(Comparator.comparing(manifest -> manifest.createdAt))
                    .orElse(null);
            if (lastFull == null || lastFull.includedBytes <= 0L) {
                return 1.0;
            }
            Path zipFile = backupDir.resolve(lastFull.zipFileName);
            if (!Files.isRegularFile(zipFile)) {
                return 1.0;
            }
            double ratio = (double) Files.size(zipFile) / (double) lastFull.includedBytes;
            if (!Double.isFinite(ratio) || ratio <= 0.0) {
                return 1.0;
            }
            return ratio;
        } catch (IOException exception) {
            return 1.0;
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
                    BackupProgressPhase.COPYING,
                    bytesWritten,
                    totalBytes,
                    filesWritten,
                    totalFiles,
                    state
            ));
        }
    }
}