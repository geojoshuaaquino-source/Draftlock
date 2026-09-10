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

## Google OAuth setup

Create a Google OAuth client for Android use with the debug/release certificate fingerprints required by the Google project. Put the web client ID in a local `local.properties` entry:

```properties
GOOGLE_CLIENT_ID=YOUR_CLIENT_ID.apps.googleusercontent.com
```

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
