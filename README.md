![Just Enough Backups](assets/banner.png)

# Just Enough Backups

Just Enough Backups is a Fabric mod that adds server-side world backups with a usable in-game workflow.

It supports:

- full, partial, and differential backups
- manual backups through commands and UI
- automatic scheduled backups
- retention by backup count
- retention by total backup size per world
- restore preparation from the backup browser
- a configurable client HUD popup with live preview
- optional Mod Menu integration

The mod is designed to keep the actual backup logic on the server side while exposing management and configuration through a client UI.

## Features

### Backup types

- **Full**: stores a complete snapshot of the world.
- **Partial**: stores changes relative to a base backup.
- **Differential**: stores changes relative to a base backup, while keeping a separate retention bucket from partial backups.

Dependencies between backups are tracked. Retention and delete operations respect those dependencies, so required base backups are preserved when newer partial or differential backups still depend on them.

### Automatic backups

Automatic backups are driven by the scheduler and can be configured to:

- run every `N` minutes
- pause when no players have been online since the last backup
- run on server start
- run on server stop

### Retention policy

Retention can be configured in two ways at the same time:

- **count-based retention**
  - keep the newest `X` full backups
  - keep the newest `X` partial backups
  - keep the newest `X` differential backups
- **size cap per world**
  - optional hard cap for the total size of a world's backup folder
  - applied to the final published backup set
  - respects dependency chains

If a new backup would exceed the configured space cap even after deleting every backup that is allowed to be removed, creation fails instead of publishing an invalid final state.

### Backup management UI

The backup browser includes:

- refresh
- create backup
- rename backup
- delete backup
- restore backup
- dependency-aware inline details
- search/filtering

Manual backup creation supports a custom file name from both the command line and the UI.

### Config UI and popup preview

The config screen exposes:

- backup mode and scheduler options
- retention rules
- permission level
- integrity mode
- backup directory
- popup text, colors, channels, and layout

The popup preview screen lets you move and preview the HUD state before saving.

## Commands

The root command is:

```text
/jeb
```

Available commands:

```text
/jeb now
/jeb now <name>

/jeb create full
/jeb create full <name>
/jeb create partial
/jeb create partial <name>
/jeb create differential
/jeb create differential <name>

/jeb list
/jeb next
/jeb restore <backupId>
/jeb config reload
```

Notes:

- `<name>` is optional and becomes the real `.zip` file name after sanitization.
- restore is prepared asynchronously and then the server shuts down so the restore can be applied safely on the next startup.
- command access follows the configured permission level.

## Default keybindings

Client-side keybindings:

- `B`: open backup management
- `N`: open config screen

These are regular Minecraft keybindings and can be changed in Controls.

## Configuration

The mod writes its config file to:

```text
config/justenoughbackups.json
```

Main config areas:

- `backupMode`
- `automaticBackupsEnabled`
- `pauseAutomaticBackupsWithoutPlayers`
- `backupOnServerStart`
- `backupOnServerStop`
- `automaticIntervalMinutes`
- `commandPermissionLevel`
- `integrityMode`
- `backupDirectory`
- `retention`
  - `full`
  - `incremental`
  - `differential`
  - `maxTotalSizeMb`
- `popup`

`backupDirectory` may be relative to the game directory or an absolute path.

## Backup naming

Manual backups support custom names:

- from commands: `/jeb now My backup`
- from the create dialog in the backup UI

If no custom name is provided, the mod uses the automatic naming scheme based on type and timestamp.

Rename uses the same sanitization rules as manual creation to keep naming behavior consistent.

## Requirements

From the current project configuration:

- Minecraft: `26.1.2`
- Fabric Loader: `0.18.5+`
- Fabric API: `0.147.0+26.1.2`
- Java: `25`

Optional:

- [Mod Menu](https://modrinth.com/mod/modmenu) for a cleaner config entry point

## Building

Build from the project root:

```bash
./gradlew build
```

For a compile-only check:

```bash
./gradlew compileJava
```

The project uses Fabric Loom and targets Java 25.

## Project structure

High-level layout:

```text
src/main/java/com/frandm/justenoughbackups
├─ backup/          Core backup, restore, retention, storage, progress
├─ client/          Client entrypoints, screens, UI chrome, popup HUD
├─ command/         Brigadier command registration
├─ config/          Persistent config and shared color parsing
├─ network/         UI payloads and request/response handling
└─ scheduler/       Automatic backup scheduling
```

Notable classes:

- `BackupService`: main service entry for create/list/rename/delete/restore
- `BackupStorage`: zip creation, manifest I/O, naming, sanitization
- `RetentionPolicy`: dependency-aware retention planning and deletion
- `BackupScheduler`: automatic backup timing and lifecycle hooks
- `BackupManagementScreen`: in-game backup browser
- `JEBConfigScreen`: in-game config editor

## Design notes

- Backup logic is server-side.
- The backup browser and config editor are client-side frontends for that logic.
- Retention is dependency-aware.
- The size cap is enforced per world backup directory, not globally across all worlds.
- Popup colors are stored as normalized `0xAARRGGBB` strings.

## License

This project is licensed under the [MIT License](LICENSE).
