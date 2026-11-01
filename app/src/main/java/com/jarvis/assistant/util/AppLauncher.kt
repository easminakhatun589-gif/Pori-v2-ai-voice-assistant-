package com.jarvis.assistant.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale

object AppLauncher {

    data class InstalledAppInfo(
        val packageName: String,
        val label: String,
        val className: String = ""
    )

    sealed class OpenAppResult {
        data class SingleFound(val app: InstalledAppInfo) : OpenAppResult()
        data class MultipleFound(val apps: List<InstalledAppInfo>) : OpenAppResult()
        object NotFound : OpenAppResult()
    }

    private val multilingualAliases: Map<String, List<String>> = mapOf(
        "com.whatsapp" to listOf(
            "whatsapp", "হোয়াটসঅ্যাপ", "হোয়াটস অ্যাপ", "व्हाट्सएप", "व्हाट्सऐप", "wa"
        ),
        "com.whatsapp.w4b" to listOf(
            "whatsapp business", "হোয়াটসঅ্যাপ বিজনেস", "व्हाट्सएप बिजनेस"
        ),
        "com.google.android.youtube" to listOf(
            "youtube", "ইউটিউব", "यूट्यूब", "yt"
        ),
        "com.facebook.katana" to listOf(
            "facebook", "ফেসবুক", "फेसबुक", "fb"
        ),
        "com.instagram.android" to listOf(
            "instagram", "ইনস্টাগ্রাম", "इंस्टाग्राम", "insta"
        ),
        "com.android.chrome" to listOf(
            "chrome", "ক্রোম", "क्रोम", "browser"
        )
    )

    fun findMatchingApps(context: Context, query: String): List<InstalledAppInfo> {
        val clean = query.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) return emptyList()

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        val installed = resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = info.nonLocalizedLabel?.toString()
                ?: info.loadLabel(pm)?.toString()
                ?: pkg
            val cls = info.activityInfo?.name.orEmpty()
            InstalledAppInfo(packageName = pkg, label = label, className = cls)
        }

        val targetPackages = multilingualAliases.entries
            .filter { (_, aliases) -> aliases.any { it.equals(clean, ignoreCase = true) || clean.contains(it) } }
            .map { it.key }

        val ranked = mutableListOf<InstalledAppInfo>()
        for (targetPkg in targetPackages) {
            installed.filter { it.packageName == targetPkg }.forEach { app ->
                if (ranked.none { it.packageName == app.packageName }) {
                    ranked.add(app)
                }
            }
        }

        installed.filter { app ->
            app.label.lowercase(Locale.ROOT).contains(clean) ||
                app.packageName.lowercase(Locale.ROOT).contains(clean)
        }.forEach { app ->
            if (ranked.none { it.packageName == app.packageName }) {
                ranked.add(app)
            }
        }

        return ranked
    }

    fun openAppResult(context: Context, query: String): OpenAppResult {
        val matches = findMatchingApps(context, query)
        if (matches.isEmpty()) return OpenAppResult.NotFound

        val primary = matches.first()
        val cloneList = try {
            Settings.Secure.getString(context.contentResolver, "clone_app_list").orEmpty()
        } catch (_: Exception) {
            ""
        }

        val isCloned = cloneList.split(",", ";", " ").any { it.trim() == primary.packageName }
        return if (isCloned || matches.size > 1) {
            val allOptions = if (matches.size > 1) {
                matches
            } else {
                listOf(primary, primary.copy(label = "${primary.label} (Clone)"))
            }
            OpenAppResult.MultipleFound(allOptions)
        } else {
            OpenAppResult.SingleFound(primary)
        }
    }

    fun launchApp(context: Context, query: String): Boolean {
        val matches = findMatchingApps(context, query)
        val target = matches.firstOrNull() ?: return false
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(target.packageName)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    setPackage(target.packageName)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    if (target.className.isNotBlank()) {
                        setClassName(target.packageName, target.className)
                    }
                }
            launchIntent.setPackage(target.packageName)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
