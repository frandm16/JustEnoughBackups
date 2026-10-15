package com.frandm.justenoughbackups.backup.storage.remote;

import com.frandm.justenoughbackups.config.BackupConfig;

import java.io.IOException;

public final class RemoteObjectStores {
    private RemoteObjectStores() {
    }

    public static RemoteObjectStore create(BackupConfig.RemoteStorage config) throws IOException {
        return switch (config.provider) {
            case S3 -> new S3ObjectStore(config);
            case WEBDAV -> new WebDavObjectStore(config);
        };
    }
}