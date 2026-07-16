package com.sleepalarm

import android.content.Context

enum class SleepProfile { WEEKDAY, WEEKEND }

class AlarmSettings(context: Context) {

    companion object {
        const val PREFS_NAME = "sleep_alarm_prefs"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        private const val KEY_DIFFICULTY = "math_difficulty"
        private const val KEY_BEDTIME_SECOND_OF_DAY = "bedtime_second_of_day"
        private const val KEY_SLEEP_HOURS = "sleep_hours"
        private const val KEY_SLEEP_NOW = "sleep_now"
        private const val KEY_BEDTIME_REMINDER_ENABLED = "bedtime_reminder_enabled"
        private const val KEY_CHALLENGE_TYPE = "challenge_type"
        private const val KEY_WAKE_UP_CHECK_MINUTES = "wake_up_check_minutes"
        private const val KEY_WAKE_CHECK_PENDING = "wake_check_pending"
        private const val KEY_LAST_RING_FROM_WAKE_CHECK = "last_ring_from_wake_check"
        private const val KEY_MAX_SNOOZES = "max_snoozes"
        private const val KEY_SNOOZE_COUNT = "snooze_count"
        private const val KEY_SNOOZE_TARGETS = "snooze_targets"
        private const val LEGACY_KEY_SNOOZE_TARGET_MILLIS = "snooze_target_millis"
        private const val KEY_MAX_RING_MINUTES = "max_ring_minutes"
        private const val KEY_VIBRATE_ENABLED = "vibrate_enabled"
        private const val KEY_VOLUME_RAMP_SECONDS = "volume_ramp_seconds"
        private const val KEY_ALARM_SOUND_URI = "alarm_sound_uri"
        private const val KEY_RINGING_ALARM_ID = "ringing_alarm_id"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_BEDTIME_WEEKEND = "bedtime_second_of_day_weekend"
        private const val KEY_SLEEP_HOURS_WEEKEND = "sleep_hours_weekend"
        private const val KEY_BATTERY_PROMPT_SHOWN = "battery_prompt_shown"
        private const val KEY_SNOOZE_ESCALATION_MINUTES = "snooze_escalation_minutes"
        private const val KEY_GENTLE_WAKE_MINUTES = "gentle_wake_minutes"
        private const val KEY_FALL_ASLEEP_MINUTES = "fall_asleep_minutes"
        const val DEFAULT_SNOOZE_MINUTES = 5
        const val DEFAULT_BEDTIME_SECOND_OF_DAY = 23 * 3600
        const val DEFAULT_SLEEP_HOURS = 8f
        const val DEFAULT_MAX_RING_MINUTES = 10
        const val DEFAULT_VOLUME_RAMP_SECONDS = 60
        val SNOOZE_CHOICES = listOf(5, 10, 15)
        val WAKE_UP_CHECK_CHOICES = listOf(0, 3, 5, 10)
        val MAX_SNOOZE_CHOICES = listOf(0, 1, 2, 3)
        val MAX_RING_CHOICES = listOf(5, 10, 15)
        val VOLUME_RAMP_CHOICES = listOf(0, 30, 60, 120)
        val SNOOZE_ESCALATION_CHOICES = listOf(0, 1, 2, 5)
        val GENTLE_WAKE_CHOICES = listOf(0, 5, 10, 15)
        val FALL_ASLEEP_CHOICES = listOf(10, 15, 20, 30)

        private val snoozeTargetsLock = Any()


        fun encodeSnoozeTargets(targets: Map<Int, Long>): String =
            targets.entries
                .sortedBy { it.key }
                .joinToString(";") { "${it.key}:${it.value}" }

        fun decodeSnoozeTargets(encoded: String): Map<Int, Long> =
            if (encoded.isEmpty()) {
                emptyMap()
            } else {
                encoded.split(";").mapNotNull { pair ->
                    val fields = pair.split(":")
                    if (fields.size != 2) return@mapNotNull null
                    val id = fields[0].toIntOrNull() ?: return@mapNotNull null
                    val millis = fields[1].toLongOrNull() ?: return@mapNotNull null
                    id to millis
                }.toMap()
            }
    }

    private val prefs = Prefs.sharedPrefs(context)

    var snoozeMinutes: Int
        get() = prefs.getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)
        set(value) = prefs.edit().putInt(KEY_SNOOZE_MINUTES, value).apply()

    var difficulty: MathChallenge.Difficulty
        get() = prefs.getString(KEY_DIFFICULTY, null)
            ?.let { stored -> MathChallenge.Difficulty.entries.firstOrNull { it.name == stored } }
            ?: MathChallenge.Difficulty.MEDIUM
        set(value) = prefs.edit().putString(KEY_DIFFICULTY, value.name).apply()

    var activeProfile: SleepProfile
        get() = prefs.getString(KEY_ACTIVE_PROFILE, null)
            ?.let { stored -> SleepProfile.entries.firstOrNull { it.name == stored } }
            ?: SleepProfile.WEEKDAY
        set(value) = prefs.edit().putString(KEY_ACTIVE_PROFILE, value.name).apply()

    var bedtimeSecondOfDay: Int
        get() = prefs.getInt(bedtimeKey(), DEFAULT_BEDTIME_SECOND_OF_DAY)
        set(value) = prefs.edit().putInt(bedtimeKey(), value).apply()

    var sleepHours: Float
        get() = prefs.getFloat(sleepHoursKey(), DEFAULT_SLEEP_HOURS)
        set(value) = prefs.edit().putFloat(sleepHoursKey(), value).apply()

    private fun bedtimeKey() = when (activeProfile) {
        SleepProfile.WEEKDAY -> KEY_BEDTIME_SECOND_OF_DAY
        SleepProfile.WEEKEND -> KEY_BEDTIME_WEEKEND
    }

    private fun sleepHoursKey() = when (activeProfile) {
        SleepProfile.WEEKDAY -> KEY_SLEEP_HOURS
        SleepProfile.WEEKEND -> KEY_SLEEP_HOURS_WEEKEND
    }

    var sleepNow: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_NOW, true)
        set(value) = prefs.edit().putBoolean(KEY_SLEEP_NOW, value).apply()

    var bedtimeReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_BEDTIME_REMINDER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BEDTIME_REMINDER_ENABLED, value).apply()

    var challengeType: ChallengeType
        get() = prefs.getString(KEY_CHALLENGE_TYPE, null)
            ?.let { stored -> ChallengeType.entries.firstOrNull { it.name == stored } }
            ?: ChallengeType.MATH
        set(value) = prefs.edit().putString(KEY_CHALLENGE_TYPE, value.name).apply()

    var wakeUpCheckMinutes: Int
        get() = prefs.getInt(KEY_WAKE_UP_CHECK_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_WAKE_UP_CHECK_MINUTES, value).apply()

    var wakeCheckPending: Boolean
        get() = prefs.getBoolean(KEY_WAKE_CHECK_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_CHECK_PENDING, value).apply()

    var lastRingFromWakeCheck: Boolean
        get() = prefs.getBoolean(KEY_LAST_RING_FROM_WAKE_CHECK, false)
        set(value) = prefs.edit().putBoolean(KEY_LAST_RING_FROM_WAKE_CHECK, value).apply()

    var ringingAlarmId: Int
        get() = prefs.getInt(KEY_RINGING_ALARM_ID, 0)
        set(value) = prefs.edit().putInt(KEY_RINGING_ALARM_ID, value).apply()

    var maxSnoozes: Int
        get() = prefs.getInt(KEY_MAX_SNOOZES, 0)
        set(value) = prefs.edit().putInt(KEY_MAX_SNOOZES, value).apply()

    var snoozeCount: Int
        get() = prefs.getInt(KEY_SNOOZE_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_SNOOZE_COUNT, value).apply()

    var snoozeTargets: Map<Int, Long>
        get() = synchronized(snoozeTargetsLock) {
            val stored = decodeSnoozeTargets(prefs.getString(KEY_SNOOZE_TARGETS, "") ?: "")
            val legacy = prefs.getLong(LEGACY_KEY_SNOOZE_TARGET_MILLIS, 0L)
            if (legacy <= 0L) return stored
            val merged = stored + (AlarmScheduler.SNOOZE_ID to legacy)
            prefs.edit()
                .remove(LEGACY_KEY_SNOOZE_TARGET_MILLIS)
                .putString(KEY_SNOOZE_TARGETS, encodeSnoozeTargets(merged))
                .commit()
            merged
        }
        set(value) {
            synchronized(snoozeTargetsLock) {
                prefs.edit()
                    .putString(KEY_SNOOZE_TARGETS, encodeSnoozeTargets(value))
                    .commit()
            }
        }

    fun addSnoozeTarget(alarmId: Int, triggerAtMillis: Long) {
        synchronized(snoozeTargetsLock) {
            snoozeTargets = snoozeTargets + (alarmId to triggerAtMillis)
        }
    }

    fun removeSnoozeTarget(alarmId: Int) {
        synchronized(snoozeTargetsLock) {
            snoozeTargets = snoozeTargets - alarmId
        }
    }

    var maxRingMinutes: Int
        get() = prefs.getInt(KEY_MAX_RING_MINUTES, DEFAULT_MAX_RING_MINUTES)
        set(value) = prefs.edit().putInt(KEY_MAX_RING_MINUTES, value).apply()

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_ENABLED, value).apply()

    var volumeRampSeconds: Int
        get() = prefs.getInt(KEY_VOLUME_RAMP_SECONDS, DEFAULT_VOLUME_RAMP_SECONDS)
        set(value) = prefs.edit().putInt(KEY_VOLUME_RAMP_SECONDS, value).apply()

    var alarmSoundUri: String?
        get() = prefs.getString(KEY_ALARM_SOUND_URI, null)
        set(value) = prefs.edit().putString(KEY_ALARM_SOUND_URI, value).apply()

    var snoozeEscalationMinutes: Int
        get() = prefs.getInt(KEY_SNOOZE_ESCALATION_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_SNOOZE_ESCALATION_MINUTES, value).apply()

    var gentleWakeMinutes: Int
        get() = prefs.getInt(KEY_GENTLE_WAKE_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_GENTLE_WAKE_MINUTES, value).apply()

    var fallAsleepMinutes: Int
        get() = prefs.getInt(
            KEY_FALL_ASLEEP_MINUTES, WakeTimeCalculator.DEFAULT_FALL_ASLEEP_MINUTES
        )
        set(value) = prefs.edit().putInt(KEY_FALL_ASLEEP_MINUTES, value).apply()

    var batteryPromptShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_PROMPT_SHOWN, value).apply()
}
