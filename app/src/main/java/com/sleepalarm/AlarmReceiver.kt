package com.sleepalarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.ZoneId

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val BEDTIME_CHANNEL_ID = "bedtime_channel"
        private const val BEDTIME_NOTIFICATION_ID = 43
        const val WAKE_CHECK_CHANNEL_ID = "wake_check_channel"
        const val WAKE_CHECK_NOTIFICATION_ID = 44
        private const val FALLBACK_NOTIFICATION_ID = 45
        const val ACTION_CONFIRM_AWAKE = "com.sleepalarm.action.CONFIRM_AWAKE"
        const val ACTION_UNDO_DISMISS = "com.sleepalarm.action.UNDO_DISMISS"
    }

    @SuppressLint("Wakelock")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CONFIRM_AWAKE) {
            confirmAwake(context)
            return
        }

        if (intent.action == ACTION_UNDO_DISMISS) {
            undoDismiss(context, intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0))
            return
        }

        if (intent.getBooleanExtra(AlarmScheduler.EXTRA_BEDTIME_REMINDER, false)) {
            postBedtimeReminder(context)
            scheduleNextBedtimeReminder(context)
            return
        }

        if (intent.getBooleanExtra(AlarmScheduler.EXTRA_GENTLE_WAKE, false)) {
            startGentleWake(context, intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0))
            return
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepAlarm:receiver")
        wakeLock.acquire(10_000L)

        val settings = AlarmSettings(context)
        if (intent.getBooleanExtra(AlarmScheduler.EXTRA_WAKE_CHECK, false)) {
            settings.wakeCheckPending = false
            settings.lastRingFromWakeCheck = true
            settings.ringingAlarmId = 0
            context.getSystemService(NotificationManager::class.java)
                .cancel(WAKE_CHECK_NOTIFICATION_ID)
        } else {
            settings.lastRingFromWakeCheck = false

            val alarmId =
                intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, AlarmScheduler.SNOOZE_ID)
            settings.ringingAlarmId = alarmId

            val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false) ||
                alarmId == AlarmScheduler.SNOOZE_ID
            if (isSnooze) {
                AlarmScheduler(context).clearSnoozeTarget(alarmId)
            } else {
                advance(context, alarmId)
            }
        }

        val serviceIntent = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_START)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            postFallbackAlarmNotification(context)
        }

        QuickAlarmWidgetProvider.updateAll(context)
    }

    private fun postFallbackAlarmNotification(context: Context) {
        AlarmService.createAlarmChannel(context)
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            FALLBACK_NOTIFICATION_ID,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AlarmActivity.EXTRA_FALLBACK_ALERT, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(NotificationManager::class.java).notify(
            FALLBACK_NOTIFICATION_ID,
            Notification.Builder(context, AlarmService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.alarm_notification_title))
                .setContentText(context.getString(R.string.alarm_notification_text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setFullScreenIntent(fullScreenIntent, true)
                .setContentIntent(fullScreenIntent)
                .build()
        )
    }

    private fun startGentleWake(context: Context, alarmId: Int) {
        val alarm = AlarmStore(context).get(alarmId) ?: return
        if (!alarm.enabled || alarm.triggerAtMillis <= System.currentTimeMillis()) return
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java)
                    .setAction(AlarmService.ACTION_GENTLE)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmService.EXTRA_GENTLE_TRIGGER_AT, alarm.triggerAtMillis)
            )
        } catch (e: Exception) {
        }
    }

    private fun undoDismiss(context: Context, alarmId: Int) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(AlarmService.DISMISSED_NOTIFICATION_ID)
        val settings = AlarmSettings(context)
        settings.lastRingFromWakeCheck = false
        settings.ringingAlarmId = alarmId
        val serviceIntent = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_START)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            postFallbackAlarmNotification(context)
        }
    }

    private fun confirmAwake(context: Context) {
        AlarmScheduler(context).cancelWakeCheck()
        AlarmSettings(context).wakeCheckPending = false
        context.getSystemService(NotificationManager::class.java)
            .cancel(WAKE_CHECK_NOTIFICATION_ID)
    }

    private fun advance(context: Context, alarmId: Int) {
        val store = AlarmStore(context)
        val alarm = store.get(alarmId) ?: run {
            Log.w(TAG, "Fired alarm $alarmId is not in the store; ignoring")
            return
        }
        if (alarm.repeatDays.isNotEmpty()) {
            val next = alarm.copy(
                triggerAtMillis = AlarmTimeCalculator.nextTrigger(
                    alarm.hour,
                    alarm.minute,
                    alarm.repeatDays,
                    System.currentTimeMillis(),
                    ZoneId.systemDefault()
                ),
                skipNext = false
            )
            store.upsert(next)
            AlarmScheduler(context).schedule(next)
        } else {
            store.upsert(alarm.copy(enabled = false, skipNext = false))
        }
    }

    private fun postBedtimeReminder(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                BEDTIME_CHANNEL_ID,
                context.getString(R.string.bedtime_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            AlarmScheduler.BEDTIME_REMINDER_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            BEDTIME_NOTIFICATION_ID,
            Notification.Builder(context, BEDTIME_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.bedtime_reminder_title))
                .setContentText(
                    context.getString(
                        R.string.bedtime_reminder_text,
                        AlarmSettings(context).sleepHours
                    )
                )
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
        )
    }

    private fun scheduleNextBedtimeReminder(context: Context) {
        val settings = AlarmSettings(context)
        if (!settings.bedtimeReminderEnabled) return
        AlarmScheduler(context).scheduleBedtimeReminder(
            AlarmTimeCalculator.nextOccurrence(
                settings.bedtimeSecondOfDay,
                System.currentTimeMillis(),
                ZoneId.systemDefault()
            )
        )
    }
}
