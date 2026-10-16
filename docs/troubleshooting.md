# Troubleshooting & FAQ

Quick solutions for common questions and error messages.

---

## Error Messages

### "Not enough free space on backup volume"
- **Cause**: Before a local backup, JEB estimates the compressed archive size from the files included in this backup and the latest Full backup's compression ratio. It then checks the required space:

```text
Same file system: 2 × estimated archive size + minimumFreeSpaceReserveMb
Separate file systems: estimated archive size + minimumFreeSpaceReserveMb on each volume
```

For a Partial backup, the estimate uses the changed files, not the entire world. When the temporary and backup directories are on different file systems, each volume is checked separately.

- **Fix**:
  1. Free up space on the destination drive.
  2. Or lower `minimumFreeSpaceReserveMb` in `config/justenoughbackups.json` (e.g., reduce from `512` to `256`).

---

### "Remote storage upload failed" / Connection Timed Out
- **Cause**: S3 or WebDAV endpoint is unreachable, credentials are invalid, or network latency timed out.
- **Fix**:
  1. **HTTPS**: Ensure your endpoint URL starts with `https://`. Unencrypted `http://` is only allowed for `localhost`.
  2. **Prefix**: A trailing slash on `s3Prefix` or `webDavPrefix` is optional; JEB normalizes the path.
  3. **Credentials**: Check environment variables (`JEB_S3_ACCESS_KEY_ID`, etc.) or in-game screen entries.
  4. **Retries**: Increase `remoteStorage.maxRetries` (e.g. from `3` to `5`).

---

## Frequently Asked Questions

### Does creating a backup cause server lag or TPS drops?
JEB moves scanning, hashing, compression, and extraction to worker threads, but backups still use disk I/O and CPU resources. If you notice an impact, lower `threadCount`, reduce backup frequency, or use a faster backup drive.

### Why is an automatic backup not running?
Check that the matching `automaticSchedule.<type>.enabled` value is `true` and that its interval is configured as expected. Scheduled backups are paused until a player has been online since the previous backup when `pauseAutomaticBackupsWithoutPlayers` is enabled. After editing the server configuration, run `/jeb config reload`; `/jeb next` shows the time remaining for each enabled schedule or reports that schedules are disabled.

### Why did a Partial or Differential request create a Full backup?
Partial and Differential backups need a usable Full base. If none exists, or no compatible base is available, JEB creates a Full backup instead so the backup can be restored later.

### Why won't an old Full backup get deleted by retention rules?
JEB will **never** delete a base Full backup if there are newer Partial or Differential backups that still depend on it to restore. Once those newer child backups expire, the base Full backup will be safely deleted.

### Can I exclude large map caches like Dynmap or Voxy?
**Yes.** Add their relative paths to `excludedPaths` in `config/justenoughbackups.json`:

```json
{
  "excludedPaths": [
    "dynmap/web/tiles",
    "voxy/cache.db"
  ]
}
```

A folder path excludes its entire subtree; a file path excludes only that specific file.

### How do I change the default hotkeys (<kbd>B</kbd> and <kbd>N</kbd>)?
Open Minecraft's standard **Options → Controls → Key Binds**, scroll to the **Just Enough Backups** section, and assign any key you prefer.
