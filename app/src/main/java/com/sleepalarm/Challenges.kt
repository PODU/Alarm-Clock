package com.sleepalarm

import kotlin.random.Random

enum class ChallengeType { MATH, TYPING, MEMORY, SHAKE, STEPS }

object Challenges {


    fun pickPhrase(phrases: List<String>, random: Random = Random.Default): String {
        require(phrases.isNotEmpty()) { "phrase list must not be empty" }
        return phrases[random.nextInt(phrases.size)]
    }

    fun typingMatches(expected: String, input: String): Boolean =
        input.trim() == expected.trim()


    fun memoryDigits(difficulty: MathChallenge.Difficulty): Int = when (difficulty) {
        MathChallenge.Difficulty.EASY -> 4
        MathChallenge.Difficulty.MEDIUM -> 6
        MathChallenge.Difficulty.HARD -> 8
    }

    fun memoryShowMillis(difficulty: MathChallenge.Difficulty): Long = when (difficulty) {
        MathChallenge.Difficulty.EASY -> 5_000L
        MathChallenge.Difficulty.MEDIUM -> 4_000L
        MathChallenge.Difficulty.HARD -> 3_000L
    }

    fun memorySequence(
        difficulty: MathChallenge.Difficulty,
        random: Random = Random.Default
    ): String = buildString {
        repeat(memoryDigits(difficulty)) { append(random.nextInt(10)) }
    }


    const val GRAVITY_MPS2 = 9.81f

    const val SHAKE_THRESHOLD_MPS2 = 12f

    const val SHAKE_DEBOUNCE_MILLIS = 300L

    fun isShake(accelerationMagnitude: Float): Boolean =
        accelerationMagnitude - GRAVITY_MPS2 > SHAKE_THRESHOLD_MPS2

    fun shakesForDismiss(difficulty: MathChallenge.Difficulty): Int = when (difficulty) {
        MathChallenge.Difficulty.EASY -> 10
        MathChallenge.Difficulty.MEDIUM -> 20
        MathChallenge.Difficulty.HARD -> 30
    }

    fun shakesForSnooze(difficulty: MathChallenge.Difficulty): Int =
        (shakesForDismiss(difficulty) + 1) / 2


    fun stepsForDismiss(difficulty: MathChallenge.Difficulty): Int = when (difficulty) {
        MathChallenge.Difficulty.EASY -> 10
        MathChallenge.Difficulty.MEDIUM -> 20
        MathChallenge.Difficulty.HARD -> 40
    }

    fun stepsForSnooze(difficulty: MathChallenge.Difficulty): Int =
        (stepsForDismiss(difficulty) + 1) / 2


    fun canSnooze(maxSnoozes: Int, snoozesUsed: Int): Boolean =
        maxSnoozes == 0 || snoozesUsed < maxSnoozes
}
