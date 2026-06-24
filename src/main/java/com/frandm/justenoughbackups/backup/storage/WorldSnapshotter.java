package com.frandm.justenoughbackups.backup.storage;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.BackupConstants;
import com.frandm.justenoughbackups.config.BackupConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class WorldSnapshotter {
    private static final Pattern TRANSIENT_LEVEL_FILE = Pattern.compile("^level\\d+\\.dat$");

    private WorldSnapshotter() {
    }

    public static Map<String, BackupManifest.FileState> snapshot(Path worldPath) throws IOException {
        return snapshot(worldPath, WorldSnapshotter::readFileState, BackupConfig.get().excludedPaths);
    }

    static Map<String, BackupManifest.FileState> snapshot(Path worldPath, SnapshotReader reader, List<String> excludedPaths) throws IOException {
        Map<String, BackupManifest.FileState> snapshot = new LinkedHashMap<>();

        Files.walkFileTree(worldPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (shouldSkip(worldPath.relativize(dir), excludedPaths)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = worldPath.relativize(file);
                if (shouldSkip(relative, excludedPaths)) {
                    return FileVisitResult.CONTINUE;
                }

                String relativeName = relative.toString().replace('\\', '/');
                try {
                    snapshot.put(relativeName, reader.read(file, attrs));
                } catch (NoSuchFileException exception) {
                    logSkippedTransientFile(file, relativeName, exception);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                if (exception instanceof NoSuchFileException) {
                    String relativeName = worldPath.relativize(file).toString().replace('\\', '/');
                    logSkippedTransientFile(file, relativeName, exception);
                    return FileVisitResult.CONTINUE;
                }
                throw exception;
            }

            private void logSkippedTransientFile(Path file, String relativeName, IOException exception) {
                WorldBackupMod.LOGGER.debug(
                        "Skipping transient world file during snapshot: {} ({})",
                        relativeName,
                        rootMessage(exception)
                );
            }

            private String rootMessage(IOException exception) {
                return exception.getMessage() == null || exception.getMessage().isBlank()
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
            }
        });

        return snapshot;
    }

    private static boolean shouldSkip(Path relativePath, List<String> excludedPaths) {
        String normalizedRelative = normalizeRelativePath(relativePath).toLowerCase(Locale.ROOT);

        for(String path : excludedPaths){
            String normalizedExcludedPath = path.toLowerCase(Locale.ROOT);
            if(normalizedRelative.equals(normalizedExcludedPath) || normalizedRelative.startsWith(normalizedExcludedPath + "/")){
                return true;
            }
        }
        for (Path part : relativePath) {
            if ("backups".equalsIgnoreCase(part.toString())) {
                return true;
            }
        }

        String fileName = relativePath.getFileName() == null
                ? ""
                : relativePath.getFileName().toString().toLowerCase(Locale.ROOT);

        return "session.lock".equals(fileName)
                || TRANSIENT_LEVEL_FILE.matcher(fileName).matches()
                || fileName.endsWith(".lock")
                || BackupConstants.SUMMARY_ENTRY.equals(fileName);
    }

    private static String normalizeRelativePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static BackupManifest.FileState readFileState(Path file, BasicFileAttributes attrs) throws IOException {
        return new BackupManifest.FileState(
                attrs.size(),
                attrs.lastModifiedTime().toMillis(),
                sha256(file)
        );
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

    @FunctionalInterface
    interface SnapshotReader {
        BackupManifest.FileState read(Path file, BasicFileAttributes attrs) throws IOException;
    }
}
