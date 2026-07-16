package com.sleepalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSettingsCodecTest {

    @Test
    fun emptyMapRoundTrips() {
        assertTrue(
            AlarmSettings.decodeSnoozeTargets(
                AlarmSettings.encodeSnoozeTargets(emptyMap())
            ).isEmpty()
        )
    }

    @Test
    fun multipleTargetsRoundTrip() {
        val targets = mapOf(1 to 1_784_000_000_000L, 7 to 1_784_000_300_000L, 0 to 5L)
        assertEquals(
            targets,
            AlarmSettings.decodeSnoozeTargets(AlarmSettings.encodeSnoozeTargets(targets))
        )
    }

    @Test
    fun encodingIsCanonicalRegardlessOfMapOrder() {
        val a = linkedMapOf(3 to 30L, 1 to 10L)
        val b = linkedMapOf(1 to 10L, 3 to 30L)
        assertEquals(
            AlarmSettings.encodeSnoozeTargets(a),
            AlarmSettings.encodeSnoozeTargets(b)
        )
    }

    @Test
    fun malformedPairsAreDropped() {
        assertEquals(
            mapOf(2 to 20L),
            AlarmSettings.decodeSnoozeTargets("garbage;2:20;3:x;:5;4:")
        )
    }
}
