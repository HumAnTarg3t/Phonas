# NAS Backup

An Android app that automatically backs up photos and videos from your phone to a NAS over SMB on your local network. No cloud. No internet required. Runs silently in the background on a configurable schedule.

---

## Features

- Backs up photos and videos to any SMB2/SMB3 NAS share
- Runs automatically in the background via WorkManager
- Only operates on Wi-Fi; skips if the NAS is unreachable
- Incremental backups — skips files already on the NAS
- SHA-256 verification of every transferred file (≤500 MB)
- Preserves folder structure and original filenames
- Credentials stored with Android Keystore encryption
- Simple three-screen UI: Status, Logs, Setup

---

## Requirements

- Android 10+ (API 29)
- Android Studio Ladybug (or newer) to build
- A NAS with SMB2/SMB3 sharing enabled
- Wi-Fi network that can reach the NAS

---

## Build Instructions

### First-time setup

1. Clone or copy this repository.
2. Open the project in Android Studio. It will prompt you to download Gradle 8.9 — accept.
3. Alternatively, if you have Gradle 8.9+ installed locally, run:
   ```
   gradle wrapper --gradle-version 8.9
   ```

### Build

```bash
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # release APK (minified)
```

### Install

```bash
./gradlew installDebug
```

### Run unit tests

```bash
./gradlew test
```

### Run instrumented tests (device or emulator required)

```bash
./gradlew connectedAndroidTest
```

---

## Architecture

```
UI (Compose)          3 screens: Status, Logs, Setup
    ↓                 ViewModels observe Flow from Room + WorkManager
BackupEngine          Orchestrates scan → duplicate check → transfer → verify
    ↓
SmbClient             SMBJ-based SMB2/3 client
FileScanner           Traverses SAF document trees
FileVerifier          SHA-256 streaming hash for local and remote files
DuplicateDetector     DB-first check, NAS fallback
    ↓
Room DB               Tracks backed-up files and session logs
DataStore             Non-sensitive settings (schedule, charging)
EncryptedSharedPrefs  Credentials (host, share, username, password)
WorkManager           Periodic and on-demand backup jobs
```

### Manual dependency injection

No Hilt. `AppContainer` holds singleton instances and is created in `BackupApplication`. ViewModels receive what they need via `ViewModelProvider.Factory`.

---

## Dependencies

| Library | Purpose |
|---|---|
| SMBJ 0.13 | SMB2/3 protocol for NAS communication |
| Room 2.6 | Local database for backup state and logs |
| WorkManager 2.10 | Background scheduling; survives Doze and reboots |
| DataStore Preferences | Non-sensitive settings storage |
| EncryptedSharedPreferences | Keystore-backed credential storage |
| Jetpack Compose + Material3 | UI |
| Navigation Compose | Bottom tab navigation |
| DocumentFile | SAF-based folder traversal |
| BouncyCastle | Cryptography backend required by SMBJ |

---

## Configuration

1. Open the app and go to **Setup**.
2. Enter:
   - **NAS Hostname or IP** — e.g. `192.168.1.100` or `nas.local`
   - **Shared Folder Name** — the SMB share name, e.g. `Photos`
   - **Username** and **Password**
3. Tap **Test Connection** to verify.
4. Tap **Add Folder** to select one or more phone folders to back up.
5. Choose a backup interval and optional charging requirement.
6. Tap **Save**. The first scheduled backup will run automatically.

Tap **Back Up Now** on the Status screen to trigger an immediate backup.

---

## NAS Path Structure

Files are organized on the NAS as:

```
[Share]/[Source folder name]/[relative subfolder]/filename.jpg
```

Example: if you select the `Camera` folder on your phone, photos appear at `Photos/Camera/IMG_001.jpg` on the NAS.

---

## Duplicate Detection

Before transferring any file, the app checks:

1. Local database — if the file was previously backed up with the same size and modification time, skip it.
2. NAS presence — if the file exists on the NAS with the same size, compute SHA-256 hashes and compare.
3. Only transfer if the file is genuinely new or changed.

For files larger than 500 MB, size match is sufficient to skip; hash computation is skipped to avoid re-downloading gigabytes.

---

## Transfer Verification

Every transferred file is verified before being marked as successful:

- **Files ≤500 MB**: SHA-256 is computed during upload (streaming, no re-read) and compared against the remote file.
- **Files >500 MB**: Remote file size is compared against expected size.

If verification fails, the incomplete remote file is deleted and the transfer is marked as failed for retry on the next run.

---

## Security Notes

- Credentials are stored with AES-256-GCM via Android Keystore.
- Passwords are never written to logs.
- The app does not make any internet connections; all traffic is local SMB.

---

## SMBJ and BouncyCastle

Android ships an incomplete BouncyCastle provider. `BackupApplication.onCreate()` removes it and registers the full BC provider, which SMBJ requires for SMB signing and encryption. This is a one-time setup that runs before any backup.

---

## Known Limitations

- `DocumentFile.listFiles()` is slow for very large directories due to SAF overhead. For a library of 50,000+ files, the initial scan can take several minutes. Subsequent runs are fast because most files are skipped via DB lookup.
- SMB stream API depends on SMBJ 0.13.x. If you upgrade SMBJ, verify that `file.outputStream` and `file.inputStream` are still available; SMBJ occasionally changes its streaming API between minor versions.
- The app does not retry individual files within a session — failures are logged and retried on the next scheduled backup run.
