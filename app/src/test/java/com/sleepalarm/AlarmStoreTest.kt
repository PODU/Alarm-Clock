package com.sleepalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmStoreTest {

    private val oneShot = Alarm(
        id = 3,
        triggerAtMillis = 1_784_000_000_000L,
        hour = 7,
        minute = 30,
        repeatDays = emptySet(),
        enabled = true
    )

    private val repeating = Alarm(
        id = 12,
        triggerAtMillis = 1_784_100_000_000L,
        hour = 6,
        minute = 45,
        repeatDays = setOf(1, 3, 5, 7),
        enabled = false
    )

    private val fullyCustomized = Alarm(
        id = 7,
        triggerAtMillis = 1_784_200_000_000L,
        hour = 9,
        minute = 15,
        repeatDays = setOf(6, 7),
        enabled = true,
        label = "Gym | legs, not arms; 100%",
        soundUri = "content://media/external/audio/media/42?a=b|c",
        challengeType = ChallengeType.MEMORY,
        difficulty = MathChallenge.Difficulty.HARD,
        skipNext = true
    )

    @Test
    fun oneShotRoundTrips() {
        assertEquals(oneShot, AlarmStore.decode(AlarmStore.encode(oneShot)))
    }

    @Test
    fun repeatingDisabledAlarmRoundTrips() {
        assertEquals(repeating, AlarmStore.decode(AlarmStore.encode(repeating)))
    }

    @Test
    fun perAlarmOverridesRoundTrip() {
        assertEquals(fullyCustomized, AlarmStore.decode(AlarmStore.encode(fullyCustomized)))
    }

    @Test
    fun everySingleRepeatDayRoundTrips() {
        for (day in 1..7) {
            val alarm = oneShot.copy(repeatDays = setOf(day))
            assertEquals(alarm, AlarmStore.decode(AlarmStore.encode(alarm)))
        }
    }

    @Test
    fun encodingIsCanonicalRegardlessOfSetOrder() {
        val shuffled = repeating.copy(repeatDays = linkedSetOf(7, 1, 5, 3))
        assertEquals(AlarmStore.encode(repeating), AlarmStore.encode(shuffled))
    }

    @Test
    fun legacySixFieldRowDecodesWithDefaults() {
        assertEquals(oneShot, AlarmStore.decode("3|1784000000000|7|30||true"))
        assertEquals(
            repeating,
            AlarmStore.decode("12|1784100000000|6|45|1,3,5,7|false")
        )
    }

    @Test
    fun decodeRejectsMalformedInput() {
        assertNull(AlarmStore.decode(""))
        assertNull(AlarmStore.decode("not an alarm"))
        assertNull(AlarmStore.decode("3|123|7|30|true"))
        assertNull(AlarmStore.decode("3|123|7|30||true|extra"))
        assertNull(AlarmStore.decode("x|123|7|30||true"))
        assertNull(AlarmStore.decode("3|abc|7|30||true"))
        assertNull(AlarmStore.decode("3|123|7|30|1,x|true"))
        assertNull(AlarmStore.decode("3|123|7|30||maybe"))
        assertNull(AlarmStore.decode("3|123|7|30||true|||NOT_A_CHALLENGE||false"))
        assertNull(AlarmStore.decode("3|123|7|30||true||||NOT_A_DIFFICULTY|false"))
        assertNull(AlarmStore.decode("3|123|7|30||true|||||maybe"))
    }

    @Test
    fun expiredSpentOneShotsArePruned() {
        val now = 1_784_000_000_000L
        val dayMillis = 24 * 60 * 60 * 1000L
        val freshSpent = oneShot.copy(id = 1, enabled = false, triggerAtMillis = now - 1_000L)
        val staleSpent = oneShot.copy(id = 2, enabled = false, triggerAtMillis = now - dayMillis - 1)
        val pendingOneShot = oneShot.copy(id = 3, enabled = true, triggerAtMillis = now + 1_000L)
        val staleDisabledRepeat = repeating.copy(id = 4, triggerAtMillis = now - 2 * dayMillis)

        val kept = AlarmStore.withoutExpiredOneShots(
            listOf(freshSpent, staleSpent, pendingOneShot, staleDisabledRepeat),
            now
        )

        assertEquals(listOf(freshSpent, pendingOneShot, staleDisabledRepeat), kept)
    }
}
