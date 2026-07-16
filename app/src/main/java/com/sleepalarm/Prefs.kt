package com.sleepalarm

import android.content.Context
import android.content.SharedPreferences

object Prefs {

    @Volatile
    private var migrated = false

    fun sharedPrefs(context: Context): SharedPreferences {
        val device = context.createDeviceProtectedStorageContext()
        if (!migrated) {
            device.moveSharedPreferencesFrom(context, AlarmSettings.PREFS_NAME)
            migrated = true
        }
        return device.getSharedPreferences(AlarmSettings.PREFS_NAME, Context.MODE_PRIVATE)
    }
}
