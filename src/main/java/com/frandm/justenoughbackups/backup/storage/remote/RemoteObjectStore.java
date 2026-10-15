package com.frandm.justenoughbackups.backup.storage.remote;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.time.Instant;

public interface RemoteObjectStore extends AutoCloseable {
    List<ObjectInfo> list(String prefix) throws IOException;

    void put(String key, InputStream input) throws IOException;

    InputStream get(String key) throws IOException;

    void delete(String key) throws IOException;

    default void move(String sourceKey, String targetKey) throws IOException {
        try (InputStream in = get(sourceKey)) {
            put(targetKey, in);
        }
        delete(sourceKey);
    }

    @Override
    default void close() {
    }

    record ObjectInfo(String key, long size, Instant lastModified) {
        public ObjectInfo(String key, long size) { this(key, size, Instant.EPOCH); }
    }
}