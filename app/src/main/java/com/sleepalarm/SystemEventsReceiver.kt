package com.sleepalarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SystemEventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = AlarmScheduler(context)
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                scheduler.rescheduleAll(forceRecompute = true)
                scheduler.rearmAuxiliarySlots()
            }
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                scheduler.rescheduleAll()
                scheduler.rearmAuxiliarySlots()
            }
        }
    }
}
