package com.frandm.justenoughbackups.backup;

import com.frandm.justenoughbackups.backup.model.BackupManifest;
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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        testBackupRootDir = tempWorkingDir.resolve("backups");
        Files.createDirectories(testBackupRootDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testBackupRootDir)) {
            deleteRecursively(testBackupRootDir);
        }
    }

    @Test
    void benchmarkFullBackupPerformance() throws IOException {
        BackupConfig config = new BackupConfig();
        config.backupDirectory = testBackupRootDir.toAbsolutePath().toString();
        BackupConfig.setCurrent(config);

        String worldName = "BenchmarkWorld";
        String worldDirName = "BenchmarkWorld";
        Path backupDir = config.resolveBackupRoot().resolve(worldDirName);
        Files.createDirectories(backupDir);

        long scanStart = System.nanoTime();
        Map<String, BackupManifest.FileState> snapshot = WorldSnapshotter.snapshot(
                testWorldPath,
                BackupType.FULL,
                "Benchmark Test",
                BackupProgressListener.noop()
        );
        long scanDurationMs = (System.nanoTime() - scanStart) / 1_000_000;

        long totalSizeBytes = snapshot.values().stream().mapToLong(s -> s.size).sum();
        int totalFiles = snapshot.size();
        double totalSizeMB = totalSizeBytes / (1024.0 * 1024.0);

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
        long totalDurationMs = scanDurationMs + compressDurationMs;

        Path generatedZip = backupDir.resolve(manifest.zipFileName);
        long zipSizeBytes = Files.exists(generatedZip) ? Files.size(generatedZip) : 0;
        double zipSizeMB = zipSizeBytes / (1024.0 * 1024.0);

        System.out.println("-------------------------------------------------------");
        System.out.println("BACKUP BENCHMARK (" + (isCustomWorld ? "Real World" : "Synthetic World") + ")");
        System.out.println("-------------------------------------------------------");
        System.out.println("PHASE 1: Scan and SHA-256 Hash");
        System.out.printf("  Scanned files        : %d%n", totalFiles);
        System.out.printf("  Total data size      : %.2f MB (%d bytes)%n", totalSizeMB, totalSizeBytes);
        System.out.printf("  Elapsed time         : %d ms%n", scanDurationMs);
        if (scanDurationMs > 0) {
            System.out.printf("  Scan throughput      : %.2f MB/s%n", totalSizeMB / (scanDurationMs / 1000.0));
        }

        System.out.println();
        System.out.println("PHASE 2: Compression and ZIP Writing");
        System.out.printf("  Output file          : %s%n", manifest.zipFileName);
        System.out.printf("  Final ZIP size       : %.2f MB (%d bytes)%n", zipSizeMB, zipSizeBytes);
        System.out.printf("  Elapsed time         : %d ms%n", compressDurationMs);
        if (compressDurationMs > 0) {
            System.out.printf("  Write throughput     : %.2f MB/s%n", totalSizeMB / (compressDurationMs / 1000.0));
        }

        System.out.println("-------------------------------------------------------");
        System.out.printf("SUMMARY: Total Time: %d ms (%.2f s) [Phase 1: %d ms + Phase 2: %d ms] | Average Speed: %.2f MB/s%n",
                totalDurationMs, totalDurationMs / 1000.0, scanDurationMs, compressDurationMs, totalSizeMB / (totalDurationMs / 1000.0));
        System.out.println("-------------------------------------------------------");

        assertNotNull(manifest);
        assertFalse(snapshot.isEmpty());
        assertTrue(Files.exists(generatedZip));
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
}
