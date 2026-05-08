package com.frandm.advancedbackups.backup.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BackupManifest {
    public int manifestVersion = 1;
    public String id;
    public BackupType type;
    public String createdAt;
    public String worldName;
    public String baseBackupId;
    public String zipFileName;
    public long includedBytes;
    public List<String> includedFiles = new ArrayList<>();
    public Map<String, FileState> snapshot = new LinkedHashMap<>();

    public static final class FileState {
        public long size;
        public long modifiedTime;
        public String sha256;

        public FileState() {
        }

        public FileState(long size, long modifiedTime, String sha256) {
            this.size = size;
            this.modifiedTime = modifiedTime;
            this.sha256 = sha256;
        }

        public boolean sameContent(FileState other) {
            return other != null
                    && size == other.size
                    && modifiedTime == other.modifiedTime
                    && Objects.equals(sha256, other.sha256);
        }
    }
}
