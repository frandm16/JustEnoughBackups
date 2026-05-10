package com.frandm.justenoughbackups.backup.model;

public final class BackupMetadata {
    public int metadataVersion = 1;
    public BackupManifest manifest;
    public BackupStatus status;

    public BackupMetadata() {
    }

    public BackupMetadata(BackupManifest manifest, BackupStatus status) {
        this.manifest = manifest;
        this.status = status;
    }
}
