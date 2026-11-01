package com.jarvis.assistant.network

import com.jarvis.assistant.data.model.GeminiConstants
import java.util.concurrent.ConcurrentHashMap

object VoiceModelHealthMonitor {

    data class ModelTelemetry(
        val modelId: String,
        val isWsConnected: Boolean,
        val wsPingMs: Long,
        val resolvedBidiWireModel: String,
        val stallCount: Int = 0,
        val lastError: String? = null
    ) {
        fun formatCompactPingBadge(): String {
            return if (isWsConnected) {
                "🟢 WS ${wsPingMs}ms"
            } else {
                "⚪ WS --ms"
            }
        }
    }

    private val telemetryMap = ConcurrentHashMap<String, ModelTelemetry>()

    fun resolveWorkingBidiWireModel(modelId: String): String {
        val info = GeminiConstants.getModelInfo(modelId)
        val existing = telemetryMap[info.id]
        if (existing != null && existing.stallCount > 0 && info.fallbackBidiWireModels.isNotEmpty()) {
            val idx = (existing.stallCount - 1) % info.fallbackBidiWireModels.size
            return info.fallbackBidiWireModels[idx]
        }
        return info.bidiWireModel
    }

    fun recordModelStallOrError(modelId: String, reason: String) {
        val info = GeminiConstants.getModelInfo(modelId)
        val prev = telemetryMap[info.id]
        val nextStalls = (prev?.stallCount ?: 0) + 1
        val nextWire = if (info.fallbackBidiWireModels.isNotEmpty()) {
            info.fallbackBidiWireModels[(nextStalls - 1) % info.fallbackBidiWireModels.size]
        } else {
            info.bidiWireModel
        }
        telemetryMap[info.id] = ModelTelemetry(
            modelId = info.id,
            isWsConnected = false,
            wsPingMs = prev?.wsPingMs ?: 0L,
            resolvedBidiWireModel = nextWire,
            stallCount = nextStalls,
            lastError = reason
        )
    }

    fun selectBestFailoverModel(stalledModelId: String): GeminiConstants.ModelInfo {
        val stalledInfo = GeminiConstants.getModelInfo(stalledModelId)
        return GeminiConstants.MODEL_CATALOG.firstOrNull { it.id != stalledInfo.id }
            ?: GeminiConstants.MODEL_CATALOG.first()
    }

    fun updateModelWithWsProbe(
        modelId: String,
        wireModel: String,
        wsLatencyMs: Long,
        isSetupComplete: Boolean
    ) {
        telemetryMap[modelId] = ModelTelemetry(
            modelId = modelId,
            isWsConnected = isSetupComplete,
            wsPingMs = wsLatencyMs,
            resolvedBidiWireModel = wireModel,
            stallCount = 0,
            lastError = null
        )
    }

    fun getTelemetry(modelId: String): ModelTelemetry {
        return telemetryMap[modelId] ?: ModelTelemetry(
            modelId = modelId,
            isWsConnected = false,
            wsPingMs = 0L,
            resolvedBidiWireModel = resolveWorkingBidiWireModel(modelId)
        )
    }
}
