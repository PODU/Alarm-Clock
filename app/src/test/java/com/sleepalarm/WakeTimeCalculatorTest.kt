package com.sleepalarm

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeTimeCalculatorTest {

    private val zone = ZoneId.of("UTC")

    private fun millis(dateTime: LocalDateTime): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun sleepNowReturnsNowRegardlessOfBedtime() {
        val now = millis(LocalDateTime.of(2026, 7, 11, 14, 30))
        assertEquals(
            now,
            WakeTimeCalculator.bedtimeMillis(true, now, LocalTime.of(23, 0), zone)
        )
    }

    @Test
    fun bedtimeLaterTodayStaysOnSameDate() {
        val now = millis(LocalDateTime.of(2026, 7, 11, 14, 30))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 11, 23, 0)),
            WakeTimeCalculator.bedtimeMillis(false, now, LocalTime.of(23, 0), zone)
        )
    }

    @Test
    fun bedtimeAlreadyPassedRollsToTomorrow() {
        val now = millis(LocalDateTime.of(2026, 7, 11, 23, 45))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 23, 0)),
            WakeTimeCalculator.bedtimeMillis(false, now, LocalTime.of(23, 0), zone)
        )
    }

    @Test
    fun earlyMorningBedtimeAfterMidnightStaysOnSameDate() {
        val now = millis(LocalDateTime.of(2026, 7, 11, 23, 45))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 1, 0)),
            WakeTimeCalculator.bedtimeMillis(false, now, LocalTime.of(1, 0), zone)
        )
    }

    @Test
    fun bedtimeExactlyNowIsKeptToday() {
        val now = millis(LocalDateTime.of(2026, 7, 11, 23, 0))
        assertEquals(
            now,
            WakeTimeCalculator.bedtimeMillis(false, now, LocalTime.of(23, 0), zone)
        )
    }

    @Test
    fun wakeMillisAddsWholeHours() {
        val bedtime = millis(LocalDateTime.of(2026, 7, 11, 23, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 7, 0)),
            WakeTimeCalculator.wakeMillis(bedtime, 8f)
        )
    }

    @Test
    fun wakeMillisHandlesHalfHourSteps() {
        val bedtime = millis(LocalDateTime.of(2026, 7, 11, 23, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 6, 30)),
            WakeTimeCalculator.wakeMillis(bedtime, 7.5f)
        )
    }

    @Test
    fun wakeMillisHandlesSliderExtremes() {
        val bedtime = millis(LocalDateTime.of(2026, 7, 11, 23, 0))
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 0, 0)),
            WakeTimeCalculator.wakeMillis(bedtime, 1f)
        )
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 11, 0)),
            WakeTimeCalculator.wakeMillis(bedtime, 12f)
        )
    }

    @Test
    fun wakeMillisIsExactAtEverySliderStep() {
        val bedtime = millis(LocalDateTime.of(2026, 7, 11, 23, 0))
        for (i in 0..22) {
            val hours = 1f + i * 0.5f
            val expectedOffset = Math.round(hours.toDouble() * 3_600_000.0)
            assertEquals(
                "wake offset for $hours h",
                bedtime + expectedOffset,
                WakeTimeCalculator.wakeMillis(bedtime, hours)
            )
        }
    }

    @Test
    fun dstSpringForwardGapIsHonouredInWallClockTerms() {
        val eastern = ZoneId.of("America/New_York")
        val bedtime = LocalDateTime.of(2026, 3, 7, 23, 0)
            .atZone(eastern).toInstant().toEpochMilli()
        val wake = WakeTimeCalculator.wakeMillis(bedtime, 8f)
        val expected = LocalDateTime.of(2026, 3, 8, 8, 0)
            .atZone(eastern).toInstant().toEpochMilli()
        assertEquals(expected, wake)
    }

    @Test
    fun cycleSuggestionsDefaultRangeYieldsFiveCounts() {
        val bedtime = millis(LocalDateTime.of(2026, 7, 11, 23, 0))
        val suggestions = WakeTimeCalculator.cycleSuggestions(bedtime)
        assertEquals(5, suggestions.size)
        assertEquals(listOf(3, 4, 5, 6, 7), suggestions.map { it.cycleCount })
    }

    @Test
    fun cycleSuggestionsApplyFallAsleepOffsetPlusNinetyMinutesPerCycle() {
        val bedtime = millis(LocalDateTime.of(2026, 7, 11, 23, 0))
        val suggestions = WakeTimeCalculator.cycleSuggestions(bedtime)
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 6, 45)),
            suggestions.first { it.cycleCount == 5 }.wakeMillis
        )
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 3, 45)),
            suggestions.first().wakeMillis
        )
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 9, 45)),
            suggestions.last().wakeMillis
        )
    }

    @Test
    fun cycleSuggestionSleepHoursExcludeTheFallAsleepOffset() {
        val bedtime = millis(LocalDateTime.of(2026, 7, 11, 23, 0))
        val suggestions = WakeTimeCalculator.cycleSuggestions(bedtime)
        assertEquals(
            listOf(4.5f, 6f, 7.5f, 9f, 10.5f),
            suggestions.map { it.sleepHours }
        )
    }

    @Test
    fun cycleSuggestionsHonourCustomOffsetAndRange() {
        val bedtime = millis(LocalDateTime.of(2026, 7, 11, 23, 0))
        val suggestions = WakeTimeCalculator.cycleSuggestions(
            bedtime, fallAsleepMinutes = 0, cycles = 1..2
        )
        assertEquals(2, suggestions.size)
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 0, 30)),
            suggestions[0].wakeMillis
        )
        assertEquals(
            millis(LocalDateTime.of(2026, 7, 12, 2, 0)),
            suggestions[1].wakeMillis
        )
    }
}
