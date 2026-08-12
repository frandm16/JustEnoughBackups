# Changelog

All notable changes to this project are documented in this file.

## 1.2.0.3+26.2 - 11/08/2026

### Added

- Restore progress popup: the HUD popup now shows real-time progress while extracting the backup chain, staging the world, and creating the safety "Old_World restore" backup.
- Parallel (multi-threaded) world scanning, ZIP compression, and restore extraction, with a new configurable `threadCount` option (1 to max CPU threads).
- New `tempBackupDirectory` config to compress the backup ZIP to a temporary folder and then move it to the backup destination, so only the finished ZIP is transferred when backups are stored on a slow or network drive.
- New progress phases (`COMPRESSING`, `WRITING`) and accurate bytes/percent progress reporting.
- Config screen notice for dedicated servers: only the HUD and Preview settings are editable from the client; server settings are edited on the server's config file.

### Fixed

- Restore now completes reliably: the safety "Old_World restore" backup is created while the server is still running, so it no longer fails with C2ME's `saveAll async` guard, and the world swap is applied when the server stops.
- Fixed restore verification failing on backups created by older builds, whose manifest recorded a stale file size: restore now compares content by SHA-256 only (size differences are ignored) while keeping the structural check (missing/extra files) and the rejection of backups with a damaged status.
- Backup manifests now record the actual bytes written to the ZIP, so a file that changes between the scan and the compression (e.g. player data) no longer invalidates the manifest or blocks a later restore.
- Orphaned restore staging and temporary directories (`.justenoughbackups-staging-*` and `.restore-*`) left behind by interrupted restores are now cleaned up automatically on server start.
- Popup progress now shows the real bytes/percent while running and on completion; fixed the related tooltip error.
- Retention is now applied with the full fresh manifest list after creating a backup, avoiding a stale read.
- `/jeb next` is properly localized and lists each enabled automatic backup type with its remaining time.
- The backup management search box keeps focus and caret position while typing.
- Removed the misleading "Starting backup..." message when a backup creation already failed.
- `/jeb config reload` now reports the per-type automatic schedules instead of the legacy interval setting.

## 1.2.0.2+26.2 - 01/08/2026

### Added

- Independent automatic backup schedules for FULL, DIFFERENTIAL, and PARTIAL backups.

### Changed

- Refactored the `/jeb next` command to show remaining times and execution details for all active backup schedules sorted by proximity.

### Fixed

- Deleting a backup from the UI now refreshes the backup list automatically.

## 1.2.0.1+26.2 - 24/06/2026

### Added

- Support for excluding specific files and folders from backups, including config UI support for managing excluded paths.

## 1.2.0+26.2 - 17/06/2026

## 1.1.2.2+26.1.2 - 02/06/2026

### Fixed

- Backups failing when Minecraft creates temporary `<level<digits>>.dat` files during world saves.
- Server-side-only message translations showing raw translation keys on clients without the mod installed.

## 1.0.11+1.21.11 - 21/05/2026

## 1.0.10+1.21.10 - 21/05/2026

## 1.0.9+1.21.9 - 21/05/2026

## 1.0.8+1.21.8 - 21/05/2026

## 1.0.7+1.21.7 - 21/05/2026

## 1.0.6+1.21.6 - 21/05/2026

## 1.1.2.1+26.1.2 - 20/05/2026

### Added

- Optional `summary.txt` generation inside backup ZIPs with world and backup details.
- Pre-backup disk space validation to prevent creating backups when there is not enough free space.
- Configurable minimum free disk reserve in MB for safer backup creation.
