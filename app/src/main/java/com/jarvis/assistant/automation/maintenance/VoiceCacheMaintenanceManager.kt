package com.jarvis.assistant.automation.maintenance

import android.content.Context
import java.io.File

object VoiceCacheMaintenanceManager {

    fun autoPruneAsync(context: Context) {
        try {
            val cacheDir = context.cacheDir ?: return
            val cutoff = System.currentTimeMillis() - 30 * 60 * 1000L
            cacheDir.listFiles()?.forEach { file: File ->
                if (file.name.startsWith("mode_voice_") && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }
}
