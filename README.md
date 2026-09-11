# DraftLock

DraftLock is an offline-first Android writing accountability tracker with configurable per-app requirements and optional app suspension.

## What is implemented

- In-app writing editor with live word count and local persistence.
- Daily writing quota and configurable writing-day reset.
- Independent Android UsageStats tracking for each selected requirement app.
- AND/OR unlock logic.
- Selected locked-app list.
- Real package suspension through `DevicePolicyManager` when DraftLock is provisioned as device owner or profile owner; ordinary apps do not receive this capability.
- Emergency 15-minute override.
- Google OAuth 2.0 / PKCE-compatible authorization architecture.
- Google Drive and Google Docs API repository for creating, finding and saving documents.
- Local Room + DataStore persistence.
- Phased GitHub Actions build with an APK artifact.

## Google OAuth setup (Android client — custom scheme allowed)

Web clients reject `com.googleusercontent.apps.*:/oauth2redirect` with **“Custom scheme URIs are not allowed for 'WEB' client type.”** Use **Android** type:

**Option A — I set it up (you just log in):**
```bash
gcloud auth login
./scripts/setup-oauth.sh          # creates project, enables Drive+Docs, creates Android client
# or: gh secret set GOOGLE_CLIENT_ID --body "xxx.apps.googleusercontent.com" --repo geojoshuaaquino-source/Draftlock
```

**Option B — manual Android client (fixes the WEB error):**
1. `console.cloud.google.com` → Select your `DraftLock-vault` project → Enable `Google Drive API` + `Google Docs API`
2. `OAuth consent screen` → External → App name `DraftLock` → add `openid email profile drive.file documents` → Test users → add your Gmail (if not done)
3. `Credentials → Create OAuth client → Android` → Name `DraftLock Android`
   - **Package name:** `com.draftlock.app`
   - **SHA-1:** run `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1` or `./gradlew signingReport` (under `Variant: debug` → `SHA1`). For release, add Play Console `SHA-1` too (create second Android client).
   - **No redirect URI field for Android** — custom scheme `com.googleusercontent.apps.<ANDROID_PREFIX>:/oauth2redirect` is auto-allowed.
4. Copy the new **Android** `Client ID: xxx.apps.googleusercontent.com` (different prefix from your Web `149732972265-i4i3...`) → replace baked ID: `echo "GOOGLE_CLIENT_ID=NEW_ANDROID_ID.apps.googleusercontent.com" > local.properties` and/or `gh secret set GOOGLE_CLIENT_ID` + rebuild. Or paste in-app `Settings → Advanced Client ID` → `Save & Connect` (no rebuild, `GoogleOAuthManager.kt:31` pref wins). Keep the Web `149732972265-...` for Drive verification if you want — but the app must use the **Android** ID.

Build bakes `BuildConfig.GOOGLE_CLIENT_ID` + `manifestPlaceholders appAuthRedirectScheme` (`app/build.gradle.kts:14` `env` > `local.properties` > placeholder, `GoogleOAuthManager.kt:20` runtime pref wins). AppAuth PKCE via `accounts.google.com` requests `openid email profile drive.file documents`.

The app requests the narrow Drive file scope plus Docs access rather than a user's Google password. Google may require OAuth verification for sensitive/restricted scopes before public distribution.

## Strong app blocking setup

`UsageStatsManager` requires the user to grant Usage Access in Android Settings. Strong package suspension is exposed by `DevicePolicyManager#setPackagesSuspended` to device/profile owners (subject to Android restrictions).

For a development device you control, provision DraftLock as device owner using the standard Android device-management provisioning flow. For an adb test device that meets Google's provisioning requirements, the command is commonly:

```bash
adb shell dpm set-device-owner com.draftlock.app/.admin.DraftLockDeviceAdminReceiver
```

Do not run that against a device containing accounts or data you need to preserve without first reviewing Android's device-owner provisioning requirements.

## UI reference

The Android UI is based on the latest Canva design: **DraftLock — Writing Accountability Android Prototype** (20-page mobile prototype, updated September 10, 2026).
