package com.jarvis.assistant.alarm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

class AlarmRepository private constructor() {

    data class AlarmItem(
        val id: Long = 0L,
        val hour: Int = 7,
        val minute: Int = 0,
        val label: String = "",
        val enabled: Boolean = true,
        val nextTriggerEpochMillis: Long = 0L
    )

    data class VoiceActionNotice(
        val actionType: String,
        val summaryText: String
    )

    private var lastDeletedAlarm: AlarmItem? = null

    private val _allAlarms = MutableStateFlow<List<AlarmItem>>(
        listOf(
            AlarmItem(
                id = 1L,
                hour = 7,
                minute = 0,
                label = "Morning Wakeup (Pari Voice)",
                enabled = true,
                nextTriggerEpochMillis = computeNextTriggerMillis(7, 0)
            )
        )
    )
    val allAlarms: StateFlow<List<AlarmItem>> = _allAlarms.asStateFlow()

    private val _lastVoiceAction = MutableStateFlow<VoiceActionNotice?>(null)
    val lastVoiceAction: StateFlow<VoiceActionNotice?> = _lastVoiceAction.asStateFlow()

    fun addAlarm(hour: Int, minute: Int, label: String): AlarmItem {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val item = AlarmItem(
            id = System.currentTimeMillis(),
            hour = h,
            minute = m,
            label = label.ifBlank { "Voice Alarm" },
            enabled = true,
            nextTriggerEpochMillis = computeNextTriggerMillis(h, m)
        )
        _allAlarms.value = _allAlarms.value + item
        _lastVoiceAction.value = VoiceActionNotice("CREATED", "Alarm set for %02d:%02d (%s)".format(h, m, item.label))
        return item
    }

    fun toggleAlarm(id: Long) {
        _allAlarms.value = _allAlarms.value.map { alarm ->
            if (alarm.id == id) {
                val nextEnabled = !alarm.enabled
                alarm.copy(
                    enabled = nextEnabled,
                    nextTriggerEpochMillis = if (nextEnabled) computeNextTriggerMillis(alarm.hour, alarm.minute) else 0L
                )
            } else {
                alarm
            }
        }
    }

    fun deleteAlarm(id: Long) {
        val target = _allAlarms.value.firstOrNull { it.id == id }
        if (target != null) {
            lastDeletedAlarm = target
            _allAlarms.value = _allAlarms.value.filterNot { it.id == id }
            _lastVoiceAction.value = VoiceActionNotice("DELETED", "Deleted alarm ${target.label}")
        }
    }

    suspend fun undoLastDelete() {
        val restored = lastDeletedAlarm ?: return
        lastDeletedAlarm = null
        _allAlarms.value = _allAlarms.value + restored
    }

    fun clearVoiceActionNotice() {
        _lastVoiceAction.value = null
    }

    companion object {
        @Volatile
        private var INSTANCE: AlarmRepository? = null

        fun getInstance(context: Context): AlarmRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlarmRepository().also { INSTANCE = it }
            }
        }

        private fun computeNextTriggerMillis(hour: Int, minute: Int): Long {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }
    }
}
