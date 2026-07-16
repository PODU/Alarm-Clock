package com.sleepalarm

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object QuickAlarm {

    private val LEGACY_WAKE_FORMATTER = DateTimeFormatter.ofPattern("EEE HH:mm")

    fun nextEnabledAlarm(alarms: List<Alarm>, nowMillis: Long): Alarm? =
        alarms
            .filter { it.enabled && it.triggerAtMillis > nowMillis }
            .minByOrNull { it.triggerAtMillis }

    fun nextEnabledTrigger(alarms: List<Alarm>, nowMillis: Long): Long? =
        nextEnabledAlarm(alarms, nowMillis)?.triggerAtMillis

    fun create(context: Context): Alarm {
        val store = AlarmStore(context)
        val wakeMillis = WakeTimeCalculator.wakeMillis(
            System.currentTimeMillis(),
            AlarmSettings(context).sleepHours
        )
        val local = Instant.ofEpochMilli(wakeMillis).atZone(ZoneId.systemDefault())
        val alarm = Alarm(
            id = store.nextId(),
            triggerAtMillis = wakeMillis,
            hour = local.hour,
            minute = local.minute,
            repeatDays = emptySet(),
            enabled = true
        )
        store.upsert(alarm)
        AlarmScheduler(context).schedule(alarm)
        return alarm
    }

    fun formatWakeTime(context: Context, millis: Long): String =
        TimeFormats.formatDayClock(context, millis)

    @Deprecated(
        "Always 24-hour; use the context overload so the system " +
            "12/24-hour preference is honoured.",
        ReplaceWith("formatWakeTime(context, millis)")
    )
    fun formatWakeTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(LEGACY_WAKE_FORMATTER)
}
