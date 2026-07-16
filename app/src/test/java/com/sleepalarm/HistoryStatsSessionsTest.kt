package com.sleepalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStatsSessionsTest {

    private fun event(minute: Int, type: HistoryEvent.Type) =
        HistoryEvent(minute * 60_000L, type)

    @Test
    fun emptyLogHasNoSessions() {
        assertTrue(HistoryStats.sessions(emptyList()).isEmpty())
    }

    @Test
    fun cleanDismissMakesOneSnoozelessSession() {
        val sessions = HistoryStats.sessions(
            listOf(
                event(0, HistoryEvent.Type.RING),
                event(2, HistoryEvent.Type.DISMISS)
            )
        )
        assertEquals(
            listOf(HistoryStats.WakeSession(2 * 60_000L, snoozes = 0, autoSnoozes = 0)),
            sessions
        )
    }

    @Test
    fun snoozeReRingsStayInOneSession() {
        val sessions = HistoryStats.sessions(
            listOf(
                event(0, HistoryEvent.Type.RING),
                event(1, HistoryEvent.Type.SNOOZE),
                event(10, HistoryEvent.Type.RING),
                event(11, HistoryEvent.Type.AUTO_SNOOZE),
                event(20, HistoryEvent.Type.RING),
                event(21, HistoryEvent.Type.DISMISS)
            )
        )
        assertEquals(
            listOf(HistoryStats.WakeSession(21 * 60_000L, snoozes = 2, autoSnoozes = 1)),
            sessions
        )
    }

    @Test
    fun dismissClosesSessionAndResetsCounts() {
        val sessions = HistoryStats.sessions(
            listOf(
                event(0, HistoryEvent.Type.RING),
                event(1, HistoryEvent.Type.SNOOZE),
                event(9, HistoryEvent.Type.DISMISS),
                event(100, HistoryEvent.Type.RING),
                event(102, HistoryEvent.Type.DISMISS)
            )
        )
        assertEquals(
            listOf(
                HistoryStats.WakeSession(9 * 60_000L, snoozes = 1, autoSnoozes = 0),
                HistoryStats.WakeSession(102 * 60_000L, snoozes = 0, autoSnoozes = 0)
            ),
            sessions
        )
    }

    @Test
    fun openSessionWithoutDismissIsNotEmitted() {
        val sessions = HistoryStats.sessions(
            listOf(
                event(0, HistoryEvent.Type.RING),
                event(1, HistoryEvent.Type.SNOOZE)
            )
        )
        assertTrue(sessions.isEmpty())
    }
}
