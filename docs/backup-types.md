# Backup Types & Compression

Understanding how Just Enough Backups structures data helps you balance backup speed, disk space, and safety.

---

## The Three Backup Types

| Type | Reference Point | Contents | Speed | Disk Usage |
| :--- | :--- | :--- | :---: | :---: |
| **Full** | *None* | Entire world snapshot. | Slower | Largest |
| **Partial** | Most recent compatible backup of **any** type | Files changed since that backup. | Usually fastest | Usually smallest |
| **Differential** | Most recent **Full** backup | Files changed since that Full backup. | Fast | Grows until the next Full |

---

## Visualizing Backup Chains

| Chain Type | Base Reference | How it Works |
| :--- | :--- | :--- |
| **📦 Full Backup** | *None* (Root) | Complete standalone world snapshot. Foundation for subsequent backups. |
| **⚡ Partial Chain** | Preceding backup | Each archive records changes since the previous backup: `Full #1` → `Partial #1` → `Partial #2` |
| **🛡️ Differential Chain** | Base Full only | Each archive records all changes since the base Full: `Full #1` → `Differential #1` / `Differential #2` |

<div class="doc-chain-list" role="list" aria-label="Examples of backup chains">
  <div class="doc-chain-row" role="listitem">
    <span class="doc-chain-label">Partial chain</span>
    <div class="doc-chain-track">
      <span class="doc-chain-node"><strong>Full #1</strong><small>Complete snapshot</small></span>
      <span class="doc-chain-arrow" aria-hidden="true">→</span>
      <span class="doc-chain-node"><strong>Partial #1</strong><small>Changes since Full #1</small></span>
      <span class="doc-chain-arrow" aria-hidden="true">→</span>
      <span class="doc-chain-node"><strong>Partial #2</strong><small>Changes since Partial #1</small></span>
    </div>
  </div>
  <div class="doc-chain-row" role="listitem">
    <span class="doc-chain-label">Differential</span>
    <div class="doc-chain-track">
      <span class="doc-chain-node"><strong>Full #1</strong><small>Complete snapshot</small></span>
      <span class="doc-chain-arrow" aria-hidden="true">→</span>
      <div class="doc-chain-fork">
        <span class="doc-chain-node"><strong>Differential #1</strong><small>Changes since Full #1</small></span>
        <span class="doc-chain-node"><strong>Differential #2</strong><small>Also since Full #1</small></span>
      </div>
    </div>
  </div>
</div>

Partial backups use the most recent backup of any type as their reference. Differential backups each compare against the most recent Full snapshot.

### Which should I use?
- **Full**: Take these periodically (e.g. once a week or once a month) as solid recovery anchor points.
- **Partial**: Ideal for frequent automatic runs (e.g. hourly). They take seconds to create and use minimal disk space.
- **Differential**: Great for daily runs. If an intermediate daily backup is ever deleted, subsequent differential backups still restore directly against the Full backup.

---

## Automatic Dependency Protection

In basic backup tools, deleting an old Full backup to save space corrupts any incremental backups made after it.

> [!IMPORTANT]
> **JEB protects required bases**:
> JEB tracks the dependency tree of every archive. If a retention rule marks an old Full backup for removal, **JEB will keep it protected** as long as at least one active Partial or Differential backup depends on it.
> 
> The parent backup is only deleted once all dependent child backups have expired or been removed.

---

## Compression Engines: Deflate vs Zstandard

Archives use the `.zip` container with one of two compression methods:

| Feature | `DEFLATED` | `ZSTANDARD` |
| :--- | :---: | :---: |
| **ZIP Method** | Method 8 (Standard) | Method 93 |
| **Speed** | Standard | **Extremely Fast** |
| **Compression Ratio** | Often good | Depends on the world data; benchmark your own world |
| **Config Level Range** | `1` – `9` (Default: `6`) | `1` – `22` (Default: `3`) |
| **Compatibility** | Opens in any OS tool | Built into JEB, requires 7-Zip v22+ externally |

::: code-group
```json [Standard Deflate]
{
  "compressionMethod": "DEFLATED",
  "deflateCompressionLevel": 6
}
```

```json [Zstandard (Recommended for Large Servers)]
{
  "compressionMethod": "ZSTANDARD",
  "zstdCompressionLevel": 3
}
```
:::

### Memory Protection
For Zstandard compression, JEB uses temporary scratch files for large entries to limit memory use. Ensure the temporary directory has enough free space; this reduces memory pressure but cannot guarantee that an out-of-memory error is impossible.

---

## Free Space Pre-Flight Check

Before starting an archive, JEB checks available disk space on the backup drive:

```text
Required Free Space ≈ (estimated archive size * 2) + minimumFreeSpaceReserveMb
```

JEB estimates archive size from changed files and the last full backup's compression ratio. If temporary and destination directories are on different drives, it checks both. This preflight estimate cannot guarantee that a drive will not fill during a backup.
