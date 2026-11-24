# Cloud Storage (S3 & WebDAV)

Just Enough Backups supports automated offsite backup uploads to **Amazon S3 / S3-compatible object stores** and **WebDAV servers**.

---

## Security: Environment Variables

> [!WARNING] Keep Secrets Out of Config Files
> Avoid putting plaintext access keys and passwords inside `config/justenoughbackups.json`, especially if your server directory is shared or tracked in Git.
> 
> Use system environment variables instead — they take priority over the config file:

| Provider | Environment Variable | Value |
| :--- | :--- | :--- |
| **S3** | `JEB_S3_ACCESS_KEY_ID` | Your S3 Access Key ID |
| **S3** | `JEB_S3_SECRET_ACCESS_KEY` | Your S3 Secret Access Key |
| **WebDAV** | `JEB_WEBDAV_USERNAME` | WebDAV login username |
| **WebDAV** | `JEB_WEBDAV_PASSWORD` | WebDAV login password |

---

## S3-Compatible Setup

Set `"provider": "S3"` in `config/justenoughbackups.json`:

::: code-group
```json [Amazon Web Services (AWS)]
{
  "remoteStorage": {
    "enabled": true,
    "provider": "S3",
    "s3Endpoint": "",
    "s3Region": "us-east-1",
    "s3Bucket": "my-minecraft-backups",
    "s3Prefix": "survival-server/",
    "maxRetries": 3
  }
}
```

```json [Cloudflare R2]
{
  "remoteStorage": {
    "enabled": true,
    "provider": "S3",
    "s3Endpoint": "https://<ACCOUNT_ID>.r2.cloudflarestorage.com",
    "s3Region": "auto",
    "s3Bucket": "my-minecraft-backups",
    "s3Prefix": "survival-server/",
    "maxRetries": 3
  }
}
```

```json [MinIO (Self-Hosted)]
{
  "remoteStorage": {
    "enabled": true,
    "provider": "S3",
    "s3Endpoint": "https://minio.internal.lan:9000",
    "s3Region": "us-east-1",
    "s3Bucket": "backups",
    "s3Prefix": "minecraft/",
    "maxRetries": 3
  }
}
```

```json [Backblaze B2]
{
  "remoteStorage": {
    "enabled": true,
    "provider": "S3",
    "s3Endpoint": "https://s3.us-east-005.backblazeb2.com",
    "s3Region": "us-east-005",
    "s3Bucket": "my-b2-bucket",
    "s3Prefix": "minecraft/",
    "maxRetries": 3
  }
}
```
:::

> [!NOTE] Endpoint Rules
> - Remote endpoints **must use HTTPS**. Plain HTTP is only accepted for local loopback development (`127.0.0.1` / `localhost`).
> - Prefixes can be entered with or without a trailing slash; JEB normalizes them when building remote object paths.

---

## WebDAV Setup (Nextcloud / ownCloud)

Set `"provider": "WEBDAV"` in `config/justenoughbackups.json`:

```json
{
  "remoteStorage": {
    "enabled": true,
    "provider": "WEBDAV",
    "webDavUrl": "https://cloud.example.com/remote.php/dav/files/myuser/",
    "webDavPrefix": "MinecraftBackups/",
    "maxRetries": 3
  }
}
```

- **`webDavUrl`**: The collection endpoint for your user's WebDAV files.
- **`webDavPrefix`**: Folder inside your WebDAV account where backups will be stored. A trailing slash is optional.

---

## Upload Reliability & Retries

- **Pre-Upload Integrity & SHA-256 Checksum**: Under `STRICT` integrity mode, `.mca` region files are validated before upload begins. After upload, the remote archive is published with its SHA-256 checksum and verified on restore.
- **Retries (`maxRetries`)**: JEB retries transient network and HTTP `408`/`429`/`5xx` failures up to the configured count (0–10; default 3). WebDAV redirects are restricted to the same origin so credentials are never forwarded to another host. A WebDAV upload retry restarts the upload; interrupted uploads are not resumed after a server restart.

Uploads are streamed and published to the catalog only after checksum verification. Remote restores download the required archive chain into staging and verify it before preparing the restore. When remote storage is enabled, retention settings also apply to remote archives; dependency chains are protected there too.
