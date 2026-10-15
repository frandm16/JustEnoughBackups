package com.frandm.justenoughbackups.backup.storage.remote;

import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupMetadata;
import com.frandm.justenoughbackups.backup.model.BackupStatus;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class RemoteBackupCatalog implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final RemoteObjectStore store;
    private final String worldPrefix;

    public RemoteBackupCatalog(BackupConfig.RemoteStorage config, String worldDirectoryName) throws IOException {
        this.store = RemoteObjectStores.create(config);
        String configuredPrefix = config.provider == RemoteProviderType.S3 ? config.s3Prefix : config.webDavPrefix;
        this.worldPrefix = cleanPrefix(configuredPrefix) + cleanSegment(worldDirectoryName) + "/";
    }

    public List<CatalogEntry> list() throws IOException {
        String archivesPrefix = worldPrefix + "archives/";
        String catalogPrefix = worldPrefix + "catalog/";
        var archives = store.list(archivesPrefix).stream()
                .collect(java.util.stream.Collectors.toMap(RemoteObjectStore.ObjectInfo::key, RemoteObjectStore.ObjectInfo::size));
        List<CatalogEntry> entries = new ArrayList<>();
        for (RemoteObjectStore.ObjectInfo object : store.list(catalogPrefix)) {
            if (!object.key().endsWith(".json")) continue;
            CatalogEnvelope envelope;
            try (InputStream in = store.get(object.key())) {
                envelope = GSON.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), CatalogEnvelope.class);
            } catch (RuntimeException exception) {
                continue;
            }
            if (envelope == null || envelope.metadata == null || envelope.metadata.manifest == null) continue;
            BackupManifest manifest = envelope.metadata.manifest;
            String archiveKey = archiveKey(manifest.zipFileName);
            Long archiveSize = archives.get(archiveKey);
            if (archiveSize == null) continue;
            entries.add(new CatalogEntry(manifest, envelope.metadata.status, envelope.sha256, archiveSize));
        }
        entries.sort(Comparator.comparing(entry -> entry.manifest().createdAt));
        return List.copyOf(entries);
    }

    public UploadInfo uploadAndPublish(BackupManifest manifest, BackupStatus status, InputStream archive) throws IOException {
        return uploadAndPublish(manifest, status, archive, null);
    }

    public UploadInfo uploadAndPublish(BackupManifest manifest, BackupStatus status, InputStream archive,
                                       java.util.concurrent.CompletableFuture<BackupStatus> completedStatus) throws IOException {
        String tempKey = worldPrefix + "temporary/" + manifest.id + "-" + UUID.randomUUID() + ".zip.uploading";
        String tempCatalog = worldPrefix + "temporary/" + manifest.id + "-" + UUID.randomUUID() + ".json.uploading";
        String finalKey = archiveKey(manifest.zipFileName);
        boolean promoted = false;
        try {
            if (objectExists(finalKey)) throw new IOException("A remote backup already uses this archive name: " + manifest.zipFileName);
            store.put(tempKey, archive);
            if (completedStatus != null) {
                try { status = completedStatus.join(); }
                catch (java.util.concurrent.CompletionException exception) {
                    throw new IOException("Archive creation failed before publication.", exception.getCause());
                }
            }
            UploadInfo verified = verify(tempKey);
            store.move(tempKey, finalKey);
            promoted = true;
            UploadInfo published = verify(finalKey);
            if (!verified.sha256().equals(published.sha256()) || verified.bytes() != published.bytes())
                throw new IOException("Remote archive changed while being published; checksum verification failed.");
            manifest.compressionMethod = manifest.compressionMethod == null ? "DEFLATED" : manifest.compressionMethod;
            CatalogEnvelope envelope = new CatalogEnvelope(new BackupMetadata(manifest, status), verified.sha256());
            byte[] bytes = GSON.toJson(envelope).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String finalCatalog = catalogKey(manifest.id);
            store.put(tempCatalog, new java.io.ByteArrayInputStream(bytes));
            store.move(tempCatalog, finalCatalog);
            return verified;
        } catch (IOException | RuntimeException exception) {
            try { store.delete(tempKey); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            try { store.delete(tempCatalog); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            if (promoted) {
                try { store.delete(finalKey); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
                try { store.delete(catalogKey(manifest.id)); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            }
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("Remote backup publication failed.", exception);
        }
    }

    public InputStream download(String zipFileName) throws IOException {
        return store.get(archiveKey(zipFileName));
    }

    public void delete(String backupId, String zipFileName) throws IOException {
        store.delete(archiveKey(zipFileName));
        store.delete(catalogKey(backupId));
    }

    public void rename(BackupManifest manifest, String requestedName) throws IOException {
        String safeName = requestedName == null ? "" : requestedName.trim();
        if (safeName.isBlank() || safeName.contains("/") || safeName.contains("\\") || safeName.contains("..")) {
            throw new IOException("Invalid backup name.");
        }
        if (!safeName.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) safeName += ".zip";
        String oldName = manifest.zipFileName;
        String oldKey = archiveKey(oldName);
        String newKey = archiveKey(safeName);
        if (oldKey.equals(newKey)) return;
        if (objectExists(newKey)) throw new IOException("A remote backup already uses this archive name: " + safeName);
        CatalogEntry existing = list().stream().filter(entry -> entry.manifest().id.equals(manifest.id)).findFirst()
                .orElseThrow(() -> new IOException("Remote backup not found: " + manifest.id));
        store.move(oldKey, newKey);
        manifest.zipFileName = safeName;
        try {
            publishCatalog(manifest, existing.status(), existing.sha256());
        } catch (IOException exception) {
            manifest.zipFileName = oldName;
            try { store.move(newKey, oldKey); } catch (IOException rollback) { exception.addSuppressed(rollback); }
            throw exception;
        }
    }

    public void updateCatalog(BackupManifest manifest) throws IOException {
        CatalogEntry existing = list().stream().filter(entry -> entry.manifest().id.equals(manifest.id)).findFirst()
                .orElseThrow(() -> new IOException("Remote backup not found: " + manifest.id));
        publishCatalog(manifest, existing.status(), existing.sha256());
    }

    private void publishCatalog(BackupManifest manifest, BackupStatus status, String sha256) throws IOException {
        CatalogEnvelope envelope = new CatalogEnvelope(new BackupMetadata(manifest, status), sha256);
        String tempKey = worldPrefix + "temporary/" + manifest.id + "-" + UUID.randomUUID() + ".json.uploading";
        byte[] bytes = GSON.toJson(envelope).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        store.put(tempKey, new java.io.ByteArrayInputStream(bytes));
        try { store.move(tempKey, catalogKey(manifest.id)); }
        catch (IOException failure) {
            try { store.delete(tempKey); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public void applyRetention(BackupConfig config) throws IOException {
        List<CatalogEntry> entries = list();
        var manifests = entries.stream().map(CatalogEntry::manifest).toList();
        var sizes = entries.stream().collect(java.util.stream.Collectors.toMap(e -> e.manifest().id, CatalogEntry::archiveBytes));
        var decision = com.frandm.justenoughbackups.backup.retention.RetentionPolicy.planRemote(manifests, sizes, config);
        for (BackupManifest manifest : decision) {
            delete(manifest.id, manifest.zipFileName);
        }
        if (config.retention.maxTotalSizeMb > 0) {
            long total = list().stream().mapToLong(CatalogEntry::archiveBytes).sum();
            long cap = config.retention.maxTotalSizeMb * 1024L * 1024L;
            if (total > cap) throw new IOException("Remote backup size limit cannot be met while preserving required backup chains.");
        }
    }

    public int cleanupAbandonedUploads() throws IOException {
        java.time.Instant cutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(7));
        List<RemoteObjectStore.ObjectInfo> temporary = store.list(worldPrefix + "temporary/");
        int removed = 0;
        for (RemoteObjectStore.ObjectInfo object : temporary) {
            if (object.key().endsWith(".uploading") && object.lastModified() != null
                    && object.lastModified().isBefore(cutoff)) {
                store.delete(object.key());
                removed++;
            }
        }
        return removed;
    }

    private UploadInfo verify(String key) throws IOException {
        MessageDigest digest = sha256();
        long bytes = 0;
        try (InputStream in = new DigestInputStream(store.get(key), digest)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) bytes += read;
        }
        return new UploadInfo(bytes, HexFormat.of().formatHex(digest.digest()));
    }

    private boolean objectExists(String key) throws IOException {
        return store.list(key).stream().anyMatch(object -> object.key().equals(key));
    }

    private String archiveKey(String name) throws IOException {
        return worldPrefix + "archives/" + cleanFileName(name);
    }

    private String catalogKey(String id) throws IOException {
        return worldPrefix + "catalog/" + cleanFileName(id) + ".json";
    }

    private static String cleanFileName(String value) throws IOException {
        if (value == null || value.isBlank() || value.contains("/") || value.contains("\\") || value.contains("..")) {
            throw new IOException("Invalid remote object name.");
        }
        return value;
    }

    private static String cleanSegment(String value) throws IOException {
        return cleanFileName(value.trim()) + "/";
    }

    private static String cleanPrefix(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim().replace('\\', '/').replaceAll("^/+|/+$", "") + "/";
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    @Override
    public void close() {
        store.close();
    }

    private static final class CatalogEnvelope {
        int version = 1;
        BackupMetadata metadata;
        String sha256;

        CatalogEnvelope(BackupMetadata metadata, String sha256) {
            this.metadata = metadata;
            this.sha256 = sha256;
        }
    }

    public record CatalogEntry(BackupManifest manifest, BackupStatus status, String sha256, long archiveBytes) { }
    public record UploadInfo(long bytes, String sha256) { }
}