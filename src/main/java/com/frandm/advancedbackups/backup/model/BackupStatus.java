package com.frandm.advancedbackups.backup.model;

import java.util.ArrayList;
import java.util.List;

public final class BackupStatus {
    public int statusVersion = 1;
    public String backupId;
    public BackupType type;
    public String baseBackupId;
    public String createdAt;
    public List<FileEntry> files = new ArrayList<>();
    public List<BrokenFile> brokenFiles = new ArrayList<>();
    public long totalBytes;
    public boolean completed = true;

    public static final class FileEntry {
        public String path;
        public long size;
        public String sha256;

        public FileEntry() {
        }

        public FileEntry(String path, long size, String sha256) {
            this.path = path;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    public static final class BrokenFile {
        public String path;
        public String error;

        public BrokenFile() {
        }

        public BrokenFile(String path, String error) {
            this.path = path;
            this.error = error;
        }
    }
}
