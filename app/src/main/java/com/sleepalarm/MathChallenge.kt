package com.sleepalarm

data class MathProblem(val question: String, val answer: Int) : java.io.Serializable

object MathChallenge {

    enum class Difficulty { EASY, MEDIUM, HARD }

    fun forSnooze(difficulty: Difficulty = Difficulty.MEDIUM): MathProblem {
        val range = when (difficulty) {
            Difficulty.EASY -> 2..19
            Difficulty.MEDIUM -> 12..89
            Difficulty.HARD -> 25..199
        }
        val a = range.random()
        val b = range.random()
        return MathProblem("$a + $b = ?", a + b)
    }

    fun forDismiss(difficulty: Difficulty = Difficulty.MEDIUM): MathProblem {
        val (aRange, bRange, cRange) = when (difficulty) {
            Difficulty.EASY -> Triple(6..15, 2..5, 5..30)
            Difficulty.MEDIUM -> Triple(13..29, 4..9, 11..99)
            Difficulty.HARD -> Triple(21..60, 6..13, 50..300)
        }
        val a = aRange.random()
        val b = bRange.random()
        val c = cRange.random()
        return MathProblem("$a × $b + $c = ?", a * b + c)
    }
}
