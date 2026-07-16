package com.sleepalarm

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToLong

object WakeTimeCalculator {

    private const val CYCLE_MINUTES = 90
    const val DEFAULT_FALL_ASLEEP_MINUTES = 15

    data class CycleSuggestion(
        val cycleCount: Int,
        val sleepHours: Float,
        val wakeMillis: Long
    )

    fun cycleSuggestions(
        bedtimeMillis: Long,
        fallAsleepMinutes: Int = DEFAULT_FALL_ASLEEP_MINUTES,
        cycles: IntRange = 3..7
    ): List<CycleSuggestion> = cycles.map { count ->
        CycleSuggestion(
            cycleCount = count,
            sleepHours = count * 1.5f,
            wakeMillis = bedtimeMillis +
                (fallAsleepMinutes + count * CYCLE_MINUTES) * 60_000L
        )
    }

    fun bedtimeMillis(
        sleepNow: Boolean,
        nowMillis: Long,
        bedtime: LocalTime,
        zone: ZoneId
    ): Long {
        if (sleepNow) return nowMillis
        val nowDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        var candidate = LocalDateTime.of(nowDateTime.toLocalDate(), bedtime)
        if (candidate.isBefore(nowDateTime)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    fun wakeMillis(bedtimeMillis: Long, sleepHours: Float): Long =
        bedtimeMillis + (sleepHours * 3_600_000f).roundToLong()
}
