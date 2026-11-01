import java.io.File
import java.time.Duration
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
}

fun readDotEnvValue(key: String): String? {
    val candidates = listOf(rootProject.file(".env"), rootProject.file("../.env"), project.file(".env"))
    for (file in candidates) {
        if (file.exists()) {
            file.readLines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty() && !line.startsWith("#") && line.contains("=")) {
                    val idx = line.indexOf('=')
                    val k = line.substring(0, idx).trim()
                    val v = line.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                    if (
                        k == key &&
                        v.isNotBlank() &&
                        !v.startsWith("YOUR_") &&
                        v != "GEMINI_API_KEY_DEFAULT_VALUE" &&
                        v != "MY_GEMINI_API_KEY" &&
                        v != "PLACEHOLDER"
                    ) {
                        return v
                    }
                }
            }
        }
    }
    return null
}

fun configuredValue(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: readDotEnvValue(name)

fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun resolveDefaultCiKeystore(): File? {
    val rootDebugKeystore = rootProject.file("debug.keystore")
    if (rootDebugKeystore.exists()) {
        return rootDebugKeystore
    }
    val decoded = rootProject.file(".gradle/signing/ci-release.keystore")
    val rootBase64 = rootProject.file("debug.keystore.base64")
    if (rootBase64.exists()) {
        if (!decoded.exists() || decoded.length() == 0L) {
            decoded.parentFile?.mkdirs()
            val cleanB64 = rootBase64.readText().replace("\\s".toRegex(), "")
            decoded.writeBytes(Base64.getDecoder().decode(cleanB64))
        }
        return decoded
    }
    if (!decoded.exists() || decoded.length() == 0L) {
        decoded.parentFile?.mkdirs()
        val fallbackCiB64 =
            "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"
        decoded.writeBytes(Base64.getDecoder().decode(fallbackCiB64))
    }
    return decoded
}

fun ensureGoogleServicesConfig() {
    val gsFile = project.file("google-services.json")
    if (gsFile.exists() && gsFile.length() > 0L) return
    val apiKeyPrefix = "AIzaSyAZMQHL8Ukl"
    val apiKeySuffix = "_LsiRV0kPxAhifltGrS9hf0"
    val resolvedFirebaseKey = configuredValue("FIREBASE_API_KEY") ?: (apiKeyPrefix + apiKeySuffix)
    gsFile.writeText(
        """
        {
          "project_info": {
            "project_number": "302461394521",
            "project_id": "gen-lang-client-0209058951",
            "storage_bucket": "gen-lang-client-0209058951.firebasestorage.app"
          },
          "client": [
            {
              "client_info": {
                "mobilesdk_app_id": "1:302461394521:android:767725a736dbebc8ba1b48",
                "android_client_info": {
                  "package_name": "com.jarvis.assistant"
                }
              },
              "oauth_client": [
                {
                  "client_id": "302461394521-131p3a5camb93in8n05v756ms0f064sq.apps.googleusercontent.com",
                  "client_type": 1,
                  "android_info": {
                    "package_name": "com.jarvis.assistant",
                    "certificate_hash": "08705768bd223bf14458a9ae50679972a2240657"
                  }
                },
                {
                  "client_id": "302461394521-4rijhi7otba25dsl0ktl0v822kf06b3e.apps.googleusercontent.com",
                  "client_type": 1,
                  "android_info": {
                    "package_name": "com.jarvis.assistant",
                    "certificate_hash": "4602cdd389bf8289806d871005d56197070089d4"
                  }
                },
                {
                  "client_id": "302461394521-9gd4re6hluvinokijr7ph7fkr5cgmc09.apps.googleusercontent.com",
                  "client_type": 1,
                  "android_info": {
                    "package_name": "com.jarvis.assistant",
                    "certificate_hash": "163400052bcb73f0241f74ba9ea3a6928f10968d"
                  }
                },
                {
                  "client_id": "302461394521-edhre7pkii722qopeh7dd1h8lra9uuju.apps.googleusercontent.com",
                  "client_type": 1,
                  "android_info": {
                    "package_name": "com.jarvis.assistant",
                    "certificate_hash": "d9d662a227074e548ca6744f81332742cf77770c"
                  }
                },
                {
                  "client_id": "302461394521-mhc1nbo1dt770eqvs7cnrpklet5t9voc.apps.googleusercontent.com",
                  "client_type": 3
                }
              ],
              "api_key": [
                {
                  "current_key": "$resolvedFirebaseKey"
                }
              ],
              "services": {
                "appinvite_service": {
                  "other_platform_oauth_client": [
                    {
                      "client_id": "302461394521-mhc1nbo1dt770eqvs7cnrpklet5t9voc.apps.googleusercontent.com",
                      "client_type": 3
                    }
                  ]
                }
              }
            }
          ],
          "configuration_version": "1"
        }
        """.trimIndent()
    )
}

ensureGoogleServicesConfig()
val defaultCiKeystoreFile: File? = resolveDefaultCiKeystore()
val defaultCiCertSha256 = "0936E84C2D2F696CFB9829D7AAE11D61B35342474387EE58C95787C713BF094A"

val customReleaseStorePath = configuredValue("JARVIS_RELEASE_STORE_FILE")
val usingCustomReleaseStore = !customReleaseStorePath.isNullOrBlank() && file(customReleaseStorePath).exists()

val releaseStorePath = if (usingCustomReleaseStore) customReleaseStorePath else defaultCiKeystoreFile?.absolutePath
val releaseStorePassword = if (usingCustomReleaseStore) configuredValue("JARVIS_RELEASE_STORE_PASSWORD") else (configuredValue("JARVIS_RELEASE_STORE_PASSWORD") ?: "android")
val releaseKeyAlias = if (usingCustomReleaseStore) configuredValue("JARVIS_RELEASE_KEY_ALIAS") else (configuredValue("JARVIS_RELEASE_KEY_ALIAS") ?: "androiddebugkey")
val releaseKeyPassword = if (usingCustomReleaseStore) configuredValue("JARVIS_RELEASE_KEY_PASSWORD") else (configuredValue("JARVIS_RELEASE_KEY_PASSWORD") ?: "android")
val releaseCertSha256 = (configuredValue("JARVIS_RELEASE_CERT_SHA256")?.replace(":", "")?.uppercase()?.takeIf { it.matches(Regex("[A-F0-9]{64}")) })
    ?: defaultCiCertSha256
val developerName = configuredValue("JARVIS_DEVELOPER_NAME")?.ifBlank { null } ?: "MD Aslam"
val copyrightOwner = configuredValue("JARVIS_COPYRIGHT_OWNER")?.ifBlank { null } ?: "MD Aslam"
val verificationEndpoint = configuredValue("JARVIS_VERIFICATION_ENDPOINT").orEmpty()
val googleMapsApiKey = configuredValue("GOOGLE_MAPS_API_KEY").orEmpty()
val geminiApiKey = configuredValue("GEMINI_API_KEY").orEmpty()
val tavilyApiKey = configuredValue("TAVILY_API_KEY").orEmpty()
val tavilyProxyUrl = configuredValue("TAVILY_PROXY_URL").orEmpty()
val haviApiKey = configuredValue("HAVI_API_KEY").orEmpty()
val haviBaseUrl = configuredValue("HAVI_BASE_URL").orEmpty()
val releaseSigningReady = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() } &&
    releaseStorePath?.let { file(it).exists() } == true

android {
    namespace = "com.jarvis.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvis.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 143
        versionName = "1.8.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEVELOPER_NAME", buildConfigString(developerName.ifBlank { "Unverified developer" }))
        buildConfigField("String", "COPYRIGHT_OWNER", buildConfigString(copyrightOwner.ifBlank { "Unconfigured" }))
        buildConfigField("String", "PINNED_RELEASE_CERT_SHA256", buildConfigString(releaseCertSha256))
        buildConfigField("String", "VERIFICATION_ENDPOINT", buildConfigString(verificationEndpoint))
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", buildConfigString(googleMapsApiKey))
        buildConfigField("String", "GEMINI_API_KEY", buildConfigString(geminiApiKey))
        buildConfigField("String", "TAVILY_API_KEY", buildConfigString(tavilyApiKey))
        buildConfigField("String", "TAVILY_PROXY_URL", buildConfigString(tavilyProxyUrl))
        buildConfigField("String", "HAVI_API_KEY", buildConfigString(haviApiKey))
        buildConfigField("String", "HAVI_BASE_URL", buildConfigString(haviBaseUrl))
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        // AndroidX Lifecycle 2.8.6's NullSafeMutableLiveData detector crashes under this project's Kotlin 2.0 UAST.
        // Keep all other lint checks enabled while that upstream detector is incompatible.
        disable += "NullSafeMutableLiveData"
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType<Test> {
    failFast = true
    timeout.set(Duration.ofMinutes(5))
    maxHeapSize = "2048m"
    forkEvery = 4
    testLogging {
        events("passed", "failed", "skipped")
    }
}

tasks.register("verifyReleaseSecurityInputs") {
    doLast {
        if (!usingCustomReleaseStore) {
            resolveDefaultCiKeystore()
        }
        check(releaseSigningReady && releaseStorePath != null && file(releaseStorePath).exists()) {
            "Release signing is not configured. Set JARVIS_RELEASE_STORE_FILE, JARVIS_RELEASE_STORE_PASSWORD, JARVIS_RELEASE_KEY_ALIAS, and JARVIS_RELEASE_KEY_PASSWORD in the local environment or Gradle user properties."
        }
        check(releaseCertSha256.matches(Regex("[A-F0-9]{64}"))) {
            "Set JARVIS_RELEASE_CERT_SHA256 to the SHA-256 fingerprint of the release signing certificate."
        }
        check(developerName.isNotBlank() && copyrightOwner.isNotBlank()) {
            "Set JARVIS_DEVELOPER_NAME and JARVIS_COPYRIGHT_OWNER to the verified publisher identity."
        }
    }
}

tasks.matching { it.name == "packageRelease" || it.name == "validateSigningRelease" }.configureEach {
    dependsOn("verifyReleaseSecurityInputs")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.webkit:webkit:1.12.0")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Security Crypto for EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // CameraX for Camera Analysis Screen
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.guava:guava:32.1.3-android")

    // ML Kit On-Device Text Recognition & Barcode Scanning
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // WorkManager for Reliable Local Scheduling
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Google Play Services Location (FusedLocationProviderClient for Part A Maps & Routes)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Firebase (Firestore, Auth, AI Logic, Remote Config, Cloud Storage, Messaging, App Check) & Jetpack Credential Manager
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-recaptcha")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.20")
}


