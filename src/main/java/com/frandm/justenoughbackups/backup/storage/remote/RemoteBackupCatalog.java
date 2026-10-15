package com.frandm.justenoughbackups.backup.storage.remote;

import com.frandm.justenoughbackups.backup.model.BackupManifest;
import com.frandm.justenoughbackups.backup.model.BackupMetadata;
import com.frandm.justenoughbackups.backup.model.BackupStatus;
import com.frandm.justenoughbackups.config.BackupConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
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
        this(config, worldDirectoryName, RemoteObjectStores.create(config));
    }

    RemoteBackupCatalog(BackupConfig.RemoteStorage config, String worldDirectoryName, RemoteObjectStore store) throws IOException {
        this.store = store;
        String configuredPrefix = config.provider == RemoteProviderType.S3 ? config.s3Prefix : config.webDavPrefix;
        this.worldPrefix = cleanPrefix(configuredPrefix) + cleanSegment(worldDirectoryName);
    }

    public List<CatalogEntry> list() throws IOException {
        String archivesPrefix = worldPrefix + "archives/";
        String catalogPrefix = worldPrefix + "catalog/";
        var archives = store.list(archivesPrefix).stream()
                .collect(java.util.stream.Collectors.toMap(
                        RemoteObjectStore.ObjectInfo::key,
                        RemoteObjectStore.ObjectInfo::size,
                        (existing, replacement) -> replacement));
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
            if (manifest.id == null || manifest.id.isBlank() || manifest.createdAt == null
                    || manifest.type == null || manifest.zipFileName == null || manifest.zipFileName.isBlank()) continue;
            String archiveKey;
            try { archiveKey = archiveKey(manifest.zipFileName); }
            catch (IOException invalidName) { continue; }
            Long archiveSize = archives.get(archiveKey);
            if (archiveSize == null) continue;
            manifest.archiveBytes = archiveSize;
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
            MessageDigest digest = sha256();
            CountingInputStream counted = new CountingInputStream(archive);
            try (InputStream checked = new DigestInputStream(counted, digest)) {
                store.put(tempKey, checked);
            }
            UploadInfo uploaded = new UploadInfo(counted.bytesRead(), HexFormat.of().formatHex(digest.digest()));
            if (completedStatus != null) {
                try { status = completedStatus.join(); }
                catch (java.util.concurrent.CompletionException exception) {
                    throw new IOException("Archive creation failed before publication.", exception.getCause());
                }
            }
            store.move(tempKey, finalKey);
            promoted = true;
            RemoteObjectStore.ObjectInfo published = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                published = store.list(finalKey).stream()
                        .filter(object -> object.key().equals(finalKey))
                        .findFirst()
                        .orElse(null);
                if (published != null) break;
                try { Thread.sleep(300L); } catch (InterruptedException ignored) { }
            }
            if (published != null && published.size() > 0 && uploaded.bytes() != published.size())
                throw new IOException("Remote archive size changed while being published: " + manifest.id);
            manifest.archiveBytes = uploaded.bytes();
            manifest.compressionMethod = manifest.compressionMethod == null ? "DEFLATED" : manifest.compressionMethod;
            CatalogEnvelope envelope = new CatalogEnvelope(new BackupMetadata(manifest, status), uploaded.sha256());
            byte[] bytes = GSON.toJson(envelope).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String finalCatalog = catalogKey(manifest.id);
            store.put(tempCatalog, new java.io.ByteArrayInputStream(bytes));
            store.move(tempCatalog, finalCatalog);
            return uploaded;
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
            long total = store.list(worldPrefix + "archives/").stream()
                    .mapToLong(object -> Math.max(0L, object.size())).sum();
            long cap = config.retention.maxTotalSizeMb * 1024L * 1024L;
            if (total > cap) throw new IOException("Remote backup size limit cannot be met while preserving required chains or unreferenced remote archive objects.");
        }
    }

    public int cleanupAbandonedUploads() throws IOException {
        java.time.Instant cutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(7));
        List<RemoteObjectStore.ObjectInfo> temporary = store.list(worldPrefix + "temporary/");
        int removed = 0;
        for (RemoteObjectStore.ObjectInfo object : temporary) {
            if (object.key().endsWith(".uploading") && object.lastModified() != null
                    && !object.lastModified().equals(java.time.Instant.EPOCH)
                    && object.lastModified().isBefore(cutoff)) {
                store.delete(object.key());
                removed++;
            }
        }
        java.util.Set<String> referencedArchives = new java.util.HashSet<>();
        for (RemoteObjectStore.ObjectInfo object : store.list(worldPrefix + "catalog/")) {
            if (!object.key().endsWith(".json")) continue;
            CatalogEnvelope envelope;
            try (InputStream in = store.get(object.key())) {
                envelope = GSON.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), CatalogEnvelope.class);
            } catch (RuntimeException exception) {
                throw new IOException("Cannot safely clean remote archives because a catalog sidecar is unreadable.", exception);
            }
            if (envelope == null || envelope.metadata == null || envelope.metadata.manifest == null)
                throw new IOException("Cannot safely clean remote archives because a catalog sidecar is incomplete.");
            referencedArchives.add(archiveKey(envelope.metadata.manifest.zipFileName));
        }
        for (RemoteObjectStore.ObjectInfo object : store.list(worldPrefix + "archives/")) {
            if (!referencedArchives.contains(object.key()) && object.lastModified() != null
                    && object.lastModified().isBefore(cutoff)) {
                store.delete(object.key());
                removed++;
            }
        }
        return removed;
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
        if (value == null || value.isBlank()) {
            throw new IOException("Invalid world directory name.");
        }
        String cleaned = value.trim().replace('\\', '/').replaceAll("^/+|/+$", "");
        if (cleaned.contains("..")) {
            throw new IOException("Invalid remote object name.");
        }
        return cleaned + "/";
    }

    private static String cleanPrefix(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim().replace('\\', '/').replaceAll("^/+|/+$", "") + "/";
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
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

    private static final class CountingInputStream extends FilterInputStream {
        private long bytesRead;

        private CountingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) bytesRead++;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = super.read(bytes, offset, length);
            if (count > 0) bytesRead += count;
            return count;
        }

        private long bytesRead() {
            return bytesRead;
        }
    }
}
