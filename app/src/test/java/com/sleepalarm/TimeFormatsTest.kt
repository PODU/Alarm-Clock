package com.sleepalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatsTest {

    @Test
    fun minutesOnlyUnderAnHour() {
        assertEquals("12m", TimeFormats.formatCountdown(0L, 12 * 60_000L))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals("7h 32m", TimeFormats.formatCountdown(0L, (7 * 60 + 32) * 60_000L))
    }

    @Test
    fun partialMinutesRoundUp() {
        assertEquals("2m", TimeFormats.formatCountdown(0L, 61_000L))
    }

    @Test
    fun pastTargetsClampToZero() {
        assertEquals("0m", TimeFormats.formatCountdown(10_000L, 0L))
    }

    @Test
    fun exactHourShowsZeroMinutes() {
        assertEquals("2h 0m", TimeFormats.formatCountdown(0L, 2 * 3_600_000L))
    }
}
