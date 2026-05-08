package com.frandm.advancedbackups;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class BackupManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final AtomicBoolean BACKUP_RUNNING = new AtomicBoolean(false);
    private static volatile PendingRestore pendingRestore;

    private BackupManager() {
    }

    public static boolean isBackupRunning() {
        return BACKUP_RUNNING.get();
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
                        restore.backupId,
                        restore.previousWorld
                );
            } catch (IOException exception) {
                WorldBackupMod.LOGGER.error("Prepared restore failed while server was stopping.", exception);
            }
        });
    }

    public static CompletableFuture<BackupManifest> createBackup(MinecraftServer server, BackupType type, String reason) {
        if (!BACKUP_RUNNING.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A backup is already running."));
        }

        server.saveEverything(true, true, true);
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        String worldName = getWorldName(worldPath);
        BackupConfig config = BackupConfig.get();

        return CompletableFuture.supplyAsync(() -> {
            try {
                BackupManifest manifest = createBackupNow(worldPath, worldName, config, type, reason);
                applyRetention(worldName, config);
                WorldBackupMod.LOGGER.info("Backup created: {}", manifest.id);
                return manifest;
            } catch (IOException exception) {
                throw new RuntimeException("Failed to create " + type + " backup.", exception);
            } finally {
                BACKUP_RUNNING.set(false);
            }
        });
    }

    public static List<BackupManifest> listBackups(MinecraftServer server) throws IOException {
        return readManifests(getWorldBackupDir(server)).stream()
                .sorted(Comparator.comparing(manifest -> manifest.createdAt))
                .toList();
    }

    public static CompletableFuture<PendingRestore> restoreBackup(MinecraftServer server, String backupId) {
        if (!BACKUP_RUNNING.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A backup or restore is already running."));
        }

        server.saveEverything(true, true, true);
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        Path backupDir = getWorldBackupDir(server);

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<BackupManifest> chain = resolveRestoreChain(backupDir, backupId);
                Path tempRestore = backupDir.resolve(".restore-" + backupId);
                Path previousWorld = backupDir.resolve(".previous-world-" + LocalDateTime.now().format(FILE_TIME));

                deleteIfExists(tempRestore);
                Files.createDirectories(tempRestore);
                for (BackupManifest manifest : chain) {
                    extractBackup(backupDir.resolve(manifest.zipFileName), tempRestore);
                }
                pruneToSnapshot(tempRestore, chain.getLast().snapshot);

                PendingRestore restore = new PendingRestore(backupId, worldPath, tempRestore, previousWorld);
                pendingRestore = restore;
                WorldBackupMod.LOGGER.warn("Restore {} prepared. It will be applied when the server stops.", backupId);
                return restore;
            } catch (IOException exception) {
                throw new RuntimeException("Failed to prepare restore: " + backupId, exception);
            } finally {
                BACKUP_RUNNING.set(false);
            }
        });
    }

    public static BackupConfig reloadConfig() {
        BackupConfig config = BackupConfig.reload();
        BackupScheduler.resetTimer();
        return config;
    }

    private static BackupManifest createBackupNow(
            Path worldPath,
            String worldName,
            BackupConfig config,
            BackupType type,
            String reason
    ) throws IOException {
        Path backupDir = config.resolveBackupRoot().resolve(worldName);
        Files.createDirectories(backupDir);

        List<BackupManifest> manifests = readManifests(backupDir);
        BackupManifest base = findBaseManifest(manifests, type);
        Map<String, BackupManifest.FileState> snapshot = snapshotWorld(worldPath);
        List<String> includedFiles = includedFiles(type, snapshot, base);

        String timestamp = LocalDateTime.now().format(FILE_TIME);
        String id = type.commandName() + "-" + timestamp;
        Path backupFile = backupDir.resolve(id + ".zip");

        BackupManifest manifest = new BackupManifest();
        manifest.id = id;
        manifest.type = type;
        manifest.createdAt = Instant.now().toString();
        manifest.worldName = worldName;
        manifest.baseBackupId = base == null ? null : base.id;
        manifest.zipFileName = backupFile.getFileName().toString();
        manifest.includedFiles.addAll(includedFiles);
        manifest.snapshot.putAll(snapshot);

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

            ZipEntry manifestEntry = new ZipEntry(MANIFEST_ENTRY);
            zipOut.putNextEntry(manifestEntry);
            zipOut.write(GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
        }

        WorldBackupMod.LOGGER.info("{} backup {} created for reason: {}", type, id, reason);
        return manifest;
    }

    private static BackupManifest findBaseManifest(List<BackupManifest> manifests, BackupType type) {
        if (type == BackupType.FULL_BACKUPS || manifests.isEmpty()) {
            return null;
        }

        return manifests.stream()
                .filter(manifest -> type == BackupType.INCREMENTAL || manifest.type == BackupType.FULL_BACKUPS)
                .max(Comparator.comparing(manifest -> manifest.createdAt))
                .orElse(null);
    }

    private static List<String> includedFiles(
            BackupType type,
            Map<String, BackupManifest.FileState> snapshot,
            BackupManifest base
    ) {
        if (type == BackupType.FULL_BACKUPS || base == null) {
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

    private static Map<String, BackupManifest.FileState> snapshotWorld(Path worldPath) throws IOException {
        Map<String, BackupManifest.FileState> snapshot = new LinkedHashMap<>();

        Files.walkFileTree(worldPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (shouldSkip(worldPath.relativize(dir))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = worldPath.relativize(file);
                if (shouldSkip(relative)) {
                    return FileVisitResult.CONTINUE;
                }

                String relativeName = relative.toString().replace('\\', '/');
                snapshot.put(relativeName, new BackupManifest.FileState(
                        attrs.size(),
                        attrs.lastModifiedTime().toMillis(),
                        sha256(file)
                ));
                return FileVisitResult.CONTINUE;
            }
        });

        return snapshot;
    }

    private static List<BackupManifest> readManifests(Path backupDir) throws IOException {
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

    private static BackupManifest readManifest(Path backupFile) {
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            ZipEntry entry = zipFile.getEntry(MANIFEST_ENTRY);
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

    private static List<BackupManifest> resolveRestoreChain(Path backupDir, String backupId) throws IOException {
        List<BackupManifest> manifests = readManifests(backupDir);
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
            if (current.type == BackupType.FULL_BACKUPS) {
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

        if (chain.isEmpty() || chain.peekFirst().type != BackupType.FULL_BACKUPS) {
            throw new IOException("Restore chain does not start with a full backup.");
        }

        return List.copyOf(chain);
    }

    private static void extractBackup(Path backupFile, Path targetDir) throws IOException {
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || MANIFEST_ENTRY.equals(entry.getName())) {
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

    private static void applyPreparedRestore(PendingRestore restore) throws IOException {
        if (!Files.isDirectory(restore.tempRestore)) {
            throw new IOException("Prepared restore directory is missing: " + restore.tempRestore);
        }

        deleteIfExists(restore.previousWorld);
        if (Files.exists(restore.worldPath)) {
            Files.move(restore.worldPath, restore.previousWorld, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(restore.tempRestore, restore.worldPath, StandardCopyOption.REPLACE_EXISTING);
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

    private static void applyRetention(String worldName, BackupConfig config) throws IOException {
        Path backupDir = config.resolveBackupRoot().resolve(worldName);
        List<BackupManifest> manifests = readManifests(backupDir).stream()
                .sorted(Comparator.comparing(manifest -> manifest.createdAt))
                .toList();
        if (manifests.isEmpty()) {
            return;
        }

        Set<String> protectedIds = requiredChainIds(manifests, config);
        for (BackupManifest manifest : manifests) {
            if (protectedIds.contains(manifest.id)) {
                continue;
            }

            if (isOverLimit(manifest, manifests, protectedIds, config)) {
                Files.deleteIfExists(backupDir.resolve(manifest.zipFileName));
                WorldBackupMod.LOGGER.info("Deleted old backup by retention policy: {}", manifest.id);
            }
        }
    }

    private static Set<String> requiredChainIds(List<BackupManifest> manifests, BackupConfig config) {
        Map<String, BackupManifest> byId = new LinkedHashMap<>();
        for (BackupManifest manifest : manifests) {
            byId.put(manifest.id, manifest);
        }

        Set<String> keep = new HashSet<>();
        keep.addAll(newestIds(manifests, BackupType.FULL_BACKUPS, config.retention.full));
        keep.addAll(newestIds(manifests, BackupType.INCREMENTAL, config.retention.incremental));
        keep.addAll(newestIds(manifests, BackupType.DIFFERENTIAL, config.retention.differential));

        Set<String> required = new HashSet<>(keep);
        for (String id : keep) {
            BackupManifest current = byId.get(id);
            while (current != null && current.baseBackupId != null) {
                current = byId.get(current.baseBackupId);
                if (current != null) {
                    required.add(current.id);
                }
            }
        }

        return required;
    }

    private static Set<String> newestIds(List<BackupManifest> manifests, BackupType type, int limit) {
        if (limit <= 0) {
            return Set.of();
        }

        Set<String> ids = new HashSet<>();
        manifests.stream()
                .filter(manifest -> manifest.type == type)
                .sorted(Comparator.comparing((BackupManifest manifest) -> manifest.createdAt).reversed())
                .limit(limit)
                .forEach(manifest -> ids.add(manifest.id));
        return ids;
    }

    private static boolean isOverLimit(
            BackupManifest candidate,
            List<BackupManifest> manifests,
            Set<String> protectedIds,
            BackupConfig config
    ) {
        int limit = switch (candidate.type) {
            case FULL_BACKUPS -> config.retention.full;
            case INCREMENTAL -> config.retention.incremental;
            case DIFFERENTIAL -> config.retention.differential;
        };
        if (limit <= 0) {
            return true;
        }

        long newerOrSame = manifests.stream()
                .filter(manifest -> manifest.type == candidate.type)
                .filter(manifest -> protectedIds.contains(manifest.id) || manifest.createdAt.compareTo(candidate.createdAt) >= 0)
                .count();
        return newerOrSame > limit;
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

    private static Path getWorldBackupDir(MinecraftServer server) {
        Path worldPath = server.getWorldPath(LevelResource.ROOT);
        return BackupConfig.get().resolveBackupRoot().resolve(getWorldName(worldPath));
    }

    private static String getWorldName(Path worldPath) {
        Path fileName = worldPath.getFileName();
        if (fileName == null) {
            return "world";
        }

        String cleaned = fileName.toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "world" : cleaned;
    }

    private static boolean shouldSkip(Path relativePath) {
        for (Path part : relativePath) {
            if ("backups".equalsIgnoreCase(part.toString())) {
                return true;
            }
        }

        String fileName = relativePath.getFileName() == null
                ? ""
                : relativePath.getFileName().toString().toLowerCase(Locale.ROOT);

        return "session.lock".equals(fileName) || fileName.endsWith(".lock");
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return toHex(digest.digest());
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

    public record PendingRestore(String backupId, Path worldPath, Path tempRestore, Path previousWorld) {
    }
}
