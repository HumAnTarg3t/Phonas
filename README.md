# Phonas

An Android app that automatically backs up photos and videos from your phone to a NAS over SMB on your local network. No cloud. No internet required. Runs silently in the background on a configurable schedule.

---

## Features

- Backs up photos and videos to any SMB2/SMB3 NAS share
- Runs automatically in the background via WorkManager
- Only operates on Wi-Fi; skips if the NAS is unreachable
- Incremental backups — skips files already on the NAS
- SHA-256 verification of every transferred file (≤500 MB)
- Preserves folder structure and original filenames
- Optional date filter — only back up files modified on or after a chosen date
- Configurable log retention (25 / 50 / 100 / 200 / 500 sessions)
- Export and import the full configuration as a JSON file
- Credentials stored with Android Keystore encryption (password excluded from exports)
- Simple three-screen UI: Status, Logs, Setup

---

## Requirements

- Android 10+ (API 29)
- Android Studio Ladybug or newer to build
- A NAS with SMB2/SMB3 sharing enabled
- Wi-Fi network that can reach the NAS

---

## Build Instructions

### First-time setup

1. Clone or copy this repository.
2. Open the project in Android Studio. It will download Gradle and sync dependencies automatically on first open.

### Build

```bash
.\gradlew.bat assembleDebug        # debug APK
.\gradlew.bat assembleRelease      # release APK (minified)
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Install (USB debugging)

```bash
.\gradlew.bat installDebug
```

For sideloading without USB debugging, see [SIDELOAD.md](SIDELOAD.md).

### Run unit tests

```bash
.\gradlew.bat test
```

### Run instrumented tests (device or emulator required)

```bash
.\gradlew.bat connectedAndroidTest
```

---

## Architecture

```
UI (Compose)          3 screens: Status, Logs, Setup
    ↓                 ViewModels observe Flow from Room + WorkManager
BackupEngine          Orchestrates scan → duplicate check → transfer → verify
    ↓
SmbClient             SMBJ-based SMB2/3 client
FileScanner           Traverses SAF document trees; applies date filter
FileVerifier          SHA-256 streaming hash for local and remote files
DuplicateDetector     DB-first check, NAS fallback
    ↓
Room DB               Tracks backed-up files and session logs (capped by maxLogEntries)
DataStore             Non-sensitive settings (schedule, charging, date filter, log retention)
EncryptedSharedPrefs  Credentials (host, share, username, password) — Keystore-backed
WorkManager           Periodic and on-demand backup jobs
```

### Manual dependency injection

No Hilt. `AppContainer` holds singleton instances and is created in `BackupApplication`. ViewModels receive what they need via `ViewModelProvider.Factory`.

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| SMBJ | 0.13.0 | SMB2/3 protocol for NAS communication |
| BouncyCastle | 1.72 | Cryptography backend required by SMBJ |
| Room | 2.6.1 | Local database for backup state and logs |
| WorkManager | 2.10.0 | Background scheduling; survives Doze and reboots |
| DataStore Preferences | 1.1.1 | Non-sensitive settings storage |
| security-crypto | 1.1.0-alpha06 | Keystore-backed EncryptedSharedPreferences |
| Jetpack Compose + Material3 | BOM 2024.12.01 | UI |
| Navigation Compose | 2.8.4 | Bottom tab navigation |
| DocumentFile | 1.0.1 | SAF-based folder traversal |

---

## Configuration

1. Open the app and go to **Setup**.
2. Enter your NAS details:
   - **NAS Hostname or IP** — e.g. `192.168.1.100` or `nas.local`
   - **Shared Folder Name** — the SMB share name, e.g. `Photos`
   - **Username** and **Password**
3. Tap **Test Connection** to verify before saving.
4. Tap **Add Folder** to select one or more phone folders to back up.
5. Choose a **backup interval** (1h / 6h / 12h / 24h) and optional charging-only requirement.
6. Optionally set **Keep backup logs** (how many backup sessions to retain in history).
7. Optionally set **Skip Files Older Than** to ignore files before a specific date.
8. Tap **Save**. The first scheduled backup runs automatically when on Wi-Fi.

Tap **Back Up Now** on the Status screen to trigger an immediate backup.

---

## Date Filter

The "Skip Files Older Than" setting lets you start the first backup from a specific date rather than copying your entire photo library. Set it to e.g. 1 June 2026, and only files modified on or after that date will be backed up — on every run, not just the first.

The filter is permanent. Files before the cutoff are never backed up. To back up everything, clear the date in Setup.

---

## Export / Import Configuration

In the Setup screen, tap **Export** to save all settings (NAS host, share name, username, schedule, date filter, folder list) to a JSON file. The password is intentionally excluded from the export — you will need to re-enter it after importing on a new device.

Tap **Import** and select a previously exported JSON file to restore settings. On the same device, folder selections are also restored. On a new device, you will need to re-add folders manually via **Add Folder**.

---

## NAS Path Structure

```
[Share]/[Source folder name]/[relative subfolder]/filename.jpg
```

Example: selecting the `Camera` folder on your phone writes files to `Photos\Camera\IMG_001.jpg` on the NAS.

---

## Duplicate Detection

Before transferring any file:

1. **DB check** — if the file was previously backed up with the same size and modification time, skip it immediately.
2. **NAS check** — if not in the DB (e.g. after reinstall), check whether the file exists on the NAS with the same size. For files ≤500 MB, also compare SHA-256 hashes.
3. Transfer only if the file is genuinely new or changed.

---

## Transfer Verification

Every transferred file is verified before being marked successful:

- **≤500 MB**: SHA-256 is computed during the upload read pass (no re-read) and compared against the remote file.
- **>500 MB**: Remote file size is compared against expected size.

If verification fails, the partial remote file is deleted and the transfer is retried on the next run.

---

## Security

- Credentials are stored with AES-256-GCM via Android Keystore (hardware-backed on modern devices).
- Passwords are never written to logs or export files.
- No internet connections are made; all traffic stays on the local network over SMB.
- Android's mandatory full-disk encryption (API 29+) protects the Room database and DataStore at the OS level.

---

## SMBJ and BouncyCastle

Android ships an incomplete BouncyCastle provider. `BackupApplication.onCreate()` removes it and registers the full BC provider before any backup runs. This is required for SMB signing and encryption in SMBJ.

---

## Known Limitations

- `DocumentFile.listFiles()` is slow for very large directories due to SAF overhead. For a library of 50,000+ files, the initial scan can take several minutes. Subsequent runs are fast because most files are skipped via the DB lookup.
- The app does not retry individual failed files within a session. Failures are logged and retried on the next scheduled run.
- Folder URI permissions granted via SAF are device-specific. Importing a config on a new device restores all other settings but requires re-selecting folders manually.
