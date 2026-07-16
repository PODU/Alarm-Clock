package com.sleepalarm

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object AlarmTimeCalculator {

    fun nextTrigger(
        hour: Int,
        minute: Int,
        repeatDays: Set<Int>,
        nowMillis: Long,
        zone: ZoneId
    ): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (repeatDays.isNotEmpty() && date.dayOfWeek.value !in repeatDays) continue
            val candidate = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
            if (candidate > nowMillis) return candidate
        }
        throw IllegalArgumentException("repeatDays contains no valid day: $repeatDays")
    }

    fun nextTriggerSkippingOne(
        hour: Int,
        minute: Int,
        repeatDays: Set<Int>,
        nowMillis: Long,
        zone: ZoneId
    ): Long {
        val next = nextTrigger(hour, minute, repeatDays, nowMillis, zone)
        return nextTrigger(hour, minute, repeatDays, next, zone)
    }

    fun nextOccurrence(secondOfDay: Int, nowMillis: Long, zone: ZoneId): Long {
        val time = LocalTime.ofSecondOfDay(secondOfDay.toLong())
        return nextTrigger(time.hour, time.minute, emptySet(), nowMillis, zone)
    }
}
