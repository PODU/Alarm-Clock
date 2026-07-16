package com.sleepalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    companion object {
        fun restoredSnoozeTarget(storedTargetMillis: Long, nowMillis: Long): Long? =
            when {
                storedTargetMillis <= 0L -> null
                storedTargetMillis > nowMillis -> storedTargetMillis
                else -> nowMillis + 60_000L
            }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val scheduler = AlarmScheduler(context)
        scheduler.rescheduleAll()

        AlarmSettings(context).wakeCheckPending = false

        scheduler.rearmAuxiliarySlots()
    }
}
