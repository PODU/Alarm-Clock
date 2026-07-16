package com.sleepalarm

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormats {

    fun clockFormatter(context: Context): DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
            Locale.getDefault()
        )

    fun dayClockFormatter(context: Context): DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            if (DateFormat.is24HourFormat(context)) "EEE HH:mm" else "EEE h:mm a",
            Locale.getDefault()
        )

    fun formatClock(context: Context, millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(clockFormatter(context))

    fun formatDayClock(context: Context, millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(dayClockFormatter(context))

    fun formatCountdown(nowMillis: Long, targetMillis: Long): String {
        val totalMinutes = ((targetMillis - nowMillis).coerceAtLeast(0L) + 59_999) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
