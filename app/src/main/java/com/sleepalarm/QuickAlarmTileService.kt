package com.sleepalarm

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast

class QuickAlarmTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val alarm = QuickAlarm.create(this)
        Toast.makeText(
            this,
            getString(
                R.string.quick_alarm_set,
                QuickAlarm.formatWakeTime(this, alarm.triggerAtMillis)
            ),
            Toast.LENGTH_SHORT
        ).show()
        refreshTile()
        QuickAlarmWidgetProvider.updateAll(this)
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        try {
            val next = QuickAlarm.nextEnabledTrigger(
                AlarmStore(this).load(),
                System.currentTimeMillis()
            )
            tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
            tile.label = getString(R.string.tile_label)
            tile.state = if (next != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (next != null) {
                    TimeFormats.formatClock(this, next)
                } else {
                    getString(R.string.tile_no_alarm)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh quick-settings tile", e)
            tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "QuickAlarmTileService"
    }
}
