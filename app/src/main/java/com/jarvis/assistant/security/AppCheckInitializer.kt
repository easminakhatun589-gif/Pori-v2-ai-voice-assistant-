package com.jarvis.assistant.security

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.jarvis.assistant.BuildConfig
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import com.google.android.gms.tasks.Tasks

private const val TAG = "AppCheckInit"

// App Check contract keys defined by the Firebase SDK and runtime harness
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/** Standard HTTP header used to attach Firebase App Check attestation tokens to custom backend API calls. */
const val HEADER_FIREBASE_APP_CHECK = "X-Firebase-AppCheck"

/**
 * Configures Firebase App Check before any UI, ViewModel, or backend API calls execute.
 *
 * - **Debug builds (`BuildConfig.DEBUG == true`)**: Persists the `FIREBASE_APPCHECK_DEBUG_TOKEN`
 *   launch intent extra synchronously via `.commit()` and installs [DebugAppCheckProviderFactory].
 * - **Release builds (`BuildConfig.DEBUG == false`)**: Installs [PlayIntegrityAppCheckProviderFactory]
 *   as the hardware-backed attestation provider for Google Play Integrity enforcement (with
 *   automatic fallback to [RecaptchaAppCheckProviderFactory] if Play Integrity is unavailable).
 */
fun configureAppCheck(
    context: Context,
    intent: Intent?,
    preferPlayIntegrityInRelease: Boolean = true
) {
    try {
        // 1. Ensure FirebaseApp is initialized before accessing FirebaseAppCheck
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val firebaseAppCheck = Firebase.appCheck
        firebaseAppCheck.setTokenAutoRefreshEnabled(true)

        if (BuildConfig.DEBUG) {
            // 2. Debug: Read debug token from Intent extra
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // CRITICAL: MUST use commit() instead of apply() so the secret is written
                // synchronously before DebugAppCheckProviderFactory reads it.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            // 3. Release: Primary hardware-backed attestation via Play Integrity Provider
            if (preferPlayIntegrityInRelease) {
                initializePlayIntegrityAppCheck(context, isTokenAutoRefreshEnabled = true)
            } else {
                installRecaptchaFallbackOrPlayIntegrity(context, firebaseAppCheck)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

/**
 * Explicitly initializes Firebase App Check with the Google Play Integrity provider
 * ([PlayIntegrityAppCheckProviderFactory]) to harden backend and Firebase API calls.
 */
fun initializePlayIntegrityAppCheck(
    context: Context,
    isTokenAutoRefreshEnabled: Boolean = true
): AppCheckProviderFactory {
    if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(context)
    }
    val playIntegrityFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
    val firebaseAppCheck = Firebase.appCheck
    firebaseAppCheck.setTokenAutoRefreshEnabled(isTokenAutoRefreshEnabled)
    firebaseAppCheck.installAppCheckProviderFactory(playIntegrityFactory)
    Log.d(TAG, "PlayIntegrityAppCheckProviderFactory installed (autoRefresh=$isTokenAutoRefreshEnabled).")
    return playIntegrityFactory
}

private fun installRecaptchaFallbackOrPlayIntegrity(
    context: Context,
    firebaseAppCheck: FirebaseAppCheck
) {
    val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
    if (resId != 0) {
        val siteKey = context.getString(resId)
        if (siteKey.isNotBlank()) {
            firebaseAppCheck.installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey)
            )
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
            return
        }
    }
    initializePlayIntegrityAppCheck(context, isTokenAutoRefreshEnabled = true)
}

/**
 * Fetches a valid Firebase App Check token asynchronously for custom backend API calls.
 * Returns `null` if attestation fails so callers can handle or reject unverified traffic gracefully.
 */
suspend fun fetchAppCheckToken(forceRefresh: Boolean = false): String? {
    return try {
        val tokenResult = Firebase.appCheck.getAppCheckToken(forceRefresh).await()
        tokenResult.token.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.w(TAG, "Unable to retrieve Firebase App Check token: ${e.message}")
        null
    }
}

/**
 * OkHttp [Interceptor] that attaches the `X-Firebase-AppCheck` attestation token header
 * to outgoing backend API requests to protect custom endpoints from unauthorized clients.
 */
class AppCheckBackendInterceptor(
    private val forceRefresh: Boolean = false,
    private val timeoutMs: Long = 5_000L
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = try {
            val task = Firebase.appCheck.getAppCheckToken(forceRefresh)
            Tasks.await(task, timeoutMs, TimeUnit.MILLISECONDS).token
        } catch (e: Exception) {
            Log.w(TAG, "Proceeding without X-Firebase-AppCheck header: ${e.message}")
            null
        }

        val hardenedRequest = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header(HEADER_FIREBASE_APP_CHECK, token)
                .build()
        } else {
            originalRequest
        }
        return chain.proceed(hardenedRequest)
    }
}

