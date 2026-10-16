# Commands Reference

All mod commands use the `/jeb` root namespace.

---

## Command Summary

| Command | Description |
| :--- | :--- |
| `/jeb now [name]` | Immediately triggers a backup using the default configured backup type. |
| `/jeb create full [name]` | Creates a complete snapshot of the entire world. |
| `/jeb create partial [name]` | Creates an incremental backup containing changes since the most recent backup of any type. |
| `/jeb create differential [name]` | Creates an incremental backup containing changes since the most recent **Full** backup. |
| `/jeb list` | Lists all backups for the current world with type, size, and dependencies. |
| `/jeb next` | Shows the time remaining for each enabled automatic schedule, or reports if one is ready or schedules are disabled. |
| `/jeb restore <backup>` | Prepares a safe world restoration from the chosen archive. Tab completion supported. |
| `/jeb config reload` | Hot-reloads `config/justenoughbackups.json` without restarting the server. |

---

## Command Details & Examples

### Triggering Backups

```text
/jeb now
/jeb now dragon_fight_prep
/jeb create full monthly_archive
/jeb create partial daily_incremental
/jeb create differential weekly_diff
```

- If `[name]` is omitted, JEB automatically generates a type-prefixed timestamped name (e.g. `full-2026-10-16_18-00-00.zip`).
- If `[name]` is supplied, it is sanitized to remove illegal filesystem characters and appended with `.zip`.

---

### Inspecting Upcoming Schedules: `/jeb next`

Running `/jeb next` reports the time remaining for each enabled schedule. The output is sorted by the next scheduled run; it does not show wall-clock timestamps:

```text
Next partial backup in 24m 0s.
Next differential backup in 1h 45m 0s.
Next full backup in 6h 12m 0s.
```

---

### Restoring Backups: `/jeb restore`

```text
/jeb restore full-2026-10-16_12-00-00
```

- You do not need to type `.zip` — tab completion automatically suggests all available backup names.
- When executed, JEB extracts and verifies the archive in staging, creates a safety backup of your current world, and cleanly shuts down the server to finalize the swap.

---

## Permissions Configuration

Command access is controlled by `commandPermissionLevel` in `config/justenoughbackups.json` (Default: `2`):

```json
{
  "commandPermissionLevel": 2
}
```

| Level | Typical role | Meaning in Minecraft |
| :---: | :--- | :--- |
| `0` | All Players | Everyone on the server can execute `/jeb` commands. |
| `1` | Basic command access | Can use commands requiring level 1. |
| `2` | Operator *(Default)* | Can use commands requiring level 2, including `/jeb`. |
| `3` | Administrator | Can use commands requiring level 3. |
| `4` | Server owner | Can use commands requiring the highest permission level. |

The dedicated server console has permission level 4. JEB checks the configured minimum for every `/jeb` command.
