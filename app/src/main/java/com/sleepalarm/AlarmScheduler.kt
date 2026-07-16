package com.sleepalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE = "snooze"
        const val SNOOZE_ID = 0
        const val SNOOZE_REQUEST_BASE = 100_000
        const val BEDTIME_REMINDER_ID = -1
        const val WAKE_CHECK_ID = -2
        const val EXTRA_BEDTIME_REMINDER = "bedtime_reminder"
        const val EXTRA_WAKE_CHECK = "wake_check"
        const val GENTLE_REQUEST_BASE = 200_000
        const val EXTRA_GENTLE_WAKE = "gentle_wake"
        const val MAX_ESCALATED_SNOOZE_MINUTES = 60
        private const val WINDOW_MILLIS = 10 * 60_000L

        fun escalatedSnoozeMinutes(
            baseMinutes: Int,
            escalationMinutes: Int,
            priorSnoozes: Int
        ): Int {
            if (escalationMinutes <= 0 || priorSnoozes <= 0) return baseMinutes
            return (baseMinutes + priorSnoozes * escalationMinutes)
                .coerceAtMost(MAX_ESCALATED_SNOOZE_MINUTES)
        }
    }

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(alarm: Alarm) {
        scheduleWithFallback(alarm.id, alarm.triggerAtMillis)
        scheduleGentleWake(alarm.id, alarm.triggerAtMillis)
    }

    fun cancel(alarm: Alarm) = cancelAllFor(alarm.id)

    fun cancelAllFor(alarmId: Int) {
        alarmManager.cancel(alarmPendingIntent(alarmId))
        alarmManager.cancel(gentleWakePendingIntent(alarmId))
        cancelSnooze(alarmId)
    }

    fun snooze(alarmId: Int) {
        val settings = AlarmSettings(context)
        val minutes = escalatedSnoozeMinutes(
            settings.snoozeMinutes,
            settings.snoozeEscalationMinutes,
            (settings.snoozeCount - 1).coerceAtLeast(0)
        )
        scheduleSnoozeAt(alarmId, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun scheduleSnoozeAt(alarmId: Int, triggerAtMillis: Long) {
        scheduleWithFallback(
            snoozeRequestCode(alarmId),
            triggerAtMillis,
            snoozePendingIntent(alarmId)
        )
        AlarmSettings(context).addSnoozeTarget(alarmId, triggerAtMillis)
    }

    fun cancelSnooze(alarmId: Int) {
        AlarmSettings(context).removeSnoozeTarget(alarmId)
        alarmManager.cancel(snoozePendingIntent(alarmId))
    }

    fun clearSnoozeTarget(alarmId: Int) {
        AlarmSettings(context).removeSnoozeTarget(alarmId)
    }

    fun scheduleWakeCheck(triggerAtMillis: Long) {
        scheduleWithFallback(WAKE_CHECK_ID, triggerAtMillis, wakeCheckPendingIntent())
    }

    fun cancelWakeCheck() {
        alarmManager.cancel(wakeCheckPendingIntent())
    }

    fun scheduleBedtimeReminder(triggerAtMillis: Long) {
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            WINDOW_MILLIS,
            bedtimeReminderPendingIntent()
        )
    }

    fun cancelBedtimeReminder() {
        alarmManager.cancel(bedtimeReminderPendingIntent())
    }

    fun rescheduleAll(forceRecompute: Boolean = false) {
        val store = AlarmStore(context)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val updated = store.load().map { alarm ->
            when {
                !alarm.enabled -> alarm
                alarm.repeatDays.isNotEmpty() -> {
                    val next = if (alarm.triggerAtMillis > now && !forceRecompute) {
                        alarm.triggerAtMillis
                    } else {
                        AlarmTimeCalculator.nextTrigger(
                            alarm.hour, alarm.minute, alarm.repeatDays, now, zone
                        )
                    }
                    val skipStillValid = alarm.skipNext && next == alarm.triggerAtMillis
                    alarm.copy(triggerAtMillis = next, skipNext = skipStillValid)
                        .also(::schedule)
                }
                alarm.triggerAtMillis > now -> alarm.also(::schedule)
                else -> alarm.copy(enabled = false)
            }
        }
        store.save(updated)
    }

    fun rearmAuxiliarySlots() {
        val settings = AlarmSettings(context)
        val now = System.currentTimeMillis()

        val restored = settings.snoozeTargets.mapNotNull { (alarmId, target) ->
            BootReceiver.restoredSnoozeTarget(target, now)?.let { alarmId to it }
        }.toMap()
        settings.snoozeTargets = restored
        restored.forEach { (alarmId, target) -> scheduleSnoozeAt(alarmId, target) }
        if (restored.isEmpty()) settings.snoozeCount = 0

        if (settings.bedtimeReminderEnabled) {
            scheduleBedtimeReminder(
                AlarmTimeCalculator.nextOccurrence(
                    settings.bedtimeSecondOfDay, now, ZoneId.systemDefault()
                )
            )
        }
    }

    private fun scheduleExact(id: Int, triggerAtMillis: Long, operation: PendingIntent): Boolean {
        if (!canScheduleExact()) return false

        val showIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                operation
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun scheduleWithFallback(
        id: Int,
        triggerAtMillis: Long,
        operation: PendingIntent = alarmPendingIntent(id)
    ) {
        if (scheduleExact(id, triggerAtMillis, operation)) return
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            WINDOW_MILLIS,
            operation
        )
    }

    private fun scheduleGentleWake(alarmId: Int, triggerAtMillis: Long) {
        alarmManager.cancel(gentleWakePendingIntent(alarmId))
        val minutes = AlarmSettings(context).gentleWakeMinutes
        if (minutes <= 0) return
        val leadMillis = minutes * 60_000L
        if (triggerAtMillis - System.currentTimeMillis() <= leadMillis) return
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis - leadMillis,
            minOf(WINDOW_MILLIS, leadMillis / 2),
            gentleWakePendingIntent(alarmId)
        )
    }

    private fun snoozeRequestCode(alarmId: Int): Int = SNOOZE_REQUEST_BASE + alarmId

    private fun alarmPendingIntent(id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun snoozePendingIntent(alarmId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(alarmId),
            Intent(context, AlarmReceiver::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_SNOOZE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun gentleWakePendingIntent(alarmId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            GENTLE_REQUEST_BASE + alarmId,
            Intent(context, AlarmReceiver::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_GENTLE_WAKE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun wakeCheckPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            WAKE_CHECK_ID,
            Intent(context, AlarmReceiver::class.java)
                .putExtra(EXTRA_WAKE_CHECK, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun bedtimeReminderPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            BEDTIME_REMINDER_ID,
            Intent(context, AlarmReceiver::class.java)
                .putExtra(EXTRA_BEDTIME_REMINDER, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
