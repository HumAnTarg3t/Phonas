# Phonas

An Android app that automatically backs up photos and videos from your phone to a NAS over SMB on your local network. No cloud. No internet required. Runs silently in the background on a configurable schedule.

---

## Features

- Backs up photos and videos to any SMB2/SMB3 NAS share
- Runs automatically in the background via WorkManager
- Only operates on Wi-Fi; skips if the NAS is unreachable
- Incremental backups — skips files already on the NAS
- SHA-256 verification of every transferred file (≤500 MB)
- Preserves folder structure, original filenames, and original file modification dates on the NAS
- **Scan all device media** — backs up every photo and video on the device (DCIM, WhatsApp, Screenshots, Downloads, etc.) without selecting folders manually
- Optional per-folder NAS prefix to organise files under custom subdirectories
- Optional date filter — only back up files modified on or after a chosen date
- Clickable log entries showing per-file detail (copied / skipped / failed) for each backup session
- Configurable log retention (25 / 50 / 100 / 200 / 500 sessions)
- Export and import the full configuration as a JSON file
- Credentials stored with Android Keystore encryption (password excluded from exports)
- Database reset button for testing
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

In Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Install (USB debugging)

In Android Studio: **Run → Run 'app'**

For sideloading without USB debugging, see [SIDELOAD.md](SIDELOAD.md).

### Run unit tests

In Android Studio: **Run → Run All Tests**, or right-click the `test` source set and select **Run Tests**.

---

## Architecture

```
UI (Compose)          3 screens: Status, Logs, Setup
    ↓                 ViewModels observe Flow from Room + WorkManager
BackupEngine          Orchestrates scan → duplicate check → transfer → verify
    ↓
SmbClient             SMBJ-based SMB2/3 client; sets original file timestamps via FileBasicInformation
FileScanner           Traverses SAF document trees; applies date filter
MediaStoreScanner     Queries MediaStore for all device photos and videos (scan-all mode)
FileVerifier          SHA-256 streaming hash for local and remote files
DuplicateDetector     DB-first check, NAS fallback
    ↓
Room DB               Tracks backed-up files, session logs, and per-file session detail (capped by maxLogEntries)
DataStore             Non-sensitive settings (schedule, charging, date filter, log retention, scan mode)
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
4. Choose how to select files:
   - **Scan all device media** — turn this on to back up everything on the device (photos, videos, WhatsApp, Screenshots, etc.) without selecting individual folders. The app will request storage permission on first enable.
   - **Manual folders** — tap **Add Folder** to select specific folders. An optional NAS prefix can be set per folder to organise files under a custom subdirectory on the NAS.
5. Choose a **backup interval** (1h / 6h / 12h / 24h) and optional charging-only requirement.
6. Optionally set **Keep backup logs** (how many backup sessions to retain in history).
7. Optionally set **Skip Files Older Than** to ignore files before a specific date.
8. Tap **Save**. A confirmation message appears briefly. The first scheduled backup runs automatically when on Wi-Fi.

Tap **Back Up Now** on the Status screen to trigger an immediate backup.

---

## Date Filter

The "Skip Files Older Than" setting lets you start the first backup from a specific date rather than copying your entire photo library. Set it to e.g. 1 May 2026, and only files modified on or after that date will be backed up — on every run, not just the first.

The filter is permanent. Files before the cutoff are never backed up. To back up everything, tap **Clear** next to the date in Setup.

Use the date picker carefully — selecting a future date will result in zero files being backed up.

The date filter is not a "since last backup" marker. It is a fixed cutoff that applies on every run. If you remove the date filter after a partial backup, the next run will scan all photos on the device and copy any that have never been backed up — including photos older than your previous cutoff. To avoid this, either keep the filter in place or advance it to today's date after completing a full backup.

---

## Viewing Backup Details

On the Logs screen, tap any backup session to see a full per-file breakdown:

- **Copied** — file was transferred and verified successfully
- **Skipped** — file was already on the NAS, no transfer needed
- **Failed** — transfer or verification failed (error message shown)

The NAS path and file size are shown for each entry.

---

## Export / Import Configuration

In the Setup screen, tap **Export** to save all settings (NAS host, share name, username, schedule, date filter, folder list) to a JSON file. The password is intentionally excluded from the export — you will need to re-enter it after importing on a new device.

Tap **Import** and select a previously exported JSON file to restore settings. On the same device, folder selections are also restored. On a new device, you will need to re-add folders manually via **Add Folder**.

---

## NAS Path Structure

**Scan all device media mode:**
```
[Share]/[relative path from MediaStore]/filename.jpg
```
Example: a WhatsApp image at `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/img.jpg` on the phone is stored at `Photos\Android\media\com.whatsapp\WhatsApp\Media\WhatsApp Images\img.jpg` on the NAS.

**Manual folder mode:**
```
[Share]/[optional prefix]/[relative subfolder]/filename.jpg
```
Example: selecting the `DCIM/Camera` folder with prefix `camera` writes files to `Photos\camera\IMG_001.jpg`.

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

The original file modification date is preserved on the NAS copy via SMB `FileBasicInformation`.

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

## NAS Recycle Bin

If files appear in a `#Recycle` folder on the NAS instead of being deleted cleanly, the NAS recycle bin feature is enabled on the backup share. This is a NAS setting, not an app behaviour. Disable it in your NAS admin panel:

- **Synology**: Shared Folder → Edit → uncheck "Enable Recycle Bin"
- **QNAP**: Shared Folders → Edit → uncheck "Enable Recycle Bin"
- **TrueNAS**: Dataset properties → disable recycle bin / shadow copies

---

## Known Limitations

- SAF-based folder scanning (`DocumentFile.listFiles()`) is slow for very large directories. For a library of 50,000+ files, the initial scan can take several minutes. Use "Scan all device media" mode to avoid this — MediaStore queries are fast regardless of library size.
- The app does not retry individual failed files within a session. Failures are logged and retried on the next scheduled run.
- Folder URI permissions granted via SAF are device-specific. Importing a config on a new device restores all other settings but requires re-selecting folders manually.
- In "Scan all device media" mode, storage permission (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` on Android 13+) must be granted. The app prompts for this when the toggle is first enabled.
