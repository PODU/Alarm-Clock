package com.sleepalarm

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmTimeCalculatorTest {

    private val zone = ZoneId.of("UTC")

    private fun millis(dateTime: LocalDateTime, zone: ZoneId = this.zone): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun oneShotLaterTodayStaysOnSameDay() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 6, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 13, 7, 30)),
            AlarmTimeCalculator.nextTrigger(7, 30, emptySet(), now, zone)
        )
    }

    @Test
    fun oneShotTimeAlreadyPassedRollsToTomorrow() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 8, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 14, 7, 30)),
            AlarmTimeCalculator.nextTrigger(7, 30, emptySet(), now, zone)
        )
    }

    @Test
    fun triggerIsStrictlyFutureWhenTimeIsExactlyNow() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 7, 30))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 14, 7, 30)),
            AlarmTimeCalculator.nextTrigger(7, 30, emptySet(), now, zone)
        )
    }

    @Test
    fun allDaysBehavesLikeEveryDay() {
        val allDays = (1..7).toSet()
        val before = millis(LocalDateTime.of(2026, 7, 13, 6, 0))
        val after = millis(LocalDateTime.of(2026, 7, 13, 8, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 13, 7, 30)),
            AlarmTimeCalculator.nextTrigger(7, 30, allDays, before, zone)
        )
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 14, 7, 30)),
            AlarmTimeCalculator.nextTrigger(7, 30, allDays, after, zone)
        )
    }

    @Test
    fun singleDayLaterThisWeekIsChosen() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 8, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 16, 7, 30)),
            AlarmTimeCalculator.nextTrigger(
                7, 30, setOf(DayOfWeek.THURSDAY.value), now, zone
            )
        )
    }

    @Test
    fun singleDayWhoseTimePassedRollsToNextWeek() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 8, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 20, 7, 30)),
            AlarmTimeCalculator.nextTrigger(
                7, 30, setOf(DayOfWeek.MONDAY.value), now, zone
            )
        )
    }

    @Test
    fun weekdaySetSkipsTheWeekend() {
        val now = millis(LocalDateTime.of(2026, 7, 17, 8, 0))
        val weekdays = setOf(1, 2, 3, 4, 5)
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 20, 7, 30)),
            AlarmTimeCalculator.nextTrigger(7, 30, weekdays, now, zone)
        )
    }

    @Test
    fun dstSpringForwardKeepsWallClockTime() {
        val eastern = ZoneId.of("America/New_York")
        val now = millis(LocalDateTime.of(2026, 3, 7, 12, 0), eastern)
        assertEquals(
            millis(LocalDateTime.of(2026, 3, 8, 7, 0), eastern),
            AlarmTimeCalculator.nextTrigger(
                7, 0, setOf(DayOfWeek.SUNDAY.value), now, eastern
            )
        )
    }

    @Test
    fun dstGapTimeResolvesToShiftedInstant() {
        val eastern = ZoneId.of("America/New_York")
        val now = millis(LocalDateTime.of(2026, 3, 8, 1, 0), eastern)
        assertEquals(
            millis(LocalDateTime.of(2026, 3, 8, 3, 30), eastern),
            AlarmTimeCalculator.nextTrigger(2, 30, emptySet(), now, eastern)
        )
    }

    @Test
    fun nextOccurrenceLaterTodayStaysOnSameDay() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 20, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 13, 23, 0)),
            AlarmTimeCalculator.nextOccurrence(23 * 3600, now, zone)
        )
    }

    @Test
    fun nextOccurrenceAlreadyPassedRollsToTomorrow() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 23, 30))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 14, 23, 0)),
            AlarmTimeCalculator.nextOccurrence(23 * 3600, now, zone)
        )
    }

    @Test
    fun nextOccurrenceExactlyNowRollsToTomorrow() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 23, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 14, 23, 0)),
            AlarmTimeCalculator.nextOccurrence(23 * 3600, now, zone)
        )
    }

    @Test
    fun skippingOneDailyAlarmLandsOnTheDayAfterNext() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 6, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 14, 7, 30)),
            AlarmTimeCalculator.nextTriggerSkippingOne(7, 30, emptySet(), now, zone)
        )
    }

    @Test
    fun skippingOneDailyAlarmWhoseTimePassedSkipsTomorrow() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 8, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 15, 7, 30)),
            AlarmTimeCalculator.nextTriggerSkippingOne(7, 30, emptySet(), now, zone)
        )
    }

    @Test
    fun skippingOneWeeklyAlarmLandsAWeekLater() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 8, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 23, 7, 30)),
            AlarmTimeCalculator.nextTriggerSkippingOne(
                7, 30, setOf(DayOfWeek.THURSDAY.value), now, zone
            )
        )
    }

    @Test
    fun skippingOneRespectsTheRepeatDaySet() {
        val now = millis(LocalDateTime.of(2026, 7, 17, 8, 0))
        val weekdays = setOf(1, 2, 3, 4, 5)
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 21, 7, 30)),
            AlarmTimeCalculator.nextTriggerSkippingOne(7, 30, weekdays, now, zone)
        )
    }

    @Test
    fun nextOccurrenceKeepsMinutePrecision() {
        val now = millis(LocalDateTime.of(2026, 7, 13, 6, 0))
        val halfPastTen = 22 * 3600 + 30 * 60
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 13, 22, 30)),
            AlarmTimeCalculator.nextOccurrence(halfPastTen, now, zone)
        )
    }
}
