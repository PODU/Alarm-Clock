package com.sleepalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BootReceiverTest {

    private val now = 1_000_000L

    @Test
    fun noStoredTargetRestoresNothing() {
        assertNull(BootReceiver.restoredSnoozeTarget(0L, now))
    }

    @Test
    fun negativeStoredTargetRestoresNothing() {
        assertNull(BootReceiver.restoredSnoozeTarget(-5L, now))
    }

    @Test
    fun futureTargetIsKeptAsScheduled() {
        assertEquals(
            now + 120_000L,
            BootReceiver.restoredSnoozeTarget(now + 120_000L, now)
        )
    }

    @Test
    fun targetThatPassedDuringDowntimeMovesOneMinuteOut() {
        assertEquals(
            now + 60_000L,
            BootReceiver.restoredSnoozeTarget(now - 1L, now)
        )
    }

    @Test
    fun targetExactlyAtNowIsTreatedAsPassed() {
        assertEquals(
            now + 60_000L,
            BootReceiver.restoredSnoozeTarget(now, now)
        )
    }
}
