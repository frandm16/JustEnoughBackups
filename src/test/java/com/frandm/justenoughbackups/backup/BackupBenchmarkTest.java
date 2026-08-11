package com.frandm.justenoughbackups.backup;

import com.frandm.justenoughbackups.backup.BackupConstants;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupStatus;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.progress.BackupProgressListener;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;
import com.frandm.justenoughbackups.backup.storage.WorldSnapshotter;
import com.frandm.justenoughbackups.config.BackupConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class BackupBenchmarkTest {

    @TempDir
    Path tempWorkingDir;

    private Path testWorldPath;
    private Path testBackupRootDir;
    private boolean isCustomWorld = false;

    @BeforeEach
    void setUp() throws IOException {
        String customPath = System.getProperty("test.world.path", System.getenv("TEST_WORLD_PATH"));
        if (customPath != null && !customPath.isBlank()) {
            Path customWorldDir = Paths.get(customPath);
            if (Files.isDirectory(customWorldDir)) {
                testWorldPath = customWorldDir;
                isCustomWorld = true;
            }
        }

        if (!isCustomWorld) {
            testWorldPath = tempWorkingDir.resolve("TestWorld");
            generateSyntheticWorld(testWorldPath, 40, 3 * 1024 * 1024);
        }

        String customBackupPath = System.getProperty("test.backup.path", System.getenv("TEST_BACKUP_PATH"));
        Path backupRoot = tempWorkingDir.resolve("backups");
        if (customBackupPath != null && !customBackupPath.isBlank()) {
            backupRoot = Paths.get(customBackupPath).toAbsolutePath();
        }
        Files.createDirectories(backupRoot);
        testBackupRootDir = backupRoot;
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testBackupRootDir)) {
            deleteRecursively(testBackupRootDir);
        }
    }

    @Test
    void rejectsTempDirectoryOverlappingWorld() throws IOException {
        Path worldParent = tempWorkingDir.resolve("OverlapWorld");
        Files.createDirectories(worldParent);
        Files.writeString(worldParent.resolve("level.dat"), "Synthetic Level Data");

        BackupConfig config = new BackupConfig();
        config.backupDirectory = tempWorkingDir.resolve("backups").toAbsolutePath().toString();
        config.tempBackupDirectory = worldParent.toAbsolutePath().toString();
        config.threadCount = 1;
        config.minimumFreeSpaceReserveMb = 0;
        BackupConfig.setCurrent(config);

        String worldName = "OverlapWorld";
        String worldDirName = "OverlapWorld";
        Path backupDir = config.resolveBackupRoot().resolve(worldDirName);
        Files.createDirectories(backupDir);
        try {
            Map<String, BackupManifest.FileState> snapshot = WorldSnapshotter.snapshot(
                    worldParent,
                    BackupType.FULL,
                    "Overlap Test",
                    BackupProgressListener.noop()
            );
            assertThrows(IOException.class, () -> BackupStorage.writeBackup(
                    worldParent,
                    backupDir,
                    worldName,
                    worldDirName,
                    BackupType.FULL,
                    null,
                    snapshot,
                    "Overlap Test",
                    config,
                    "",
                    BackupProgressListener.noop()
            ));
        } finally {
            if (Files.exists(backupDir)) {
                deleteRecursively(backupDir);
            }
        }
    }

    @Test
    void benchmarkFullBackupPerformanceThreads1VsN() throws IOException {
        List<Integer> threadCounts = candidateThreadCounts();
        List<RunResult> results = new ArrayList<>();

        for (int threads : threadCounts) {
            BackupConfig config = new BackupConfig();
            config.backupDirectory = testBackupRootDir.resolve("threads-" + threads).toAbsolutePath().toString();
            config.threadCount = threads;
            config.minimumFreeSpaceReserveMb = 0;
            BackupConfig.setCurrent(config);
            results.add(runBenchmark(config, threads));
        }

        printTable(results);
        assertSpeedup(results);
    }

    @Test
    void writesTempZipInConfiguredTempDirectoryAndPublishesToBackupDir() throws IOException {
        Path tempRoot = tempWorkingDir.resolve("scratch");
        Path backupRoot = tempWorkingDir.resolve("backup-destination");
        Files.createDirectories(tempRoot);
        Files.createDirectories(backupRoot);

        BackupConfig config = new BackupConfig();
        config.backupDirectory = backupRoot.toAbsolutePath().toString();
        config.tempBackupDirectory = tempRoot.toAbsolutePath().toString();
        config.threadCount = 1;
        config.minimumFreeSpaceReserveMb = 0;
        config.includeSummaryFile = false;
        BackupConfig.setCurrent(config);

        String worldName = "TempWorld";
        String worldDirName = "TempWorld";
        Path backupDir = config.resolveBackupRoot().resolve(worldDirName);
        Files.createDirectories(backupDir);

        try {
            Map<String, BackupManifest.FileState> snapshot = WorldSnapshotter.snapshot(
                    testWorldPath,
                    BackupType.FULL,
                    "Temp Test",
                    BackupProgressListener.noop()
            );
            BackupManifest manifest = BackupStorage.writeBackup(
                    testWorldPath,
                    backupDir,
                    worldName,
                    worldDirName,
                    BackupType.FULL,
                    null,
                    snapshot,
                    "Temp Test",
                    config,
                    "",
                    BackupProgressListener.noop()
            );

            Path published = backupDir.resolve(manifest.zipFileName);
            assertTrue(Files.isRegularFile(published), "Published backup should exist in backup directory");

            Path tempDir = config.resolveTempRoot().resolve(worldDirName);
            assertTrue(Files.isDirectory(tempDir), "Temporary directory should be created");
            try (var stream = Files.list(tempDir)) {
                assertFalse(stream.anyMatch(file -> file.getFileName().toString().endsWith(".tmp")),
                        "No leftover .tmp file should remain after publishing");
            }
            try (var stream = Files.list(backupDir)) {
                assertFalse(stream.anyMatch(file -> file.getFileName().toString().endsWith(".tmp")),
                        "No .tmp file should remain in the backup directory");
            }
            verifyBackupIntegrity(published, manifest.snapshot);
        } finally {
            if (Files.exists(backupDir)) {
                deleteRecursively(backupDir);
            }
        }
    }

    private RunResult runBenchmark(BackupConfig config, int threads) throws IOException {
        String worldName = "BenchmarkWorld";
        String worldDirName = "BenchmarkWorld";
        Path backupDir = config.resolveBackupRoot().resolve(worldDirName);
        Files.createDirectories(backupDir);
        try {
            return runBenchmarkIn(config, threads, worldName, worldDirName, backupDir);
        } finally {
            if (Files.exists(backupDir)) {
                deleteRecursively(backupDir);
            }
        }
    }

    private RunResult runBenchmarkIn(BackupConfig config, int threads, String worldName, String worldDirName, Path backupDir) throws IOException {
        long scanStart = System.nanoTime();
        Map<String, BackupManifest.FileState> snapshot = WorldSnapshotter.snapshot(
                testWorldPath,
                BackupType.FULL,
                "Benchmark Test",
                BackupProgressListener.noop()
        );
        long scanDurationMs = (System.nanoTime() - scanStart) / 1_000_000;

        long compressStart = System.nanoTime();
        BackupManifest manifest = BackupStorage.writeBackup(
                testWorldPath,
                backupDir,
                worldName,
                worldDirName,
                BackupType.FULL,
                null,
                snapshot,
                "Benchmark Run",
                config,
                "benchmark_test.zip",
                BackupProgressListener.noop()
        );
        long compressDurationMs = (System.nanoTime() - compressStart) / 1_000_000;

        assertNotNull(manifest);
        assertFalse(snapshot.isEmpty());

        Path generatedZip = backupDir.resolve(manifest.zipFileName);
        assertTrue(Files.exists(generatedZip), "Backup ZIP should exist");
        verifyBackupIntegrity(generatedZip, manifest.snapshot);

        BackupStatus status = BackupStorage.readStatus(generatedZip);
        assertNotNull(status, "Backup should carry a status entry");
        assertTrue(status.completed, "Backup should complete without errors (threads=" + threads + ")");
        assertTrue(status.brokenFiles.isEmpty(), "Backup should have no broken files (threads=" + threads + ")");

        long totalSizeBytes = snapshot.values().stream().mapToLong(state -> state.size).sum();
        return new RunResult(threads, scanDurationMs, compressDurationMs, snapshot.size(), totalSizeBytes);
    }

    private void verifyBackupIntegrity(Path zipFile, Map<String, BackupManifest.FileState> snapshot) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            int fileEntryCount = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()
                        && !BackupConstants.DATA_ENTRY.equals(entry.getName())
                        && !BackupConstants.MANIFEST_ENTRY.equals(entry.getName())
                        && !BackupConstants.STATUS_ENTRY.equals(entry.getName())
                        && !BackupConstants.SUMMARY_ENTRY.equals(entry.getName())) {
                    fileEntryCount++;
                }
            }
            assertEquals(snapshot.size(), fileEntryCount, "ZIP file entry count should match snapshot size");

            for (Map.Entry<String, BackupManifest.FileState> snapshotEntry : snapshot.entrySet()) {
                ZipEntry entry = zip.getEntry(snapshotEntry.getKey());
                assertNotNull(entry, "ZIP is missing entry: " + snapshotEntry.getKey());
                HashAndSize extracted;
                try (InputStream in = zip.getInputStream(entry)) {
                    extracted = hashAndSize(in);
                }
                BackupManifest.FileState expected = snapshotEntry.getValue();
                assertEquals(expected.size, extracted.size(), "Size mismatch for " + snapshotEntry.getKey());
                assertEquals(expected.sha256, extracted.sha256(), "SHA-256 mismatch for " + snapshotEntry.getKey());
            }
        }
    }

    private List<Integer> candidateThreadCounts() {
        int max = Math.max(1, Runtime.getRuntime().availableProcessors());
        return List.of(1, 4, 8, max).stream()
                .distinct()
                .filter(count -> count <= max)
                .sorted()
                .toList();
    }

    private void assertSpeedup(List<RunResult> results) {
        assumeTrue(Runtime.getRuntime().availableProcessors() >= 4,
                "At least 4 CPU cores are required for the 1 vs N thread speedup comparison");

        RunResult single = results.stream().filter(result -> result.threads == 1).findFirst().orElseThrow();
        RunResult multi = results.stream().filter(result -> result.threads >= 4).findFirst().orElseThrow();
        assertTrue(single.totalMs() > multi.totalMs() * 0.8,
                "Multithreaded backup should be at least 20% faster (1 thread=" + single.totalMs()
                        + " ms, " + multi.threads() + " threads=" + multi.totalMs() + " ms)");
    }

    private void printTable(List<RunResult> results) {
        System.out.println("------------------------------------------------------------------------");
        System.out.println("BACKUP BENCHMARK 1 vs N THREADS ("
                + (isCustomWorld ? "Real World" : "Synthetic World")
                + " | CPU cores: " + Runtime.getRuntime().availableProcessors() + ")");
        System.out.println("------------------------------------------------------------------------");
        System.out.printf(" Threads |  Scan (ms) | Compress (ms) |  Total (ms) | Files | Size (MB)%n");
        System.out.println("------------------------------------------------------------------------");
        for (RunResult result : results) {
            System.out.printf(" %7d | %10d | %13d | %11d | %5d | %9.2f%n",
                    result.threads,
                    result.scanMs(),
                    result.compressMs(),
                    result.totalMs(),
                    result.files(),
                    result.sizeBytes() / (1024.0 * 1024.0));
        }
        System.out.println("------------------------------------------------------------------------");
    }

    private void generateSyntheticWorld(Path targetDir, int fileCount, int sizePerFile) throws IOException {
        Files.createDirectories(targetDir.resolve("region"));
        Files.createDirectories(targetDir.resolve("entities"));
        Files.createDirectories(targetDir.resolve("poi"));
        Files.createDirectories(targetDir.resolve("data"));

        Random random = new Random(42);
        byte[] buffer = new byte[8192];

        for (int i = 0; i < fileCount; i++) {
            String folder = switch (i % 4) {
                case 0 -> "region/r." + (i / 4) + ".0.mca";
                case 1 -> "entities/r." + (i / 4) + ".0.mca";
                case 2 -> "poi/r." + (i / 4) + ".0.mca";
                default -> "data/map_" + i + ".dat";
            };

            Path file = targetDir.resolve(folder);
            Files.createDirectories(file.getParent());

            int written = 0;
            try (var out = Files.newOutputStream(file)) {
                while (written < sizePerFile) {
                    random.nextBytes(buffer);
                    int toWrite = Math.min(buffer.length, sizePerFile - written);
                    out.write(buffer, 0, toWrite);
                    written += toWrite;
                }
            }
        }

        Files.writeString(targetDir.resolve("level.dat"), "Synthetic Level Data Placeholder");
    }

    private void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static HashAndSize hashAndSize(InputStream in) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0L;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                size += read;
            }
            return new HashAndSize(toHex(digest.digest()), size);
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

    private record HashAndSize(String sha256, long size) {
    }

    private record RunResult(int threads, long scanMs, long compressMs, int files, long sizeBytes) {
        private long totalMs() {
            return scanMs + compressMs;
        }
    }
}
