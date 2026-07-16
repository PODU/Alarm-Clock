package com.sleepalarm

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengesTest {


    @Test
    fun pickPhraseIsDeterministicGivenTheSameSeed() {
        val phrases = listOf("alpha", "bravo", "charlie", "delta")
        val first = Challenges.pickPhrase(phrases, Random(42))
        val second = Challenges.pickPhrase(phrases, Random(42))
        assertEquals(first, second)
    }

    @Test
    fun pickPhraseAlwaysReturnsAnElementOfTheList() {
        val phrases = listOf("one", "two", "three")
        repeat(200) {
            assertTrue(Challenges.pickPhrase(phrases) in phrases)
        }
    }

    @Test
    fun pickPhraseFromSingletonListReturnsIt() {
        assertEquals("only", Challenges.pickPhrase(listOf("only")))
    }

    @Test
    fun pickPhraseCoversTheWholeListOverManyDraws() {
        val phrases = listOf("a", "b", "c")
        val seen = mutableSetOf<String>()
        val random = Random(7)
        repeat(300) { seen += Challenges.pickPhrase(phrases, random) }
        assertEquals(phrases.toSet(), seen)
    }

    @Test
    fun typingMatchIsExactButForgivesEdgeWhitespace() {
        assertTrue(Challenges.typingMatches("Good morning", "Good morning"))
        assertTrue(Challenges.typingMatches("Good morning", "  Good morning \n"))
        assertFalse(Challenges.typingMatches("Good morning", "good morning"))
        assertFalse(Challenges.typingMatches("Good morning", "Goodmorning"))
        assertFalse(Challenges.typingMatches("Good morning", "Good  morning"))
        assertFalse(Challenges.typingMatches("Good morning", ""))
    }


    @Test
    fun memorySequenceLengthGrowsWithDifficulty() {
        assertEquals(4, Challenges.memoryDigits(MathChallenge.Difficulty.EASY))
        assertEquals(6, Challenges.memoryDigits(MathChallenge.Difficulty.MEDIUM))
        assertEquals(8, Challenges.memoryDigits(MathChallenge.Difficulty.HARD))
        for (difficulty in MathChallenge.Difficulty.entries) {
            repeat(100) {
                val sequence = Challenges.memorySequence(difficulty)
                assertEquals(Challenges.memoryDigits(difficulty), sequence.length)
                assertTrue(
                    "non-digit in $sequence",
                    sequence.all { it.isDigit() }
                )
            }
        }
    }

    @Test
    fun memorySequenceIsDeterministicGivenTheSameSeed() {
        for (difficulty in MathChallenge.Difficulty.entries) {
            assertEquals(
                Challenges.memorySequence(difficulty, Random(99)),
                Challenges.memorySequence(difficulty, Random(99))
            )
        }
    }

    @Test
    fun memoryShowTimeShrinksWithDifficulty() {
        assertEquals(5_000L, Challenges.memoryShowMillis(MathChallenge.Difficulty.EASY))
        assertEquals(4_000L, Challenges.memoryShowMillis(MathChallenge.Difficulty.MEDIUM))
        assertEquals(3_000L, Challenges.memoryShowMillis(MathChallenge.Difficulty.HARD))
    }


    @Test
    fun shakeCountsPerDifficulty() {
        assertEquals(10, Challenges.shakesForDismiss(MathChallenge.Difficulty.EASY))
        assertEquals(20, Challenges.shakesForDismiss(MathChallenge.Difficulty.MEDIUM))
        assertEquals(30, Challenges.shakesForDismiss(MathChallenge.Difficulty.HARD))
    }

    @Test
    fun snoozeShakesAreHalfOfDismissRoundedUp() {
        for (difficulty in MathChallenge.Difficulty.entries) {
            val dismiss = Challenges.shakesForDismiss(difficulty)
            val expected = (dismiss + 1) / 2
            assertEquals(expected, Challenges.shakesForSnooze(difficulty))
        }
        assertEquals(5, Challenges.shakesForSnooze(MathChallenge.Difficulty.EASY))
        assertEquals(10, Challenges.shakesForSnooze(MathChallenge.Difficulty.MEDIUM))
        assertEquals(15, Challenges.shakesForSnooze(MathChallenge.Difficulty.HARD))
    }

    @Test
    fun shakeThresholdIsGravityPlusTwelve() {
        assertFalse(Challenges.isShake(Challenges.GRAVITY_MPS2))
        assertFalse(
            Challenges.isShake(
                Challenges.GRAVITY_MPS2 + Challenges.SHAKE_THRESHOLD_MPS2 - 0.1f
            )
        )
        assertTrue(
            Challenges.isShake(
                Challenges.GRAVITY_MPS2 + Challenges.SHAKE_THRESHOLD_MPS2 + 0.1f
            )
        )
        assertTrue(Challenges.isShake(30f))
    }


    @Test
    fun stepCountsPerDifficulty() {
        assertEquals(10, Challenges.stepsForDismiss(MathChallenge.Difficulty.EASY))
        assertEquals(20, Challenges.stepsForDismiss(MathChallenge.Difficulty.MEDIUM))
        assertEquals(40, Challenges.stepsForDismiss(MathChallenge.Difficulty.HARD))
    }

    @Test
    fun snoozeStepsAreHalfOfDismissRoundedUp() {
        for (difficulty in MathChallenge.Difficulty.entries) {
            val dismiss = Challenges.stepsForDismiss(difficulty)
            assertEquals((dismiss + 1) / 2, Challenges.stepsForSnooze(difficulty))
        }
        assertEquals(5, Challenges.stepsForSnooze(MathChallenge.Difficulty.EASY))
        assertEquals(10, Challenges.stepsForSnooze(MathChallenge.Difficulty.MEDIUM))
        assertEquals(20, Challenges.stepsForSnooze(MathChallenge.Difficulty.HARD))
    }


    @Test
    fun zeroMaxSnoozesMeansUnlimited() {
        assertTrue(Challenges.canSnooze(0, 0))
        assertTrue(Challenges.canSnooze(0, 100))
    }

    @Test
    fun snoozeAllowedOnlyBelowTheCap() {
        assertTrue(Challenges.canSnooze(3, 0))
        assertTrue(Challenges.canSnooze(3, 2))
        assertFalse(Challenges.canSnooze(3, 3))
        assertFalse(Challenges.canSnooze(3, 4))
        assertFalse(Challenges.canSnooze(1, 1))
    }
}
