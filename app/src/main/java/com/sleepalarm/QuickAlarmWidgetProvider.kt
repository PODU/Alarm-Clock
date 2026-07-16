package com.sleepalarm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class QuickAlarmWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, QuickAlarmWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                manager.updateAppWidget(ids, render(context))
            }
        }

        private fun render(context: Context): RemoteViews {
            val next = QuickAlarm.nextEnabledAlarm(
                AlarmStore(context).load(),
                System.currentTimeMillis()
            )
            return RemoteViews(context.packageName, R.layout.widget_quick_alarm).apply {
                setTextViewText(
                    R.id.widget_next_alarm,
                    when {
                        next == null -> context.getString(R.string.widget_no_alarm)
                        next.label.isNotEmpty() -> context.getString(
                            R.string.widget_next_alarm_labeled,
                            next.label,
                            TimeFormats.formatDayClock(context, next.triggerAtMillis)
                        )
                        else -> context.getString(
                            R.string.widget_next_alarm,
                            TimeFormats.formatDayClock(context, next.triggerAtMillis)
                        )
                    }
                )
                setTextViewText(
                    R.id.widget_sleep_now,
                    context.getString(
                        R.string.widget_sleep_now,
                        AlarmSettings(context).sleepHours
                    )
                )
                setOnClickPendingIntent(
                    R.id.widget_sleep_now,
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(context, QuickAlarmActionReceiver::class.java)
                            .setAction(QuickAlarmActionReceiver.ACTION_QUICK_ALARM),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetManager.updateAppWidget(appWidgetIds, render(context))
    }
}
