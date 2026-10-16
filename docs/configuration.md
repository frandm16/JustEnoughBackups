# Configuration Reference

The primary server configuration file is located at:

```text
config/justenoughbackups.json
```

It is generated on first startup with default settings. On dedicated servers, reload changes with `/jeb config reload` without restarting. HUD appearance is configured by each client.

When an older configuration has the legacy `automaticBackupsEnabled` and `automaticIntervalMinutes` fields but no `automaticSchedule`, JEB migrates that single schedule to the type selected by `backupMode` and initializes the other types as disabled.

---

## Core Configuration Example

This example shows the main server settings. The generated file also includes `remoteStorage`, `retention`, and `popup` sections; see below and the [remote storage guide](./remote-storage) for details.

```json
{
  "backupMode": "FULL",
  "pauseAutomaticBackupsWithoutPlayers": true,
  "backupOnServerStart": false,
  "backupOnServerStop": false,
  "startupBackupThreshold": {
    "fullMinutes": 0,
    "partialMinutes": 0,
    "differentialMinutes": 0
  },
  "automaticSchedule": {
    "full": {
      "enabled": true,
      "intervalMinutes": 240
    },
    "differential": {
      "enabled": false,
      "intervalMinutes": 120
    },
    "partial": {
      "enabled": false,
      "intervalMinutes": 60
    }
  },
  "automaticBackupWarningEnabled": true,
  "automaticBackupWarningMinutes": 5,
  "commandPermissionLevel": 2,
  "messageChannel": "ACTION_BAR",
  "integrityMode": "STRICT",
  "includeSummaryFile": false,
  "minimumFreeSpaceReserveMb": 512,
  "threadCount": 6,
  "compressionMethod": "DEFLATED",
  "deflateCompressionLevel": 6,
  "zstdCompressionLevel": 3,
  "backupDirectory": "backups",
  "tempBackupDirectory": "",
  "excludedPaths": [],
  "excludeSafetyBackupsFromIncremental": false,
  "retention": {
    "full": 5,
    "incremental": 20,
    "differential": 10,
    "maxTotalSizeMb": 0
  }
}
```

---

## 1. Automation & Schedulers

| Property | Default | Description |
| :--- | :---: | :--- |
| `backupMode` | `"FULL"` | Default backup type used for `/jeb now` and server start/stop backups. |
| `pauseAutomaticBackupsWithoutPlayers` | `true` | Pauses scheduled backups until a player has been online since the previous backup. |
| `backupOnServerStart` | `false` | Takes a backup shortly after the server finishes starting. |
| `backupOnServerStop` | `false` | Takes a backup right before the server process shuts down. |
| `automaticSchedule.full.enabled` | `true` | Enables scheduled Full backups. |
| `automaticSchedule.full.intervalMinutes` | `240` | Interval in minutes between Full backups (default: 4 hours). |
| `automaticSchedule.differential.enabled` | `false` | Enables scheduled Differential backups. |
| `automaticSchedule.differential.intervalMinutes` | `120` | Interval in minutes between Differential backups (default: 2 hours). |
| `automaticSchedule.partial.enabled` | `false` | Enables scheduled Partial backups. |
| `automaticSchedule.partial.intervalMinutes` | `60` | Interval in minutes between Partial backups (default: 1 hour). |
| `automaticBackupWarningEnabled` | `true` | Broadcasts an advance warning before an automatic backup starts. |
| `automaticBackupWarningMinutes` | `5` | Minutes before the backup to send the advance warning. |
| `startupBackupThreshold.fullMinutes` (and partial/differential) | `0` | Minimum age of the most recent backup of that type before a server-start backup runs. Zero disables the age threshold. |

---

## 2. Retention Policies

| Property | Default | Description |
| :--- | :---: | :--- |
| `retention.full` | `5` | Maximum number of **Full** backups to keep. (Base backups protected while children need them). |
| `retention.incremental` | `20` | Maximum number of **Partial** backups to keep. |
| `retention.differential` | `10` | Maximum number of **Differential** backups to keep. |
| `retention.maxTotalSizeMb` | `0` | Hard disk space cap in MB for all backups combined. Set to `0` to disable. |
| `excludeSafetyBackupsFromIncremental` | `false` | When `true`, automatic `Old_World` safety backups are not used as base references for incremental chains. |

---

## 3. Storage Paths & Exclusions

| Property | Default | Description |
| :--- | :---: | :--- |
| `backupDirectory` | `"backups"` | Destination folder for finalized `.zip` archives. Accepts relative or absolute paths. |
| `tempBackupDirectory` | `""` | Working directory where archives are staged and compressed before being moved to `backupDirectory`. Leave empty to compress in-place. |
| `excludedPaths` | `[]` | List of paths relative to the world directory to omit from backups (e.g. `["voxy", "dynmap/web/tiles"]`). |
| `minimumFreeSpaceReserveMb` | `512` | Minimum free disk space in MB required on the target drive before a backup is allowed to start. |

Relative paths are resolved from the game directory. JEB stores local archives in a per-world subdirectory, for example:

```text
backups/<world-id>/<backup-name>.zip
```

`<world-id>` is the world's internal level ID, falling back to its folder name when unavailable. Keep `tempBackupDirectory` outside the world folder; JEB rejects a temporary directory that overlaps it. If temporary and final directories are on separate file systems, both volumes need enough free space for the estimated archive plus the configured reserve.

> [!TIP] Optimizing Network / External Storage
> If your `backupDirectory` points to an external USB drive or NFS mount, set `tempBackupDirectory` to a fast local SSD (e.g. `"run/temp_backups"`). JEB will compress locally at full speed and only transfer the completed `.zip` file over the bus/network.

---

## 4. Performance & Compression

| Property | Default | Description |
| :--- | :---: | :--- |
| `threadCount` | `max(1, cores - 2)` | Number of worker threads used for parallel hashing, compression, and extraction. Valid range: `1` to the number of available CPU cores. |
| `compressionMethod` | `"DEFLATED"` | Archive compression engine: `"DEFLATED"` (standard ZIP) or `"ZSTANDARD"` (high speed). |
| `deflateCompressionLevel` | `6` | Deflate level (`1` fastest to `9` smallest). |
| `zstdCompressionLevel` | `3` | Zstandard level (`1` fastest to `22` ultra). |
| `integrityMode` | `"STRICT"` | Controls how broken files and damaged restores are handled; see the modes below. |
| `includeSummaryFile` | `false` | Adds `summary.txt` to each archive with world details, Minecraft/JEB versions, and the loaded mod list. Restoring the archive also places this file in the world folder. |

Integrity modes:

| Mode | Behavior |
| :--- | :--- |
| `STRICT` | Discards failed backups and blocks restores that fail integrity checks. |
| `PERMISSIVE` | May keep partial backups, but still blocks damaged restores. |
| `VERY_PERMISSIVE` | Allows a damaged restore to continue with a warning; intended for manual recovery. |

---

## 5. Permissions & Announcements

| Property | Default | Description |
| :--- | :---: | :--- |
| `commandPermissionLevel` | `2` | Required OP level to execute `/jeb` commands (`0` = all players, `2` = OP, `4` = admin). |
| `messageChannel` | `"ACTION_BAR"` | Where server announcements appear: `"ACTION_BAR"`, `"CHAT"`, or `"OFF"`. Client HUD popups are configured separately. |

## 6. Client Popup

The generated `popup` object controls the on-screen backup notification. These are personal client settings, including when connecting to a dedicated server.

| Setting | Purpose |
| :--- | :--- |
| `enabled`, `showTitle`, `centerText`, `showBorder` | Toggle the popup and its title, text alignment, and border. |
| `x`, `y`, `xRatio`, `yRatio` | Position the popup in pixels or relative to the screen. Use the in-game preview to place it. |
| `backgroundColor`, `runningColor`, `completedColor`, `failedColor`, `textColor` | ARGB color values in `0xAARRGGBB` form. |
| `title`, `runningText`, `scanningText`, `completedText`, `failedText` | Customize displayed text. Status templates support `{reason}` and `{type}` placeholders. |
