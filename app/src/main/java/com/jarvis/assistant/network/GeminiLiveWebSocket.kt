package com.jarvis.assistant.network

import android.util.Base64
import com.jarvis.assistant.data.model.GeminiConstants
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveWebSocket(
    private val apiKey: String,
    private val model: String,
    private val voiceName: String,
    private val systemPrompt: String,
    private val listener: Listener
) {

    interface Listener {
        fun onConnectionStateChanged(status: String)
        fun onAudioDataReceived(pcmData: ByteArray)
        fun onAssistantTextReceived(textChunk: String)
        fun onUserTextReceived(textChunk: String)
        fun onInterrupted()
        fun onTurnCompleted()
        fun onError(message: String, isFatal: Boolean, errorCode: String?)
        fun onToolCall(callId: String, name: String, args: JSONObject)
        fun onGroundingMetadataReceived(metadata: JSONObject)
    }

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val sessionReady = AtomicBoolean(false)
    private val modelInfo = GeminiConstants.getModelInfo(model)
    private var activeWireModel: String = modelInfo.bidiWireModel
    private var fallbackCursor: Int = 0

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    fun getActiveWireModel(): String = activeWireModel

    fun isSessionReady(): Boolean = sessionReady.get()

    fun connect() {
        if (apiKey.isBlank()) {
            listener.onError("API key is missing.", true, "API_KEY_MISSING")
            return
        }
        sessionReady.set(false)
        listener.onConnectionStateChanged("CONNECTING")
        val endpoint =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(endpoint).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                sendSetupMessage(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                sessionReady.set(false)
                listener.onError(t.message ?: "WebSocket connection failed", false, "WS_FAILURE")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                sessionReady.set(false)
                listener.onConnectionStateChanged("CLOSED")
            }
        })
    }

    fun disconnect() {
        isConnected.set(false)
        sessionReady.set(false)
        try {
            webSocket?.close(1000, "Client disconnect")
        } catch (_: Exception) {
        }
        webSocket = null
    }

    private fun tryNextFallbackModel(): Boolean {
        val fallbacks = modelInfo.fallbackBidiWireModels.filter { it != activeWireModel }
        if (fallbacks.isEmpty()) return false
        val next = fallbacks[fallbackCursor % fallbacks.size]
        fallbackCursor++
        activeWireModel = next
        return true
    }

    private fun sendSetupMessage(ws: WebSocket) {
        val functionDeclarations = JSONArray()
            .put(
                JSONObject()
                    .put("name", "open_app")
                    .put("description", "Open an installed Android application by name.")
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put("properties", JSONObject().put("app_name", JSONObject().put("type", "STRING")))
                    )
            )
            .put(
                JSONObject()
                    .put("name", "send_whatsapp_message")
                    .put("description", "Send a WhatsApp text message to a contact.")
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put(
                                "properties",
                                JSONObject()
                                    .put("contact", JSONObject().put("type", "STRING"))
                                    .put("message", JSONObject().put("type", "STRING"))
                            )
                    )
            )
            .put(
                JSONObject()
                    .put("name", "send_whatsapp_voice")
                    .put("description", "Send a WhatsApp voice message to a contact.")
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put("properties", JSONObject().put("contact", JSONObject().put("type", "STRING")))
                    )
            )
            .put(
                JSONObject()
                    .put("name", "builtin_chrome_search")
                    .put("description", "Perform a live grounded web search with citations.")
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put("properties", JSONObject().put("query", JSONObject().put("type", "STRING")))
                    )
            )

        val toolsArray = JSONArray()
            .put(JSONObject().put("google_search", JSONObject()))
            .put(JSONObject().put("functionDeclarations", functionDeclarations))

        val generationConfig = JSONObject()
            .put("response_modalities", JSONArray().put("AUDIO"))
            .put(
                "speech_config",
                JSONObject().put(
                    "voice_config",
                    JSONObject().put(
                        "prebuilt_voice_config",
                        JSONObject().put("voice_name", voiceName)
                    )
                )
            )

        val systemInstruction = JSONObject().put(
            "parts",
            JSONArray().put(JSONObject().put("text", systemPrompt))
        )

        val setupObj = JSONObject()
            .put("model", activeWireModel)
            .put("generation_config", generationConfig)
            .put("system_instruction", systemInstruction)
            .put("tools", toolsArray)

        val root = JSONObject().put("setup", setupObj)
        ws.send(root.toString().replace("\\/", "/"))
    }

    private fun handleIncomingMessage(rawJson: String) {
        if (rawJson.isBlank()) return
        try {
            val root = JSONObject(rawJson)
            if (root.has("setupComplete")) {
                sessionReady.set(true)
                listener.onConnectionStateChanged("LIVE")
                return
            }

            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                val groundingMetadata = serverContent.optJSONObject("groundingMetadata")
                if (groundingMetadata != null) {
                    listener.onGroundingMetadataReceived(groundingMetadata)
                }

                val inputTrans = serverContent.optJSONObject("inputTranscription")?.optString("text", "").orEmpty()
                if (inputTrans.isNotEmpty()) {
                    listener.onUserTextReceived(inputTrans)
                }

                val outputTrans = serverContent.optJSONObject("outputTranscription")?.optString("text", "").orEmpty()
                if (outputTrans.isNotEmpty()) {
                    listener.onAssistantTextReceived(outputTrans)
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                val parts = modelTurn?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        val textPart = part.optString("text", "")
                        if (textPart.isNotEmpty() && outputTrans.isEmpty()) {
                            listener.onAssistantTextReceived(textPart)
                        }
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val mimeType = inlineData.optString("mimeType", "")
                            val b64 = inlineData.optString("data", "")
                            if (mimeType.startsWith("audio/pcm") && b64.isNotEmpty()) {
                                val pcmBytes = Base64.decode(b64, Base64.DEFAULT)
                                listener.onAudioDataReceived(pcmBytes)
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("interrupted", false)) {
                    listener.onInterrupted()
                }
                if (serverContent.optBoolean("turnComplete", false)) {
                    listener.onTurnCompleted()
                }
            }

            val toolCall = root.optJSONObject("toolCall")
            if (toolCall != null) {
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.optJSONObject(i) ?: continue
                        val callId = call.optString("id", "call_$i")
                        val name = call.optString("name", "")
                        val args = call.optJSONObject("args") ?: JSONObject()
                        if (name.isNotEmpty()) {
                            listener.onToolCall(callId, name, args)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            listener.onError("Failed to parse Live WebSocket message: ${e.message}", false, "PARSE_ERROR")
        }
    }

    fun sendAudioChunk(pcm16k: ByteArray) {
        val ws = webSocket ?: return
        if (!isConnected.get()) return
        val b64 = Base64.encodeToString(pcm16k, Base64.NO_WRAP)
        val chunkObj = JSONObject()
            .put("mime_type", "audio/pcm;rate=16000")
            .put("data", b64)
        val realtimeInput = JSONObject().put("media_chunks", JSONArray().put(chunkObj))
        val payload = JSONObject().put("realtime_input", realtimeInput)
        ws.send(payload.toString().replace("\\/", "/"))
    }

    fun sendAudioStreamEnd() {
        val ws = webSocket ?: return
        if (!isConnected.get()) return
        val realtimeInput = JSONObject().put("audio_stream_end", true)
        val payload = JSONObject().put("realtime_input", realtimeInput)
        ws.send(payload.toString().replace("\\/", "/"))
    }

    fun sendToolResponse(callId: String, name: String, result: JSONObject) {
        val ws = webSocket ?: return
        if (!isConnected.get()) return
        val funcResp = JSONObject()
            .put("id", callId)
            .put("name", name)
            .put("response", JSONObject().put("result", result))
        val toolResponse = JSONObject().put("function_responses", JSONArray().put(funcResp))
        val payload = JSONObject().put("tool_response", toolResponse)
        ws.send(payload.toString().replace("\\/", "/"))
    }
}
