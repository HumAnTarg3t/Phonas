# Phonas

An Android app that automatically backs up photos and videos from your phone to a NAS over SMB on your local network. No cloud. No internet required. Runs silently in the background on a configurable schedule.

---

## Features

- Backs up photos and videos to any SMB2/SMB3 NAS share
- Runs automatically in the background via a single WorkManager periodic job — exactly once per interval, including while the screen is locked; retries on WorkManager's exponential backoff when the NAS is unreachable
- Only operates on unmetered (Wi-Fi) networks
- Incremental backups — skips files already on the NAS
- Atomic uploads: each file is written to a temporary name, verified, then renamed into place, so a mid-transfer failure never overwrites the previous good copy
- SHA-256 verification of every transferred file (full-hash up to 2 GB; size-only above)
- Preserves folder structure, original filenames, and original file modification dates on the NAS
- **Scan all device media** — backs up every photo and video on the device (DCIM, WhatsApp, Telegram, Signal, Screenshots, Downloads, etc.) without selecting folders manually. With **All Files Access** granted it also captures media the gallery hides (e.g. WhatsApp group chats with "Media visibility" off), which a MediaStore-only scan cannot see
- Near-real-time trigger: a debounced backup starts shortly after new media is saved
- Optional per-folder NAS prefix to organise files under custom subdirectories
- Optional date filter — only back up files added on or after a chosen date (a future date can't be selected)
- Clickable log entries showing per-file detail (copied / skipped / failed) for each backup session, with status filter and tap-to-open on device
- Configurable log retention (25 / 50 / 100 / 200 / 500 sessions)
- Export and import the full configuration as a JSON file
- Credentials stored with Android Keystore encryption (password excluded from exports)
- Database reset button for testing
- Abort button on the Status screen to stop a running backup immediately
- Next scheduled backup time shown on both the Status and Setup screens
- Simple three-screen UI: Status, Logs, Setup

---

## Requirements

- Android 10+ (API 29). Full "Scan all device media" coverage (All Files Access) requires Android 11+ (API 30); on Android 10 scan-all falls back to MediaStore only.
- Android Studio Ladybug or newer to build
- JDK 17+ for command-line builds (Android Studio's bundled JBR works; a JRE will not)
- A NAS with SMB2/SMB3 sharing enabled
- Wi-Fi network that can reach the NAS

---

## Build Instructions

### First-time setup

1. Clone or copy this repository.
2. Open the project in Android Studio. It will download Gradle and sync dependencies automatically on first open.

### Build

In Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**

From the command line: `./gradlew assembleDebug` (`gradlew.bat` on Windows). The wrapper pins Gradle
8.13, the version required by AGP 8.12. If `JAVA_HOME` points at an old or JRE-only install, set it
to a JDK 17+ first — on Windows, Android Studio's is at `C:\Program Files\Android\Android Studio\jbr`.

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Install (USB debugging)

In Android Studio: **Run → Run 'app'**

For sideloading without USB debugging, see [SIDELOAD.md](SIDELOAD.md).

### Run unit tests

In Android Studio: **Run → Run All Tests**, or right-click the `test` source set and select **Run Tests**.

From the command line: `./gradlew :app:testDebugUnitTest`, or a single class with
`--tests "com.phonas.backup.backup.FileVerifierTest"`. Results land in
`app/build/reports/tests/testDebugUnitTest/index.html`.

Tests that construct an `android.net.Uri` must run under Robolectric
(`@RunWith(RobolectricTestRunner::class)` plus `@Config(sdk = [34], application = android.app.Application::class)`).
Against the stubbed `android.jar` of a plain JVM test, statics like `Uri.EMPTY` are null and Kotlin's
non-null checks reject them.

---

## Architecture

```
UI (Compose)          3 screens: Status, Logs, Setup
    ↓                 ViewModels observe Flow from Room + WorkManager
BackupEngine          Orchestrates scan → dedupe → merge → transfer → verify (serialized by a Mutex)
    ↓
SmbClient             SMBJ-based SMB2/3 client with timeouts; atomic temp-then-rename upload; sets
                      original timestamps via FileBasicInformation. A fresh instance per run.
FileScanner           Traverses SAF document trees (manual folder mode)
MediaStoreScanner     Queries MediaStore across all volumes (fast path for indexed media)
AllFilesScanner       Filesystem walk (All Files Access) that finds .nomedia-hidden media MediaStore skips
MediaChangeObserver   Debounced trigger: enqueues a backup shortly after new media is indexed
FileVerifier          SHA-256 streaming hash for local and remote files
DuplicateDetector     DB-first check (stable relativePath+name identity), NAS fallback
    ↓
Room DB               Tracks backed-up files, session logs, and per-file session detail (capped by maxLogEntries)
DataStore             Non-sensitive settings (schedule, charging, date filter, log retention, scan mode)
EncryptedSharedPrefs  Credentials (host, share, username, password) — Keystore-backed
WorkManager           The single scheduler: one periodic job (self-repeating, boot-persistent) plus
                      on-demand "Back Up Now" work. No AlarmManager — two schedulers previously
                      caused every backup to run twice.
```

### Manual dependency injection

No Hilt. `AppContainer` holds singleton instances and is created in `BackupApplication`. ViewModels receive what they need via `ViewModelProvider.Factory`.

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| SMBJ | 0.13.0 | SMB2/3 protocol for NAS communication |
| BouncyCastle | 1.78.1 | Cryptography backend required by SMBJ |
| Room | 2.6.1 | Local database for backup state and logs |
| WorkManager | 2.10.0 | Background scheduling (sole scheduler) |
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
   - **Scan all device media** — turn this on to back up everything on the device (photos, videos, WhatsApp, Telegram, Screenshots, etc.) without selecting individual folders. The app requests media permission on first enable. For **complete** coverage — including media hidden from the gallery, such as WhatsApp group chats with "Media visibility" off — grant **All Files Access** when prompted (Android 11+). Without it, the scan falls back to MediaStore, which cannot see `.nomedia`-hidden folders.
   - **Manual folders** — tap **Add Folder** to select specific folders. An optional NAS prefix can be set per folder to organise files under a custom subdirectory on the NAS.
5. Choose a **backup interval** (15 min / 1h / 6h / 12h / 24h) and optional charging-only requirement. The 15-minute option is the minimum enforced by Android's job scheduler and is intended for testing. Pressing Save always resets the timer from that moment.
6. Optionally set **Keep backup logs** (how many backup sessions to retain in history).
7. Optionally set **Skip Files Older Than** to ignore files before a specific date.
8. Tap **Save**. A confirmation message appears briefly. WorkManager schedules the periodic job — the first backup runs within the chosen interval, including while the screen is locked. If the NAS is unreachable when a backup runs, WorkManager retries it on an exponential backoff and, in any case, tries again on the next interval.

For reliable background operation while the screen is off, grant the **battery-optimization exemption** offered on the Status screen. WorkManager runs regardless of lock state, but Android's Doze mode can defer jobs until maintenance windows; the exemption prevents that deferral.

Tap **Back Up Now** on the Status screen to trigger an immediate backup. While a backup is running, a **Stop Backup** button appears to cancel it immediately. The Status screen also shows when the next scheduled backup is due.

---

## Date Filter

The "Skip Files Older Than" setting lets you start the first backup from a specific date rather than copying your entire photo library. Set it to e.g. 1 May 2026, and only files added or modified on or after that date will be backed up — on every run, not just the first. The cutoff is compared against the later of a file's *date added* and *date modified*, so an app can't sneak a recently-received file past the filter by backdating its modification time.

The filter is permanent. Files before the cutoff are never backed up. To back up everything, tap **Clear** next to the date in Setup.

The date picker will not let you select a future date (which would back up nothing).

The date filter is not a "since last backup" marker. It is a fixed cutoff that applies on every run. If you remove the date filter after a partial backup, the next run will scan all photos on the device and copy any that have never been backed up — including photos older than your previous cutoff. To avoid this, either keep the filter in place or advance it to today's date after completing a full backup.

---

## Viewing Backup Details

The Logs screen lists all backup sessions. Use the filter chips at the top to narrow the list to Completed, Failed, or Cancelled sessions.

Tap any session to see a full per-file breakdown:

- **Copied** — file was transferred and verified successfully
- **Skipped** — file was already on the NAS, no transfer needed
- **Failed** — transfer or verification failed (error message shown)

The NAS path and file size are shown for each entry. Tap any row to open that file on the device in the default photo or video viewer. The tap target is only active for files backed up after updating to this version.

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

Each file is uploaded to a temporary `.part` name, verified, then atomically renamed into place — so a mid-transfer failure never leaves a truncated file under the real name or clobbers the previous good copy.

The full SHA-256 is computed for free during the upload read pass (no re-read), then:

- **≤2 GB**: the remote copy is read back and its SHA-256 compared against the local hash.
- **>2 GB**: remote file size is compared against the expected size (a full remote read-back would be prohibitively slow).

If verification fails — or the uploaded file can't be confirmed on the NAS — the `.part` file is deleted, the file is marked failed, and the transfer is retried on the next run.

The original file modification date is preserved on the NAS copy via SMB `FileBasicInformation`.

---

## Security

- Credentials are stored with AES-256-GCM via Android Keystore (hardware-backed on modern devices). If the encrypted store is ever found corrupt at launch, it is wiped and recreated (you re-enter credentials) rather than crashing.
- Passwords, hostnames, and usernames are never written to logs, the database, or export files — error messages are mapped to fixed categories and any stray raw text is scrubbed of credentials.
- No internet connections are made; all traffic stays on the local network over SMB.
- Android's mandatory full-disk encryption (API 29+) protects the Room database and DataStore at the OS level.
- **All Files Access** (`MANAGE_EXTERNAL_STORAGE`), when granted, lets the app read all shared storage so it can back up media the gallery hides. It is only used to read media for backup; nothing is uploaded anywhere except your NAS.

---

## SMBJ and BouncyCastle

Android ships an incomplete BouncyCastle provider. `BackupApplication.onCreate()` removes it and registers the full BC provider before any backup runs. This is required for SMB signing and encryption in SMBJ.

---

## Known Limitations

- **`Android/data` and `Android/obb` are unreachable** by any non-root method (they are excluded from All Files Access and blocked by SAF). A few apps keep received media in `Android/data`; that media cannot be backed up without root. Most messengers (WhatsApp, Telegram, Signal) use `Android/media`, which *is* covered.
- SAF-based folder scanning (`DocumentFile.listFiles()`) is slow for very large directories. For a library of 50,000+ files, the initial scan can take several minutes. Use "Scan all device media" mode to avoid this — MediaStore queries are fast, and the filesystem walk is scoped to media.
- The "Scan all device media" filesystem walk deliberately skips transient/derivative directories: `.thumbnails`, `.trashed`, and WhatsApp `.Statuses` (statuses the app deletes within 24h).
- The app does not retry individual failed files within a session. Failures are logged and retried on the next run.
- Folder URI permissions granted via SAF are device-specific. Importing a config on a new device restores all other settings but requires re-selecting folders manually.
- In "Scan all device media" mode, media permission (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` on Android 13+) or, for full coverage, **All Files Access** (`MANAGE_EXTERNAL_STORAGE`, Android 11+) must be granted. A partial "Select photos" grant (Android 14+) is treated as insufficient. `MANAGE_EXTERNAL_STORAGE` is restricted on Google Play — this app is intended for sideloading.
