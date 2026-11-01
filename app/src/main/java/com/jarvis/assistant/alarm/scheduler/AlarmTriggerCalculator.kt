package com.jarvis.assistant.alarm.scheduler

import java.util.Locale

object AlarmTriggerCalculator {
    fun formatTime12Or24(hour: Int, minute: Int, is24Hour: Boolean = false): Pair<String, String> {
        return if (is24Hour) {
            String.format(Locale.US, "%02d:%02d", hour, minute) to ""
        } else {
            val amPm = if (hour >= 12) "PM" else "AM"
            val h12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            String.format(Locale.US, "%d:%02d", h12, minute) to amPm
        }
    }
}
