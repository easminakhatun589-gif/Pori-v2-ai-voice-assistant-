/**
 * publish-release.js
 *
 * Automated Release Publisher for Jarvic AI Voice Assistant.
 * Used by GitHub Actions CI/CD to upload the built APK to Firebase Storage
 * and publish the verified release manifest into Firestore (`app_update/latest`)
 * and `app_update_history`.
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// Command line argument parser
function parseArgs() {
  const args = {};
  for (const arg of process.argv.slice(2)) {
    if (arg.startsWith('--')) {
      const eqIdx = arg.indexOf('=');
      if (eqIdx !== -1) {
        const key = arg.substring(2, eqIdx).trim();
        const val = arg.substring(eqIdx + 1).trim();
        args[key] = val;
      } else {
        args[arg.substring(2)] = true;
      }
    }
  }
  return args;
}

function computeFileSha256(filePath) {
  const fileBuffer = fs.readFileSync(filePath);
  return crypto.createHash('sha256').update(fileBuffer).digest('hex').toLowerCase();
}

function extractBuildGradleMetadata() {
  const candidatePaths = [
    path.join(__dirname, '..', 'Part 7', 'app', 'build.gradle.kts'),
    path.join(__dirname, '..', 'app', 'build.gradle.kts')
  ];

  for (const p of candidatePaths) {
    if (fs.existsSync(p)) {
      const content = fs.readFileSync(p, 'utf8');
      const vCodeMatch = content.match(/versionCode\s*=\s*(\d+)/);
      const vNameMatch = content.match(/versionName\s*=\s*"([^"]+)"/);
      const pkgMatch = content.match(/applicationId\s*=\s*"([^"]+)"/) || content.match(/namespace\s*=\s*"([^"]+)"/);

      return {
        versionCode: vCodeMatch ? parseInt(vCodeMatch[1], 10) : 143,
        versionName: vNameMatch ? vNameMatch[1] : '1.8.7',
        packageName: pkgMatch ? pkgMatch[1] : 'com.jarvis.assistant'
      };
    }
  }

  return { versionCode: 143, versionName: '1.8.7', packageName: 'com.jarvis.assistant' };
}

async function main() {
  const args = parseArgs();
  console.log('===========================================================');
  console.log('  Jarvic AI Voice Assistant — Automated Release Publisher  ');
  console.log('===========================================================');

  const apkPath = args.apk;
  if (!apkPath || !fs.existsSync(apkPath)) {
    console.error(`[ERROR] APK file not found at: ${apkPath}`);
    process.exit(1);
  }

  const gradleMeta = extractBuildGradleMetadata();
  const versionCode = args.versionCode ? parseInt(args.versionCode, 10) : gradleMeta.versionCode;
  const versionName = args.versionName || gradleMeta.versionName;
  const packageName = args.packageName || gradleMeta.packageName;

  const fileSize = fs.statSync(apkPath).size;
  const sha256 = args.sha256 || computeFileSha256(apkPath);

  const notesEn = args.notesEn || `Automated release v${versionName} (build ${versionCode}) with latest AI Studio updates.`;
  const notesBn = args.notesBn || `স্বয়ংক্রিয় রিলিজ সংস্করণ v${versionName} (বিল্ড ${versionCode})।`;

  console.log(`[INFO] Package Name: ${packageName}`);
  console.log(`[INFO] Version Code: ${versionCode}`);
  console.log(`[INFO] Version Name: ${versionName}`);
  console.log(`[INFO] APK Size:     ${fileSize} bytes`);
  console.log(`[INFO] APK SHA-256:  ${sha256}`);

  // Validation
  if (versionCode <= 0) {
    console.error('[ERROR] Version code must be positive integer (> 0)');
    process.exit(1);
  }
  if (!/^[a-fA-F0-9]{64}$/.test(sha256)) {
    console.error('[ERROR] Invalid SHA-256 format. Must be 64-char hex string.');
    process.exit(1);
  }

  const isEmulator = !!process.env.FIRESTORE_EMULATOR_HOST;
  const isDryRun = args.dryRun === true || args.dryRun === 'true';

  let admin;
  try {
    admin = require('firebase-admin');
  } catch (_e) {
    console.log('[WARN] firebase-admin package not found, using REST / simulation mode.');
  }

  const projectId = process.env.FIREBASE_PROJECT_ID || 'gen-lang-client-0209058951';
  const bucketName = process.env.FIREBASE_STORAGE_BUCKET || `${projectId}.firebasestorage.app`;

  let downloadUrl = `https://firebasestorage.googleapis.com/v0/b/${bucketName}/o/releases%2Fjarvis-v${versionName}-${versionCode}.apk?alt=media`;

  if (!isEmulator && !isDryRun && admin) {
    // 1. Initialize Firebase Admin
    let initialized = false;
    if (process.env.FIREBASE_SERVICE_ACCOUNT) {
      try {
        const cred = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
        admin.initializeApp({
          credential: admin.credential.cert(cred),
          storageBucket: bucketName,
          projectId: projectId
        });
        initialized = true;
      } catch (err) {
        console.log('[WARN] Could not parse FIREBASE_SERVICE_ACCOUNT as JSON, trying file path...');
        if (fs.existsSync(process.env.FIREBASE_SERVICE_ACCOUNT)) {
          admin.initializeApp({
            credential: admin.credential.cert(process.env.FIREBASE_SERVICE_ACCOUNT),
            storageBucket: bucketName,
            projectId: projectId
          });
          initialized = true;
        }
      }
    } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS && fs.existsSync(process.env.GOOGLE_APPLICATION_CREDENTIALS)) {
      admin.initializeApp({
        credential: admin.credential.applicationDefault(),
        storageBucket: bucketName,
        projectId: projectId
      });
      initialized = true;
    }

    if (!initialized) {
      console.log('[WARN] No service account credentials detected in environment.');
      console.log('       To enable live Firebase publishing from GitHub Actions, configure the');
      console.log('       `FIREBASE_SERVICE_ACCOUNT` secret in your GitHub repository.');
    } else {
      // 2. Upload APK to Firebase Cloud Storage
      console.log(`\n[1/3] Uploading APK to Firebase Storage: releases/jarvis-v${versionName}-${versionCode}.apk...`);
      const bucket = admin.storage().bucket();
      const destination = `releases/jarvis-v${versionName}-${versionCode}.apk`;
      const token = crypto.randomUUID();

      await bucket.upload(apkPath, {
        destination: destination,
        metadata: {
          contentType: 'application/vnd.android.package-archive',
          metadata: {
            firebaseStorageDownloadTokens: token,
            versionCode: versionCode.toString(),
            versionName: versionName,
            sha256: sha256
          }
        }
      });

      downloadUrl = `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodeURIComponent(destination)}?alt=media&token=${token}`;
      console.log(`[SUCCESS] APK uploaded successfully!`);
      console.log(`[INFO] Download URL: ${downloadUrl}`);

      // 3. Write Release Manifest to Firestore
      console.log(`\n[2/3] Publishing release manifest to Firestore app_update/latest...`);
      const db = admin.firestore();
      const latestRef = db.collection('app_update').doc('latest');
      const latestDoc = await latestRef.get();

      let previousVersionCode = 0;
      if (latestDoc.exists) {
        const oldData = latestDoc.data();
        previousVersionCode = oldData.versionCode || 0;
        // Archive in history
        await db.collection('app_update_history').doc(`v${previousVersionCode}_${Date.now()}`).set(oldData);
        console.log(`[INFO] Archived previous release v${previousVersionCode} to app_update_history.`);
      }

      const releaseManifest = {
        versionCode: versionCode,
        versionName: versionName,
        packageName: packageName,
        apkUrl: downloadUrl,
        sha256: sha256,
        signingCertSha256: "",
        apkSizeBytes: fileSize,
        minSupportedVersionCode: 1,
        forceUpdate: false,
        channel: 'stable',
        rolloutPercent: 100,
        releaseNotes: notesEn,
        releaseNotesEn: notesEn,
        releaseNotesBn: notesBn,
        newlyAddedFeatures: [
          'Automatic CI/CD APK release build',
          'Automated Firebase Storage distribution',
          'In-app background update checker'
        ],
        publicDownloadPageUrl: downloadUrl,
        storagePath: destination,
        publishedAtMs: Date.now(),
        publishedByUid: 'github-actions-ci',
        publishedByEmail: 'github-actions-ci@jarvis.internal',
        previousVersionCode: previousVersionCode
      };

      await latestRef.set(releaseManifest);
      console.log(`[SUCCESS] Firestore app_update/latest published with versionCode=${versionCode}!`);

      // 4. Update Remote Config latest_release parameter
      console.log(`\n[3/3] Updating Firebase Remote Config latest_release manifest...`);
      try {
        const remoteConfig = admin.remoteConfig();
        const template = await remoteConfig.getTemplate();
        template.parameters['latest_release'] = {
          defaultValue: {
            value: JSON.stringify(releaseManifest)
          },
          description: `Latest Jarvic APK release (v${versionName} - ${versionCode})`
        };
        template.parameters['jarvis_latest_version_code'] = {
          defaultValue: { value: versionCode.toString() }
        };
        template.parameters['jarvis_latest_apk_url'] = {
          defaultValue: { value: downloadUrl }
        };
        template.parameters['jarvis_latest_apk_sha256'] = {
          defaultValue: { value: sha256 }
        };
        await remoteConfig.publishTemplate(template);
        console.log(`[SUCCESS] Remote Config template published!`);
      } catch (rcErr) {
        console.log(`[INFO] Remote Config update note: ${rcErr.message} (Firestore is active).`);
      }
    }
  }

  // Always write release_manifest.json on disk for CI artifact verification
  const localManifest = {
    versionCode: versionCode,
    versionName: versionName,
    packageName: packageName,
    apkUrl: downloadUrl,
    sha256: sha256,
    apkSizeBytes: fileSize,
    minSupportedVersionCode: 1,
    forceUpdate: false,
    channel: 'stable',
    rolloutPercent: 100,
    releaseNotesEn: notesEn,
    releaseNotesBn: notesBn,
    publishedAtMs: Date.now(),
    publishedByEmail: 'github-actions-ci@jarvis.internal'
  };

  const manifestPath = path.join(__dirname, 'release_manifest.json');
  fs.writeFileSync(manifestPath, JSON.stringify(localManifest, null, 2));
  console.log(`\n[OUTPUT] Release manifest saved to: ${manifestPath}`);
  console.log('===========================================================');
  console.log('  Release Manifest Summary:');
  console.log(JSON.stringify(localManifest, null, 2));
  console.log('===========================================================');
}

main().catch(err => {
  console.error('[FATAL]', err);
  process.exit(1);
});
