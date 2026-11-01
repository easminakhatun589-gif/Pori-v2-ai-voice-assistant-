/**
 * set-admin.js
 * 
 * One-time Node.js script using Firebase Admin SDK to grant the "owner" and "admin"
 * custom claims to mdaslamlaskar14@gmail.com.
 *
 * HOW TO RUN:
 * 1. cd scripts
 * 2. npm init -y
 * 3. npm install firebase-admin
 * 4. Download your Service Account Private Key from Firebase Console:
 *    Project Settings -> Service accounts -> "Generate new private key"
 *    Save it in this directory as "serviceAccountKey.json"
 *    (Alternatively, set export GOOGLE_APPLICATION_CREDENTIALS="/path/to/key.json")
 * 5. Run: node set-admin.js
 */

const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

const TARGET_EMAIL = 'mdaslamlaskar14@gmail.com';
const SERVICE_ACCOUNT_FILE = path.join(__dirname, 'serviceAccountKey.json');

async function grantAdminRole() {
  console.log(`\n======================================================`);
  console.log(`  Angel AI / JARVIS — Grant Owner & Admin Role Script  `);
  console.log(`======================================================\n`);

  if (fs.existsSync(SERVICE_ACCOUNT_FILE)) {
    console.log(`[INFO] Found serviceAccountKey.json. Initializing Firebase Admin SDK...`);
    const serviceAccount = require(SERVICE_ACCOUNT_FILE);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    console.log(`[INFO] Using GOOGLE_APPLICATION_CREDENTIALS: ${process.env.GOOGLE_APPLICATION_CREDENTIALS}`);
    admin.initializeApp({
      credential: admin.credential.applicationDefault()
    });
  } else {
    console.error(`[ERROR] Missing service account key!`);
    console.error(`Please download your service account JSON file from Firebase Console:`);
    console.error(`Project Settings -> Service accounts -> 'Generate new private key'`);
    console.error(`and save it as: ${SERVICE_ACCOUNT_FILE}`);
    process.exit(1);
  }

  const auth = admin.auth();
  const db = admin.firestore();

  try {
    console.log(`[1/3] Searching for user with email: ${TARGET_EMAIL}...`);
    const userRecord = await auth.getUserByEmail(TARGET_EMAIL);
    console.log(`[SUCCESS] User found:`);
    console.log(`   UID: ${userRecord.uid}`);
    console.log(`   Email: ${userRecord.email}`);
    console.log(`   Email Verified: ${userRecord.emailVerified}`);

    console.log(`\n[2/3] Setting Firebase Custom User Claims: { admin: true, role: 'owner' }...`);
    await auth.setCustomUserClaims(userRecord.uid, {
      admin: true,
      role: 'owner'
    });
    console.log(`[SUCCESS] Custom claims set successfully!`);

    console.log(`\n[3/3] Synchronizing Firestore users/${userRecord.uid} document...`);
    const userDocRef = db.collection('users').doc(userRecord.uid);
    await userDocRef.set({
      userId: userRecord.uid,
      email: TARGET_EMAIL,
      displayName: userRecord.displayName || 'MD Aslam',
      photoUrl: userRecord.photoURL || null,
      role: 'owner',
      status: 'active',
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
    console.log(`[SUCCESS] Firestore document updated to role='owner', status='active'!`);

    console.log(`\n======================================================`);
    console.log(`  VERIFICATION: Fetching updated claims...             `);
    const updatedUser = await auth.getUser(userRecord.uid);
    console.log(`  Current Claims:`, updatedUser.customClaims);
    console.log(`======================================================`);
    console.log(`\nIMPORTANT: On your Android device, sign out and sign back in`);
    console.log(`(or call user.getIdToken(true)) so the client receives the new token claim!`);
    console.log(`The app will now automatically open the Admin Dashboard for this account.\n`);

  } catch (error) {
    if (error.code === 'auth/user-not-found') {
      console.error(`\n[ERROR] No account exists yet with email: ${TARGET_EMAIL}.`);
      console.error(`Please sign in with Google once on the Android app first so the Auth record is created, then run this script.`);
    } else {
      console.error(`\n[ERROR] Failed to grant admin claims:`, error);
    }
    process.exit(1);
  }
}

grantAdminRole();
