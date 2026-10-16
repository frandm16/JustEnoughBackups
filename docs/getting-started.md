# Getting Started

**Just Enough Backups (JEB)** brings fast, multithreaded world backups to Fabric with a complete in-game graphical interface.

<div class="doc-flow" role="list" aria-label="Getting started with Just Enough Backups">
  <div class="doc-flow-step" role="listitem"><span class="doc-flow-index">01</span><strong>Install on the server</strong><span>Add JEB and Fabric API to the server's <code>mods/</code> folder.</span></div>
  <span class="doc-flow-arrow" aria-hidden="true">→</span>
  <div class="doc-flow-step" role="listitem"><span class="doc-flow-index">02</span><strong>Start the server</strong><span>JEB creates its default configuration automatically.</span></div>
  <span class="doc-flow-arrow" aria-hidden="true">→</span>
  <div class="doc-flow-step" role="listitem"><span class="doc-flow-index">03</span><strong>Take a backup</strong><span>Use the in-game menu or run <code>/jeb now</code>.</span></div>
</div>

---

## Requirements

| Component | Minimum Version | Note |
| :--- | :--- | :--- |
| **Minecraft** | Minecraft 26.3 or later | The current build targets Minecraft 26.3. Check [Modrinth](https://modrinth.com/mod/justenoughbackups-jeb) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/just-enough-backups) for compatible builds. |
| **Java** | Java 25 or later | Required by the current build. |
| **Fabric Loader** | `>= 0.19.5` | Required by the current build. |
| **Fabric API** | Required | Matching your Minecraft version |
| **Mod Menu** | Optional | Recommended on client for graphical settings access |

---

## Installation

### Dedicated Servers <Badge type="tip" text="Server-Side" />
1. Download the `.jar` for your Minecraft version from [Modrinth](https://modrinth.com/mod/justenoughbackups-jeb) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/just-enough-backups).
2. Place it in your server's `mods/` directory along with **Fabric API**.
3. Start the server. The default configuration file will be generated at `config/justenoughbackups.json`.

> [!TIP]
> Players connecting to your server **do not need the mod installed** on their game clients. JEB runs entirely server-side.

### Singleplayer & LAN <Badge type="info" text="Client & Server" />
1. Download the `.jar` for your Minecraft version from [Modrinth](https://modrinth.com/mod/justenoughbackups-jeb) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/just-enough-backups), then put it in your `.minecraft/mods/` directory.
2. Launch Minecraft. You have immediate access to both automated backups and the in-game GUI.

### Clients Connecting to a JEB Server <Badge type="tip" text="Optional Client" />
If players install JEB on their client when playing on a server that also has JEB installed:
- Admins with permission level `2+` (OPs) can open the **Backup Management Menu** (<kbd>B</kbd>) directly inside the server.
- Players see live on-screen HUD toast notifications when backups complete.

The server-side mod is required for backup and restore operations. A client-only installation cannot manage a server's worlds.

---

## Default Controls

When installed on the client, JEB registers two hotkeys:

| Key | Menu | Description |
| :---: | :--- | :--- |
| <kbd>B</kbd> | **Backup Management** | Open the backup browser to inspect, create, rename, delete, or restore backups. |
| <kbd>N</kbd> | **Config Screen** | Open settings with live HUD position and color preview. |

*Keys can be customized in **Options → Controls → Key Binds** under the "Just Enough Backups" category.*

---

## Creating Your First Backup

You can take a backup at any time using either the UI or chat:

::: code-group
```text [In-Game Menu]
1. Press B to open the Backup Management screen.
2. Click "Create Backup".
3. Enter an optional custom name (e.g., "Before Ender Dragon") and confirm.
```

```text [Chat Command]
# Quick full backup (the default backup mode):
/jeb now

# Or specify a custom name:
/jeb now Before_Ender_Dragon
```
:::

The backup runs asynchronously. A notification appears when it finishes. By default, full backups are scheduled every four hours; partial and differential schedules are disabled.
