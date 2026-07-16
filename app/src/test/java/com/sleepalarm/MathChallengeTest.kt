package com.sleepalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathChallengeTest {

    private val snoozePattern = Regex("""(\d+) \+ (\d+) = \?""")
    private val dismissPattern = Regex("""(\d+) × (\d+) \+ (\d+) = \?""")

    private val snoozeAnswerRanges = mapOf(
        MathChallenge.Difficulty.EASY to 4..38,
        MathChallenge.Difficulty.MEDIUM to 24..178,
        MathChallenge.Difficulty.HARD to 50..398
    )
    private val dismissAnswerRanges = mapOf(
        MathChallenge.Difficulty.EASY to 17..105,
        MathChallenge.Difficulty.MEDIUM to 63..360,
        MathChallenge.Difficulty.HARD to 176..1080
    )

    @Test
    fun snoozeAnswerMatchesItsQuestionAtEveryDifficulty() {
        for (difficulty in MathChallenge.Difficulty.entries) {
            repeat(500) {
                val problem = MathChallenge.forSnooze(difficulty)
                val match = snoozePattern.matchEntire(problem.question)
                assertNotNull("unexpected question format: ${problem.question}", match)
                val (a, b) = match!!.destructured
                assertEquals(a.toInt() + b.toInt(), problem.answer)
            }
        }
    }

    @Test
    fun dismissAnswerMatchesItsQuestionAtEveryDifficulty() {
        for (difficulty in MathChallenge.Difficulty.entries) {
            repeat(500) {
                val problem = MathChallenge.forDismiss(difficulty)
                val match = dismissPattern.matchEntire(problem.question)
                assertNotNull("unexpected question format: ${problem.question}", match)
                val (a, b, c) = match!!.destructured
                assertEquals(a.toInt() * b.toInt() + c.toInt(), problem.answer)
            }
        }
    }

    @Test
    fun snoozeAnswersStayInExpectedRangePerDifficulty() {
        for ((difficulty, range) in snoozeAnswerRanges) {
            repeat(500) {
                val answer = MathChallenge.forSnooze(difficulty).answer
                assertTrue("$difficulty snooze answer $answer not in $range", answer in range)
            }
        }
    }

    @Test
    fun dismissAnswersStayInExpectedRangePerDifficulty() {
        for ((difficulty, range) in dismissAnswerRanges) {
            repeat(500) {
                val answer = MathChallenge.forDismiss(difficulty).answer
                assertTrue("$difficulty dismiss answer $answer not in $range", answer in range)
            }
        }
    }

    @Test
    fun snoozeIsAdditionOnlyAndDismissUsesMultiplicationAtEveryDifficulty() {
        for (difficulty in MathChallenge.Difficulty.entries) {
            repeat(200) {
                val snooze = MathChallenge.forSnooze(difficulty).question
                assertTrue("snooze must be addition-only: $snooze", "+" in snooze)
                assertTrue("snooze must not multiply: $snooze", "×" !in snooze)

                val dismiss = MathChallenge.forDismiss(difficulty).question
                assertTrue("dismiss must multiply: $dismiss", "×" in dismiss)
            }
        }
    }

    @Test
    fun higherDifficultyRaisesTheAnswerCeiling() {
        fun maxSnooze(d: MathChallenge.Difficulty) =
            (1..2000).maxOf { MathChallenge.forSnooze(d).answer }
        fun maxDismiss(d: MathChallenge.Difficulty) =
            (1..2000).maxOf { MathChallenge.forDismiss(d).answer }

        assertTrue(maxSnooze(MathChallenge.Difficulty.EASY) < maxSnooze(MathChallenge.Difficulty.MEDIUM))
        assertTrue(maxSnooze(MathChallenge.Difficulty.MEDIUM) < maxSnooze(MathChallenge.Difficulty.HARD))
        assertTrue(maxDismiss(MathChallenge.Difficulty.EASY) < maxDismiss(MathChallenge.Difficulty.MEDIUM))
        assertTrue(maxDismiss(MathChallenge.Difficulty.MEDIUM) < maxDismiss(MathChallenge.Difficulty.HARD))
    }

    @Test
    fun defaultDifficultyIsMedium() {
        val snoozeRange = snoozeAnswerRanges.getValue(MathChallenge.Difficulty.MEDIUM)
        val dismissRange = dismissAnswerRanges.getValue(MathChallenge.Difficulty.MEDIUM)
        repeat(500) {
            assertTrue(MathChallenge.forSnooze().answer in snoozeRange)
            assertTrue(MathChallenge.forDismiss().answer in dismissRange)
        }
    }
}
