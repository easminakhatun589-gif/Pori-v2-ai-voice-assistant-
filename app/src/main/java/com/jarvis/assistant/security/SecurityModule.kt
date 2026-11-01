package com.jarvis.assistant.security

import com.jarvis.assistant.BuildConfig

data class DeveloperInfo(
    val developerName: String = BuildConfig.DEVELOPER_NAME,
    val copyrightOwner: String = BuildConfig.COPYRIGHT_OWNER
)

object AppIdentityVerifier {
    fun getPinnedCertSha256(): String = BuildConfig.PINNED_RELEASE_CERT_SHA256
}

object SecurityManager {
    fun getDeveloperInfo(): DeveloperInfo = DeveloperInfo()
}
