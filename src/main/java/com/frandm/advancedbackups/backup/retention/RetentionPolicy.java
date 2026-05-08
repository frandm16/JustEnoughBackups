package com.frandm.advancedbackups.backup.retention;

import com.frandm.advancedbackups.WorldBackupMod;
import com.frandm.advancedbackups.backup.model.BackupManifest;
import com.frandm.advancedbackups.backup.model.BackupType;
import com.frandm.advancedbackups.backup.storage.BackupStorage;
import com.frandm.advancedbackups.config.BackupConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RetentionPolicy {
    private RetentionPolicy() {
    }

    public static void apply(String worldName, BackupConfig config) throws IOException {
        Path backupDir = config.resolveBackupRoot().resolve(worldName);
        List<BackupManifest> manifests = BackupStorage.readManifests(backupDir).stream()
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
        keep.addAll(newestIds(manifests, BackupType.FULL, config.retention.full));
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

    private static boolean isOverLimit(BackupManifest candidate, List<BackupManifest> manifests, Set<String> protectedIds, BackupConfig config) {
        int limit = switch (candidate.type) {
            case FULL -> config.retention.full;
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
}
