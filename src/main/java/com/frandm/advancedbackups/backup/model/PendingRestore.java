package com.frandm.advancedbackups.backup.model;

import java.nio.file.Path;

public record PendingRestore(String backupId, Path worldPath, Path tempRestore, Path previousWorld) {
}
