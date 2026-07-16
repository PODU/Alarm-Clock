package com.sleepalarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSchedulerTest {

    @Test
    fun escalationOffReturnsTheBaseLength() {
        assertEquals(5, AlarmScheduler.escalatedSnoozeMinutes(5, 0, 3))
        assertEquals(15, AlarmScheduler.escalatedSnoozeMinutes(15, 0, 0))
    }

    @Test
    fun negativeEscalationIsTreatedAsOff() {
        assertEquals(10, AlarmScheduler.escalatedSnoozeMinutes(10, -3, 4))
    }

    @Test
    fun firstSnoozeGetsThePlainBaseLength() {
        assertEquals(5, AlarmScheduler.escalatedSnoozeMinutes(5, 5, 0))
    }

    @Test
    fun eachPriorSnoozeAddsOneEscalationStep() {
        assertEquals(10, AlarmScheduler.escalatedSnoozeMinutes(5, 5, 1))
        assertEquals(15, AlarmScheduler.escalatedSnoozeMinutes(5, 5, 2))
        assertEquals(11, AlarmScheduler.escalatedSnoozeMinutes(5, 2, 3))
        assertEquals(16, AlarmScheduler.escalatedSnoozeMinutes(15, 1, 1))
    }

    @Test
    fun totalIsCappedAtSixtyMinutes() {
        assertEquals(
            AlarmScheduler.MAX_ESCALATED_SNOOZE_MINUTES,
            AlarmScheduler.escalatedSnoozeMinutes(15, 10, 20)
        )
        assertEquals(
            AlarmScheduler.MAX_ESCALATED_SNOOZE_MINUTES,
            AlarmScheduler.escalatedSnoozeMinutes(5, 60, 1)
        )
        assertEquals(59, AlarmScheduler.escalatedSnoozeMinutes(5, 6, 9))
    }

    @Test
    fun negativePriorCountIsTreatedAsZero() {
        assertEquals(5, AlarmScheduler.escalatedSnoozeMinutes(5, 5, -1))
    }
}
