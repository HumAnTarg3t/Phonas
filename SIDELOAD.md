# Sideloading Phonas

This guide covers installing the app on an Android phone without using the Play Store or USB debugging. This is the method to use for company-managed phones where USB debugging is locked by IT policy.

---

## Step 1 — Build the APK

In Android Studio:

1. Wait for the Gradle sync to finish (bottom status bar must say "Gradle sync finished").
2. Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. Wait for the build to complete. A notification appears in the bottom-right corner: **"APK(s) generated successfully"**.
4. Click the **locate** link in that notification.

The file is at:
```
app\build\outputs\apk\debug\app-debug.apk
```

---

## Step 2 — Transfer the APK to the phone

Choose any of these methods:

**USB (recommended for large files)**
- Plug the phone into your PC via USB.
- Pull down the notification shade on the phone, tap the USB notification, and switch to **File Transfer (MTP)**.
- The phone appears as a drive in Windows Explorer.
- Copy `app-debug.apk` to the phone's `Downloads` folder.

**Email**
- Email the APK file to yourself and open the attachment on the phone.

**Google Drive / OneDrive**
- Upload the APK to cloud storage and download it on the phone via the cloud app.

---

## Step 3 — Allow installing unknown apps

On the phone, go to:

**Settings → Apps → Special app access → Install unknown apps**

Find the app you will use to open the APK (usually **Files**, **My Files**, or **Chrome**) and enable **Allow from this source**.

> **Company phone note:** If this toggle is greyed out, your device has an MDM policy blocking sideloading. You cannot proceed without IT approval. Contact your IT department and ask them to whitelist app installation for your device, or request they install the app through their MDM system instead.

---

## Step 4 — Install

Open the APK file on the phone:

- If you transferred via USB: open the **Files** or **My Files** app, navigate to `Downloads`, and tap `app-debug.apk`.
- If you opened it from email or cloud storage: tap **Download** then **Open**.

The Android installer appears. Tap **Install**. The app will appear in your launcher as **Phonas**.

---

## Step 5 — First launch

1. Open **Phonas**.
2. Go to the **Setup** tab.
3. Enter your NAS hostname, share name, username, and password.
4. Tap **Test Connection** to confirm reachability.
5. Tap **Add Folder** and select the phone folders to back up (e.g. DCIM/Camera).
6. Set your preferred backup interval and tap **Save**.

If you have a previously exported config file, tap **Import** in the Setup screen to restore all settings (you will need to re-enter your password and re-add folders).

---

## Updating the app

Repeat steps 1–4 with the new APK. Android will install the new version over the existing one, preserving all settings and the backup history database.

---

## Troubleshooting

**"App not installed" error**
The debug signing certificate may differ from a previous install. Uninstall the existing version first: long-press the app icon → **Uninstall**, then install the new APK. Note that uninstalling clears the backup database; your NAS files are unaffected.

**"Parse error"**
The APK file is corrupted or incomplete. Re-download or re-transfer it and try again.

**Install unknown apps toggle is missing**
Some Android skins (including One UI) move this setting. Try:
- Settings → Biometrics and security → Install unknown apps
- Settings → Security → More security settings → Install unknown apps

**The app crashes on launch**
Check Logcat in Android Studio (connect via USB in File Transfer mode, not debugging mode — Logcat still works). Filter by `com.phonas.backup` to see crash details. Share the output to diagnose the issue.
