package com.jarvis.assistant

import android.app.Application
import com.jarvis.assistant.data.preferences.AppPreferences
import com.jarvis.assistant.security.configureAppCheck

class JarvisApp : Application() {

    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = AppPreferences(this)
        configureAppCheck(this, null)
    }

    companion object {
        @Volatile
        lateinit var instance: JarvisApp
            internal set
    }
}
