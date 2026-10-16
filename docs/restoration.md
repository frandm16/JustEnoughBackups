# World Restoration

JEB prepares a restore in staging, verifies the reconstructed snapshot, creates a safety backup, and applies the replacement during a clean server shutdown. Keep an independent copy of important worlds as well; no restore process can protect against every hardware or filesystem failure.

---

## Restoring a Backup

You can trigger a restore either in-game or via the server console:

::: code-group
```text [In-Game Interface]
1. Press B to open the Backup Management Menu.
2. Select the backup you wish to restore.
3. Click "Restore".
4. Review the chain summary and confirm.
```

```text [Server Command]
# Using backup name (tab completion supported):
/jeb restore full-2026-10-16_12-00-00
```
:::

---

## How the Safe Restore Process Works

<div class="doc-flow doc-restore-flow" role="list" aria-label="Safe restore sequence">
  <div class="doc-flow-step" role="listitem"><span class="doc-flow-index">01</span><strong>Select a restore point</strong><span>Choose it in the menu or run <code>/jeb restore &lt;name&gt;</code>.</span></div>
  <span class="doc-flow-arrow" aria-hidden="true">→</span>
  <div class="doc-flow-step" role="listitem"><span class="doc-flow-index">02</span><strong>Rebuild in staging</strong><span>Apply the Full base and required incremental archives in order.</span></div>
  <span class="doc-flow-arrow" aria-hidden="true">→</span>
  <div class="doc-flow-step" role="listitem"><span class="doc-flow-index">03</span><strong>Verify and protect</strong><span>Check the reconstructed files and create a safety backup.</span></div>
  <span class="doc-flow-arrow" aria-hidden="true">→</span>
  <div class="doc-flow-step" role="listitem"><span class="doc-flow-index">04</span><strong>Apply on shutdown</strong><span>Swap in the restored world after a clean server shutdown.</span></div>
</div>

The server stays online while the restore is prepared. Once staging and the safety backup succeed, JEB performs the world swap during shutdown; start the server again to load the restored world.

JEB records the restore phase before changing world directories. If the server stops during the swap, the next startup resumes the pending operation. If applying or verifying the restored world fails, JEB attempts to put the previous world back when it is available. Check the server log for the restore result.

---

## Offline Disaster Recovery (Manual)

If your server machine is offline or you cannot boot Minecraft:

1. **If using a Full Backup**:
   - Simply extract the `.zip` archive into your server's root world folder.
2. **If using an Incremental or Differential Backup**:
   - Extract the base **Full** backup first.
   - Follow the target's dependency chain in order, applying each required archive over the previous contents. Do not apply unrelated backups.

> [!NOTE] Zstandard Archives
> If your backups use `compressionMethod: "ZSTANDARD"`, open them with [7-Zip](https://www.7-zip.org/) (v22+) or PeaZip, which support ZIP method 93.
