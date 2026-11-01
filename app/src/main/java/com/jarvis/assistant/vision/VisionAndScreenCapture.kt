package com.jarvis.assistant.vision

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenCaptureManager private constructor() {

    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

    private val _hasReceivedFrame = MutableStateFlow(false)
    val hasReceivedFrame: StateFlow<Boolean> = _hasReceivedFrame.asStateFlow()

    fun toggleSharing() {
        val next = !_isSharing.value
        _isSharing.value = next
        _hasReceivedFrame.value = next
    }

    companion object {
        @Volatile
        private var INSTANCE: ScreenCaptureManager? = null

        fun getInstance(): ScreenCaptureManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenCaptureManager().also { INSTANCE = it }
            }
        }
    }
}

class VisionAnalysisModule private constructor() {

    enum class SceneCategory {
        GENERAL,
        SOCIAL,
        TRADING,
        CODE,
        MEDIA
    }

    data class SceneAnalysis(
        val sceneCategory: SceneCategory,
        val summary: String
    )

    private val _latestScene = MutableStateFlow<SceneAnalysis?>(null)
    val latestScene: StateFlow<SceneAnalysis?> = _latestScene.asStateFlow()

    fun updateScene(category: SceneCategory, summary: String) {
        _latestScene.value = SceneAnalysis(sceneCategory = category, summary = summary)
    }

    companion object {
        @Volatile
        private var INSTANCE: VisionAnalysisModule? = null

        fun getInstance(context: Context): VisionAnalysisModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VisionAnalysisModule().also { INSTANCE = it }
            }
        }
    }
}
