package com.sleepalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickAlarmTest {

    private fun alarm(id: Int, triggerAtMillis: Long, enabled: Boolean = true) =
        Alarm(id, triggerAtMillis, 7, 0, emptySet(), enabled)

    @Test
    fun picksEarliestFutureEnabledAlarm() {
        val alarms = listOf(alarm(1, 3_000L), alarm(2, 2_000L), alarm(3, 4_000L))
        assertEquals(2_000L, QuickAlarm.nextEnabledTrigger(alarms, 1_000L))
    }

    @Test
    fun skipsDisabledAlarms() {
        val alarms = listOf(alarm(1, 2_000L, enabled = false), alarm(2, 3_000L))
        assertEquals(3_000L, QuickAlarm.nextEnabledTrigger(alarms, 1_000L))
    }

    @Test
    fun skipsAlreadyPassedTriggers() {
        val alarms = listOf(alarm(1, 1_000L), alarm(2, 2_000L))
        assertEquals(2_000L, QuickAlarm.nextEnabledTrigger(alarms, 1_500L))
    }

    @Test
    fun triggerExactlyAtNowIsNotFuture() {
        assertNull(QuickAlarm.nextEnabledTrigger(listOf(alarm(1, 1_000L)), 1_000L))
    }

    @Test
    fun nullWhenNoAlarms() {
        assertNull(QuickAlarm.nextEnabledTrigger(emptyList(), 1_000L))
    }

    @Test
    fun nullWhenNothingPendingAndEnabled() {
        val alarms = listOf(alarm(1, 500L), alarm(2, 5_000L, enabled = false))
        assertNull(QuickAlarm.nextEnabledTrigger(alarms, 1_000L))
    }

    @Test
    fun nextEnabledAlarmReturnsTheWholeAlarm() {
        val earliest = alarm(2, 2_000L)
        val alarms = listOf(alarm(1, 3_000L), earliest, alarm(3, 4_000L))
        assertEquals(earliest, QuickAlarm.nextEnabledAlarm(alarms, 1_000L))
    }

    @Test
    fun nextEnabledAlarmIsNullWhenNothingPending() {
        assertNull(QuickAlarm.nextEnabledAlarm(emptyList(), 1_000L))
        assertNull(
            QuickAlarm.nextEnabledAlarm(
                listOf(alarm(1, 500L), alarm(2, 5_000L, enabled = false)),
                1_000L
            )
        )
    }
}
