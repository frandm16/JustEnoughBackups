# Changelog

All notable changes to this project are documented in this file.

## 1.2.0.3+26.2 - 11/08/2026

### Added

- Parallel (multi-threaded) world scanning, ZIP compression, and restore extraction, with a new configurable `threadCount` option (1 to max CPU threads).
- New `tempBackupDirectory` config to compress the backup ZIP to a temporary folder and then move it to the backup destination, so only the finished ZIP is transferred when backups are stored on a slow or network drive.
- Backup watchdog that detects a stalled backup, dumps all threads for diagnosis, and aborts after a timeout.
- New progress phases (`COMPRESSING`, `WRITING`) and accurate bytes/percent progress reporting.
- Config screen notice for dedicated servers: only the HUD and Preview settings are editable from the client; server settings are edited on the server's config file.
- Backup benchmark test (1 vs N threads) and test coverage for the custom temporary directory and its overlap validation.

### Fixed

- Popup progress now shows the real bytes/percent while running and on completion; fixed the related tooltip error.
- Popup progress no longer breaks when compression runs multi-threaded.
- Retention is now applied with the full fresh manifest list after creating a backup, avoiding a stale read.
- `/jeb next` is properly localized and lists each enabled automatic backup type with its remaining time.
- An invalid or unreadable `justenoughbackups.json` is no longer overwritten with defaults on load.
- Backup/restore no longer leave the "a backup is already running" lock stuck when the initial server setup fails.
- The backup management search box keeps focus and caret position while typing.
- Removed the misleading "Starting backup..." message when a backup creation already failed.
- `/jeb config reload` now reports the per-type automatic schedules instead of the legacy interval setting.
- The config screen is no longer misdetected as a "dedicated server" when opened from the main menu (Mod Menu entry point).
- A `tempBackupDirectory` that overlaps the world directory is rejected with a clear error.
- A `RuntimeException` during backup UI failure handling can no longer escape to the server thread.
- Removed a dead `WRITING` branch in the popup progress renderer.

### Changed

- Updated Apache Commons Compress to 1.28.0.
- Removed the "Requirements" section from the README.
- Version bumped to 1.2.0.3+26.2.

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
