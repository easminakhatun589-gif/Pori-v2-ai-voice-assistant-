# Jarvic AI Voice Assistant — Automated CI/CD Release Pipeline

This document describes the automated APK build, testing, and Firebase publishing pipeline configured for the **Jarvic AI Voice Assistant** (`com.jarvis.assistant`).

---

## 1. How It Works

Whenever code changes are synchronized to your GitHub repository from **Google AI Studio** (via the **Settings → Push to GitHub** action), GitHub Actions automatically triggers the workflow:

**Workflow File:** `.github/workflows/build_and_publish_release.yml`

```
┌─────────────────────────────────┐
│ Google AI Studio Approved Code  │
└────────────────┬────────────────┘
                 │  "Push to GitHub"
                 ▼
┌─────────────────────────────────┐
│ GitHub Repository (main/master) │
└────────────────┬────────────────┘
                 │  Triggers GitHub Actions
                 ▼
┌─────────────────────────────────┐
│ 1. Run Automated Unit Tests     │
│ 2. Assemble Signed Release APK  │
│ 3. Compute SHA-256 & Metadata   │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ Firebase Cloud Storage          │
│ (releases/jarvis-v1.8.8-144.apk)│
└────────────────┬────────────────┘
                 │
                 ▼
┌────────────────────────────────────────────────────────┐
│ Cloud Firestore (`app_update/latest` + History)        │
│ & Firebase Remote Config (`latest_release`)            │
└────────────────┬───────────────────────────────────────┘
                 │
                 ▼
┌────────────────────────────────────────────────────────┐
│ Installed Jarvic Android Devices (Direct In-App Update)│
└────────────────────────────────────────────────────────┘
```

---

## 2. GitHub Secrets Configuration

To enable the workflow to authenticate with your Firebase project and sign production APKs, add the following secrets in your GitHub repository:

**Path in GitHub:** `Settings` → `Secrets and variables` → `Actions` → `New repository secret`

| Secret Name | Required? | Description |
|---|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | **Required** for Cloud Publish | The full JSON content of your Firebase Service Account key (Project `gen-lang-client-0209058951`). |
| `RELEASE_KEYSTORE_BASE64` | Optional (Recommended) | Base64-encoded production release `.keystore` / `.jks` file. If omitted, CI signs with the bundled fallback debug keystore. |
| `RELEASE_KEYSTORE_PASSWORD` | Optional | Password for the release keystore. |
| `RELEASE_KEY_ALIAS` | Optional | Key alias for the release signing key. |
| `RELEASE_KEY_PASSWORD` | Optional | Password for the release key alias. |

### How to Get Your Firebase Service Account JSON:
1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Select your project: **`gen-lang-client-0209058951`** (or your linked Firebase project).
3. Click the **Gear icon (Project settings)** → **Service accounts** tab.
4. Click **Generate new private key**, then **Generate key**.
5. Copy the entire contents of the downloaded `.json` file.
6. Paste it into the `FIREBASE_SERVICE_ACCOUNT` secret in your GitHub repository.

---

## 3. Manual / CLI Release Publishing

You can also run the release publishing script locally or on a developer machine:

```bash
# Dry-run validation (without uploading):
node scripts/publish-release.js --apk="path/to/jarvis.apk" --dryRun=true

# Live publishing with service account:
export FIREBASE_SERVICE_ACCOUNT="/path/to/serviceAccountKey.json"
node scripts/publish-release.js --apk="path/to/jarvis.apk"
```

---

## 4. In-App Update Client Integration

The Android client (`com.jarvis.assistant`) checks both Firestore `app_update/latest` and Firebase Remote Config `latest_release`:
- It compares the published `versionCode` against `BuildConfig.VERSION_CODE`.
- It validates the HTTPS download URL and verifies the cryptographic SHA-256 hash.
- It triggers in-app installation using Android's native `FileProvider` package installer flow.
