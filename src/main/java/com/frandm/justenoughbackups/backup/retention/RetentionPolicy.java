package com.frandm.justenoughbackups.backup.retention;

import com.frandm.justenoughbackups.WorldBackupMod;
import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupType;
import com.frandm.justenoughbackups.backup.storage.BackupStorage;
import com.frandm.justenoughbackups.config.BackupConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RetentionPolicy {
    private RetentionPolicy() {
    }

    public static void apply(String worldDirectoryName, BackupConfig config) throws IOException {
        Path backupDir = config.resolveBackupRoot().resolve(worldDirectoryName);
        apply(backupDir, BackupStorage.readManifests(backupDir), config);
    }

    public static void apply(String worldDirectoryName, BackupConfig config, List<BackupManifest> manifests) throws IOException {
        Path backupDir = config.resolveBackupRoot().resolve(worldDirectoryName);
        apply(backupDir, manifests, config);
    }

    private static void apply(Path backupDir, List<BackupManifest> manifests, BackupConfig config) throws IOException {
        RetentionDecision decision = plan(backupDir, manifests, config);
        for (BackupEntry entry : decision.deletions()) {
            if (entry.existingFile()) {
                Files.deleteIfExists(entry.path());
                WorldBackupMod.LOGGER.info("Deleted old backup by retention policy: {}", entry.manifest().id);
            }
        }
    }

    public static RetentionDecision plan(Path backupDir, List<BackupManifest> manifests, BackupConfig config) throws IOException {
        return plan(backupDir, manifests, config, null, 0L);
    }

    public static RetentionDecision planWithPending(Path backupDir, List<BackupManifest> manifests, BackupConfig config, BackupManifest pendingManifest, long pendingBytes) throws IOException {
        return plan(backupDir, manifests, config, pendingManifest, pendingBytes);
    }

    private static RetentionDecision plan(Path backupDir, List<BackupManifest> manifests, BackupConfig config, BackupManifest pendingManifest, long pendingBytes) throws IOException {
        List<BackupManifest> orderedManifests = manifests.stream()
                .sorted(Comparator.comparing(manifest -> manifest.createdAt))
                .toList();
        if (orderedManifests.isEmpty() && pendingManifest == null) {
            return new RetentionDecision(List.of(), Set.of(), 0L, 0L, false);
        }

        List<BackupEntry> entries = loadEntries(backupDir, orderedManifests, pendingManifest, pendingBytes);
        List<BackupManifest> manifestsForRules = entries.stream()
                .map(BackupEntry::manifest)
                .sorted(Comparator.comparing(manifest -> manifest.createdAt))
                .toList();

        Set<String> protectedIds = requiredChainIds(manifestsForRules, config);
        LinkedHashSet<String> deletionIds = new LinkedHashSet<>();
        for (BackupEntry entry : entries) {
            if (protectedIds.contains(entry.id())) {
                continue;
            }
            if (isOverLimit(entry.manifest(), manifestsForRules, protectedIds, config)) {
                deletionIds.add(entry.id());
            }
        }

        long capBytes = toCapBytes(config);
        long projectedBytes = projectedBytes(entries, deletionIds);
        if (capBytes > 0L && projectedBytes > capBytes) {
            for (BackupEntry entry : entries) {
                if (projectedBytes <= capBytes) {
                    break;
                }
                if (protectedIds.contains(entry.id()) || deletionIds.contains(entry.id())) {
                    continue;
                }
                deletionIds.add(entry.id());
                projectedBytes -= entry.zipBytes();
            }
        }

        List<BackupEntry> deletions = entries.stream()
                .filter(entry -> deletionIds.contains(entry.id()))
                .toList();
        long totalBytes = entries.stream().mapToLong(BackupEntry::zipBytes).sum();
        boolean exceedsSpaceLimit = capBytes > 0L && projectedBytes > capBytes;
        return new RetentionDecision(deletions, Set.copyOf(protectedIds), totalBytes, projectedBytes, exceedsSpaceLimit);
    }

    private static Set<String> requiredChainIds(List<BackupManifest> manifests, BackupConfig config) {
        Map<String, BackupManifest> byId = new LinkedHashMap<>();
        for (BackupManifest manifest : manifests) {
            byId.put(manifest.id, manifest);
        }

        Set<String> keep = new HashSet<>();
        keep.addAll(newestIds(manifests, BackupType.FULL, config.retention.full));
        keep.addAll(newestIds(manifests, BackupType.PARTIAL, config.retention.incremental));
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

    private static boolean isOverLimit(BackupManifest candidate, List<BackupManifest> manifests, Set<String> protectedIds, BackupConfig config) {
        int limit = switch (candidate.type) {
            case FULL -> config.retention.full;
            case PARTIAL -> config.retention.incremental;
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

    private static List<BackupEntry> loadEntries(Path backupDir, List<BackupManifest> manifests, BackupManifest pendingManifest, long pendingBytes) throws IOException {
        List<BackupEntry> entries = manifests.stream()
                .map(manifest -> new BackupEntry(
                        manifest,
                        backupDir.resolve(manifest.zipFileName),
                        fileSize(backupDir.resolve(manifest.zipFileName)),
                        true
                ))
                .sorted(Comparator.comparing(entry -> entry.manifest().createdAt))
                .toList();
        if (pendingManifest == null) {
            return entries;
        }

        java.util.ArrayList<BackupEntry> combined = new java.util.ArrayList<>(entries);
        combined.add(new BackupEntry(
                pendingManifest,
                backupDir.resolve(pendingManifest.zipFileName),
                Math.max(0L, pendingBytes),
                false
        ));
        combined.sort(Comparator.comparing(entry -> entry.manifest().createdAt));
        return combined;
    }

    private static long fileSize(Path file) {
        try {
            return Files.exists(file) ? Files.size(file) : 0L;
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static long projectedBytes(List<BackupEntry> entries, Set<String> deletionIds) {
        return entries.stream()
                .filter(entry -> !deletionIds.contains(entry.id()))
                .mapToLong(BackupEntry::zipBytes)
                .sum();
    }

    private static long toCapBytes(BackupConfig config) {
        long mb = Math.max(0L, config.retention.maxTotalSizeMb);
        return mb <= 0L ? 0L : mb * 1024L * 1024L;
    }

    public record RetentionDecision(
            List<BackupEntry> deletions,
            Set<String> protectedIds,
            long totalBytes,
            long projectedBytes,
            boolean exceedsSpaceLimit
    ) {
    }

    public record BackupEntry(BackupManifest manifest, Path path, long zipBytes, boolean existingFile) {
        public String id() {
            return manifest.id;
        }
    }
}
