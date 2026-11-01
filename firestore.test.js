const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read user profile or memories", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
});

test("Authenticated user: can create and read own profile with valid schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      userName: "MD Aslam",
      personality: "Pari",
      language: "Bangla",
      voiceTone: "Romantic",
      role: "user",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: cannot read or write another user's profile", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      userName: "Bob",
      personality: "Jarvis",
      language: "English",
      role: "user",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      userName: "Hacked",
      personality: "Jarvis",
      language: "English",
      role: "user",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user: role self-escalation and shadow admin field injection are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      userName: "MD Aslam",
      personality: "Pari",
      language: "Bangla",
      role: "admin",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      userName: "MD Aslam",
      personality: "Pari",
      language: "Bangla",
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Normal user: cannot write config/app, app_update/latest, or announcements", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("config").doc("app").set({ maintenanceMode: true })
  );
  await assertFails(
    aliceDb.collection("app_update").doc("latest").set({ versionCode: 999 })
  );
});

test("Admin with custom claim (admin: true): can write config/app and read users, but CANNOT read user private memories", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      userName: "Alice",
      role: "user",
      status: "active",
      createdAt: now,
      updatedAt: now,
    });
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_secret")
      .set({
        userId: ALICE_UID,
        category: "private",
        content: "Private voice memory",
        importance: 5,
        createdAt: now,
        updatedAt: now,
      });
  });

  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { admin: true, role: "admin" })
    .firestore();

  await assertSucceeds(adminDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    adminDb.collection("config").doc("app").set({ maintenanceMode: false })
  );
  // Strict privacy rule: even Admin CANNOT read a user's private memories
  await assertFails(
    adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_secret")
      .get()
  );
});

test("Blocked user: denied access to protected data and memories", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      userName: "Blocked Bob",
      role: "user",
      status: "blocked",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("config").doc("app").get());
  await assertFails(
    bobDb
      .collection("users")
      .doc(BOB_UID)
      .collection("memories")
      .doc("mem_1")
      .set({
        userId: BOB_UID,
        category: "general",
        content: "Should fail",
        importance: 5,
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Authenticated user: can create, list, and update own memories, and rejects cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .set({
        userId: ALICE_UID,
        category: "relationship",
        content: "Aslam loves listening to romantic Bangla songs.",
        importance: 5,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Owner/Admin: can publish valid HTTPS + SHA-256 release to app_update/latest and history, while invalid HTTP or bad SHA-256 is rejected", async () => {
  const ownerDb = testEnv
    .authenticatedContext("owner_aslam", {
      email: "mdaslamlaskar14@gmail.com",
      email_verified: true,
    })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  const validHash = "a".repeat(64);
  // Owner can publish valid release manifest
  await assertSucceeds(
    ownerDb.collection("app_update").doc("latest").set({
      versionCode: 144,
      versionName: "5.5.0",
      minSupportedVersionCode: 140,
      forceUpdate: false,
      apkUrl: "https://firebasestorage.googleapis.com/v0/b/jarvis/o/releases%2Fjarvis-v5.5.0-144.apk?alt=media",
      sha256: validHash,
      apkSizeBytes: 24500000,
      packageName: "com.jarvis.assistant",
      releaseNotes: "Automatic update system + remote config",
      publishedByEmail: "mdaslamlaskar14@gmail.com",
    })
  );

  // Unauthenticated user can read app_update/latest (public APK update check)
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("app_update").doc("latest").get());

  // Unauthenticated user CANNOT write or tamper with app_update/latest
  await assertFails(
    unauthDb.collection("app_update").doc("latest").set({
      versionCode: 145,
      versionName: "5.6.0",
      apkUrl: "https://evil.example.com/jarvis.apk",
      sha256: validHash,
    })
  );

  // Normal active user can read app_update/latest
  await assertSucceeds(aliceDb.collection("app_update").doc("latest").get());

  // Normal user CANNOT overwrite or roll back app_update/latest or app_update_history
  await assertFails(
    aliceDb.collection("app_update").doc("latest").set({
      versionCode: 145,
      versionName: "5.6.0",
      apkUrl: "https://evil.example.com/jarvis.apk",
      sha256: validHash,
    })
  );
  await assertFails(
    aliceDb.collection("app_update_history").doc("v144_test").set({
      versionCode: 144,
    })
  );

  // Even owner CANNOT publish an insecure http:// URL or invalid SHA-256 hash
  await assertFails(
    ownerDb.collection("app_update").doc("latest").set({
      versionCode: 145,
      versionName: "5.6.0",
      apkUrl: "http://insecure.example.com/jarvis.apk",
      sha256: validHash,
    })
  );
  await assertFails(
    ownerDb.collection("app_update").doc("latest").set({
      versionCode: 145,
      versionName: "5.6.0",
      apkUrl: "https://firebasestorage.googleapis.com/v0/b/jarvis/o/jarvis.apk",
      sha256: "short_invalid_hash",
    })
  );
});

test("Announcements & User Messages: public can read active announcements, admin can publish, direct user messages restricted to recipient & admin", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ownerDb = testEnv.authenticatedContext("owner_uid", { email: "mdaslamlaskar14@gmail.com", email_verified: true, admin: true }).firestore();

  // 1. Unauthenticated or normal user can read announcements
  await assertSucceeds(unauthDb.collection("announcements").doc("latest_popup").get());
  await assertSucceeds(aliceDb.collection("announcements").doc("latest_popup").get());

  // 2. Normal user CANNOT write announcements
  await assertFails(aliceDb.collection("announcements").doc("latest_popup").set({
    title: "Hack",
    type: "photo_pop"
  }));

  // 3. Admin can write announcements
  await assertSucceeds(ownerDb.collection("announcements").doc("latest_popup").set({
    title: "Special Photo Pop",
    type: "photo_pop",
    photoUrl: "https://example.com/photo.png",
    isActive: true
  }));

  // 4. Admin can send direct user message to Alice
  await assertSucceeds(ownerDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").set({
    title: "Direct Alert",
    body: "Hello Alice",
    type: "push_notification"
  }));

  // 5. Alice can read her own direct message
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").get());

  // 6. Bob CANNOT read Alice's direct message
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").get());

  // 7. Alice CANNOT send messages into her own messages inbox
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_2").set({
    title: "Fake Message"
  }));
});


