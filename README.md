# Call Analytics Android APK

Native Kotlin + Jetpack Compose starter. Reads only the local Android call log after permission is granted. No network permission or server integration is included.

## Build from an Android phone using Termux + GitHub Actions

1. Install Termux from the official project releases: https://github.com/termux/termux-app/releases
2. Run:

```bash
pkg update && pkg upgrade -y
pkg install git nano -y
termux-setup-storage
```

3. Create an empty GitHub repository named `CallAnalytics`.
4. Download and extract this ZIP on your phone, then copy the `CallAnalyticsAndroid` folder into Termux storage, or clone your repo and copy these project files into it.
5. From the project folder:

```bash
git init
git add .
git commit -m "Initial Call Analytics Android app"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/CallAnalytics.git
git push -u origin main
```

6. On GitHub open **Actions** → **Build Call Analytics APK**. Wait for the workflow to finish.
7. Open the completed workflow run → **Artifacts** → download `CallAnalytics-debug-apk`.
8. Extract the artifact ZIP on your phone. Install `app-debug.apk`.

## Build locally (optional)

A complete Android SDK + compatible JDK + Gradle installation is large and can be difficult on Android/Termux. The included GitHub Actions workflow is the recommended phone-only build route. If you have a configured Android SDK locally, run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Permission notes

`READ_CALL_LOG` is a dangerous and hard-restricted Android permission. Android may require the installer to allowlist it; a normal sideload may not be able to grant it. Check Android Settings → Apps → Call Analytics → Permissions / restricted settings if the device offers that option. If Android blocks the permission, do not try to bypass the restriction. Use an authorized distribution/installer route or keep the app as a demo until the permission can be granted.

Google Play restricts call-log permissions to eligible core use cases and approval. This starter is for personal testing on a device you own or are authorized to administer; Play publication is not guaranteed.

## Current MVP limitations

- Uses the call log's cached contact name; it does not request contacts permission.
- Date filters: Today (last rolling 24 hours), 7 days, 30 days, All.
- CSV is Excel-compatible; no `.xlsx` writer yet.
- Up to 300 calls displayed in the recent-call list; the report exports all fetched rows in the selected filter.
- "Missed" dashboard count includes rejected calls; individual rows distinguish them.
- Device/OEM call-log behavior varies. Some records may be unavailable or categorized differently.
