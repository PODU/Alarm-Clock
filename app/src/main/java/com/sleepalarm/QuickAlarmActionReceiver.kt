package com.sleepalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class QuickAlarmActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_QUICK_ALARM = "com.sleepalarm.action.QUICK_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_QUICK_ALARM) return
        val alarm = QuickAlarm.create(context)
        Toast.makeText(
            context,
            context.getString(
                R.string.quick_alarm_set,
                QuickAlarm.formatWakeTime(context, alarm.triggerAtMillis)
            ),
            Toast.LENGTH_SHORT
        ).show()
        QuickAlarmWidgetProvider.updateAll(context)
    }
}
