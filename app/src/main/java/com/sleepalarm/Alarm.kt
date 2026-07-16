package com.sleepalarm

data class Alarm(
    val id: Int,
    val triggerAtMillis: Long,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<Int>,
    val enabled: Boolean,
    val label: String = "",
    val soundUri: String? = null,
    val challengeType: ChallengeType? = null,
    val difficulty: MathChallenge.Difficulty? = null,
    val skipNext: Boolean = false
)
