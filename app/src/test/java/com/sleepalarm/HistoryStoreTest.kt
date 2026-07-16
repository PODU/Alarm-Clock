package com.sleepalarm

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryStoreTest {

    private val ring = HistoryEvent(1_784_000_000_000L, HistoryEvent.Type.RING)
    private val snooze = HistoryEvent(1_784_000_060_000L, HistoryEvent.Type.SNOOZE)
    private val autoSnooze = HistoryEvent(1_784_000_120_000L, HistoryEvent.Type.AUTO_SNOOZE)
    private val dismiss = HistoryEvent(1_784_000_180_000L, HistoryEvent.Type.DISMISS)

    @Test
    fun everyEventTypeRoundTrips() {
        for (event in listOf(ring, snooze, autoSnooze, dismiss)) {
            assertEquals(event, HistoryStore.decode(HistoryStore.encode(event)))
        }
    }

    @Test
    fun decodeRejectsMalformedInput() {
        assertNull(HistoryStore.decode(""))
        assertNull(HistoryStore.decode("not an event"))
        assertNull(HistoryStore.decode("123"))
        assertNull(HistoryStore.decode("123|RING|extra"))
        assertNull(HistoryStore.decode("abc|RING"))
        assertNull(HistoryStore.decode("123|NAP"))
    }

    @Test
    fun logRoundTripsThroughOneJoinedString() {
        val events = listOf(ring, snooze, autoSnooze, dismiss)
        assertEquals(events, HistoryStore.decodeAll(HistoryStore.encodeAll(events)))
    }

    @Test
    fun emptyLogRoundTrips() {
        assertEquals("", HistoryStore.encodeAll(emptyList()))
        assertEquals(emptyList<HistoryEvent>(), HistoryStore.decodeAll(""))
    }

    @Test
    fun decodeAllDropsMalformedEntriesAndSortsAscending() {
        val encoded = listOf(
            HistoryStore.encode(dismiss),
            "garbage",
            HistoryStore.encode(ring)
        ).joinToString(";")
        assertEquals(listOf(ring, dismiss), HistoryStore.decodeAll(encoded))
    }

    @Test
    fun duplicateEventsSurviveTheRoundTrip() {
        val events = listOf(ring, ring)
        assertEquals(events, HistoryStore.decodeAll(HistoryStore.encodeAll(events)))
    }

    @Test
    fun appendedKeepsOrderBelowTheCap() {
        assertEquals(listOf(ring, snooze), HistoryStore.appended(listOf(ring), snooze))
    }

    @Test
    fun appendedPrunesToTheMostRecentEvents() {
        val old = (0 until HistoryStore.MAX_EVENTS).map {
            HistoryEvent(1_000L + it, HistoryEvent.Type.RING)
        }
        val newest = HistoryEvent(999_999L, HistoryEvent.Type.DISMISS)
        val pruned = HistoryStore.appended(old, newest)
        assertEquals(HistoryStore.MAX_EVENTS, pruned.size)
        assertEquals(old[1], pruned.first())
        assertEquals(newest, pruned.last())
    }
}

class HistoryStatsTest {

    private companion object {
        const val NOW = 1_784_000_000_000L
        const val MINUTE = 60_000L
    }

    private fun ring(at: Long) = HistoryEvent(at, HistoryEvent.Type.RING)
    private fun snooze(at: Long) = HistoryEvent(at, HistoryEvent.Type.SNOOZE)
    private fun autoSnooze(at: Long) = HistoryEvent(at, HistoryEvent.Type.AUTO_SNOOZE)
    private fun dismiss(at: Long) = HistoryEvent(at, HistoryEvent.Type.DISMISS)

    @Test
    fun emptyLogYieldsZeroesAndNoAverage() {
        val summary = HistoryStats.summarize(emptyList(), NOW)
        assertEquals(0, summary.ringsLastWeek)
        assertEquals(0, summary.snoozesLastWeek)
        assertEquals(0, summary.dismissesLastWeek)
        assertNull(summary.averageDismissMillis)
        assertEquals(0, summary.longestSnoozeStreak)
    }

    @Test
    fun weeklyCountsIgnoreEventsOlderThanSevenDays() {
        val old = NOW - HistoryStats.WEEK_MILLIS - MINUTE
        val events = listOf(
            ring(old), snooze(old + 1), dismiss(old + 2),
            ring(NOW - MINUTE), dismiss(NOW)
        ).sortedBy { it.epochMillis }
        val summary = HistoryStats.summarize(events, NOW)
        assertEquals(1, summary.ringsLastWeek)
        assertEquals(0, summary.snoozesLastWeek)
        assertEquals(1, summary.dismissesLastWeek)
    }

    @Test
    fun weeklySnoozesIncludeAutoSnoozes() {
        val events = listOf(
            ring(NOW - 3 * MINUTE),
            snooze(NOW - 2 * MINUTE),
            autoSnooze(NOW - MINUTE)
        )
        assertEquals(2, HistoryStats.summarize(events, NOW).snoozesLastWeek)
    }

    @Test
    fun averagePairsEachRingWithItsFirstDismiss() {
        val events = listOf(
            ring(0), dismiss(2 * MINUTE),
            ring(10 * MINUTE), dismiss(14 * MINUTE)
        )
        assertEquals(3 * MINUTE, HistoryStats.averageDismissMillis(events))
    }

    @Test
    fun averageAnchorsASnoozedSessionOnItsOpeningRing() {
        val events = listOf(
            ring(0), snooze(MINUTE), ring(6 * MINUTE), dismiss(8 * MINUTE)
        )
        assertEquals(8 * MINUTE, HistoryStats.averageDismissMillis(events))
    }

    @Test
    fun averageSkipsAnUnpairedTrailingRing() {
        val events = listOf(
            ring(0), dismiss(MINUTE),
            ring(10 * MINUTE)
        )
        assertEquals(MINUTE, HistoryStats.averageDismissMillis(events))
    }

    @Test
    fun averageSkipsADismissWithNoOpenRing() {
        assertNull(HistoryStats.averageDismissMillis(listOf(dismiss(0))))
        val events = listOf(dismiss(0), ring(MINUTE), dismiss(2 * MINUTE))
        assertEquals(MINUTE, HistoryStats.averageDismissMillis(events))
    }

    @Test
    fun averageIsNullWhenOnlyUnpairedRingsExist() {
        assertNull(HistoryStats.averageDismissMillis(listOf(ring(0), ring(MINUTE))))
    }

    @Test
    fun streakCountsSnoozesAndAutoSnoozesAcrossReRings() {
        val events = listOf(
            ring(0), snooze(1), ring(2), autoSnooze(3), ring(4), snooze(5),
            dismiss(6)
        )
        assertEquals(3, HistoryStats.longestSnoozeStreak(events))
    }

    @Test
    fun streakResetsAtDismissBetweenSessions() {
        val events = listOf(
            ring(0), snooze(1), snooze(2), dismiss(3),
            ring(4), snooze(5), dismiss(6)
        )
        assertEquals(2, HistoryStats.longestSnoozeStreak(events))
    }

    @Test
    fun streakIsZeroWithoutAnySnooze() {
        assertEquals(0, HistoryStats.longestSnoozeStreak(listOf(ring(0), dismiss(1))))
    }

    @Test
    fun formatsMillisAsMinutesAndPaddedSeconds() {
        assertEquals("0:00", HistoryStats.formatMinutesSeconds(0))
        assertEquals("0:59", HistoryStats.formatMinutesSeconds(59_999))
        assertEquals("1:35", HistoryStats.formatMinutesSeconds(95_000))
        assertEquals("12:05", HistoryStats.formatMinutesSeconds(725_000))
    }


    private val utc = ZoneId.of("UTC")

    private fun utcMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(utc).toInstant().toEpochMilli()

    @Test
    fun consistencyIsNullWithFewerThanTwoRings() {
        val now = utcMillis(LocalDateTime.of(2026, 7, 14, 8, 0))
        assertNull(HistoryStats.wakeConsistencyMillis(emptyList(), now, utc))
        assertNull(
            HistoryStats.wakeConsistencyMillis(
                listOf(ring(utcMillis(LocalDateTime.of(2026, 7, 14, 7, 0)))),
                now,
                utc
            )
        )
    }

    @Test
    fun consistencyIsAverageDeviationFromTheMeanTimeOfDay() {
        val events = listOf(
            ring(utcMillis(LocalDateTime.of(2026, 7, 13, 7, 0))),
            ring(utcMillis(LocalDateTime.of(2026, 7, 14, 7, 30)))
        )
        val now = utcMillis(LocalDateTime.of(2026, 7, 14, 8, 0))
        assertEquals(
            15 * MINUTE,
            HistoryStats.wakeConsistencyMillis(events, now, utc)!!
        )
    }

    @Test
    fun consistencyIsZeroForIdenticalWakeTimes() {
        val events = listOf(
            ring(utcMillis(LocalDateTime.of(2026, 7, 13, 7, 0))),
            ring(utcMillis(LocalDateTime.of(2026, 7, 14, 7, 0)))
        )
        val now = utcMillis(LocalDateTime.of(2026, 7, 14, 8, 0))
        assertEquals(0L, HistoryStats.wakeConsistencyMillis(events, now, utc)!!)
    }

    @Test
    fun consistencyIgnoresRingsOlderThanAWeek() {
        val events = listOf(
            ring(utcMillis(LocalDateTime.of(2026, 7, 1, 3, 0))),
            ring(utcMillis(LocalDateTime.of(2026, 7, 13, 7, 0))),
            ring(utcMillis(LocalDateTime.of(2026, 7, 14, 7, 30)))
        )
        val now = utcMillis(LocalDateTime.of(2026, 7, 14, 8, 0))
        assertEquals(
            15 * MINUTE,
            HistoryStats.wakeConsistencyMillis(events, now, utc)!!
        )
    }


    @Test
    fun cleanSessionsCountFromTheNewestBackward() {
        val events = listOf(
            ring(0), snooze(1), dismiss(2),
            ring(10), dismiss(11),
            ring(20), dismiss(21)
        )
        assertEquals(2, HistoryStats.currentNoSnoozeStreak(events))
    }

    @Test
    fun streakIsZeroWhenTheLatestSessionSnoozed() {
        val events = listOf(
            ring(0), dismiss(1),
            ring(10), snooze(11), dismiss(12)
        )
        assertEquals(0, HistoryStats.currentNoSnoozeStreak(events))
    }

    @Test
    fun autoSnoozeBreaksTheStreakLikeAManualOne() {
        val events = listOf(ring(0), autoSnooze(1), dismiss(2))
        assertEquals(0, HistoryStats.currentNoSnoozeStreak(events))
    }

    @Test
    fun openSessionWithoutDismissDoesNotCount() {
        val events = listOf(ring(0), dismiss(1), ring(10))
        assertEquals(1, HistoryStats.currentNoSnoozeStreak(events))
        val snoozedOpen = listOf(ring(0), dismiss(1), ring(10), snooze(11))
        assertEquals(1, HistoryStats.currentNoSnoozeStreak(snoozedOpen))
    }

    @Test
    fun streakIsZeroOnAnEmptyLog() {
        assertEquals(0, HistoryStats.currentNoSnoozeStreak(emptyList()))
    }

    @Test
    fun summarizeCarriesTheNewStats() {
        val events = listOf(
            ring(utcMillis(LocalDateTime.of(2026, 7, 13, 7, 0))),
            dismiss(utcMillis(LocalDateTime.of(2026, 7, 13, 7, 1))),
            ring(utcMillis(LocalDateTime.of(2026, 7, 14, 7, 30))),
            dismiss(utcMillis(LocalDateTime.of(2026, 7, 14, 7, 31)))
        )
        val now = utcMillis(LocalDateTime.of(2026, 7, 14, 8, 0))
        val summary = HistoryStats.summarize(events, now, utc)
        assertEquals(15 * MINUTE, summary.wakeConsistencyMillis!!)
        assertEquals(2, summary.noSnoozeStreak)
    }
}
