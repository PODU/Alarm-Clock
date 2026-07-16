package com.sleepalarm

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId

class AlarmStore(context: Context) {

    companion object {
        private const val KEY_ALARMS = "alarms"
        private const val KEY_NEXT_ID = "next_alarm_id"
        private const val LEGACY_KEY_WAKE_TIME = "wake_time_millis"
        private const val FIRST_ID = 1
        private const val LEGACY_FIELD_COUNT = 6
        private const val FIELD_COUNT = 11
        const val SPENT_ONE_SHOT_RETENTION_MILLIS = 24 * 60 * 60 * 1000L

        private val storeLock = Any()

        private fun escape(value: String): String = URLEncoder.encode(value, "UTF-8")
        private fun unescape(value: String): String = URLDecoder.decode(value, "UTF-8")

        fun encode(alarm: Alarm): String = listOf(
            alarm.id.toString(),
            alarm.triggerAtMillis.toString(),
            alarm.hour.toString(),
            alarm.minute.toString(),
            alarm.repeatDays.sorted().joinToString(","),
            alarm.enabled.toString(),
            escape(alarm.label),
            alarm.soundUri?.let(::escape) ?: "",
            alarm.challengeType?.name ?: "",
            alarm.difficulty?.name ?: "",
            alarm.skipNext.toString()
        ).joinToString("|")

        fun decode(encoded: String): Alarm? {
            val fields = encoded.split("|")
            if (fields.size != LEGACY_FIELD_COUNT && fields.size != FIELD_COUNT) return null
            val base = Alarm(
                id = fields[0].toIntOrNull() ?: return null,
                triggerAtMillis = fields[1].toLongOrNull() ?: return null,
                hour = fields[2].toIntOrNull() ?: return null,
                minute = fields[3].toIntOrNull() ?: return null,
                repeatDays = if (fields[4].isEmpty()) {
                    emptySet()
                } else {
                    fields[4].split(",").map { it.toIntOrNull() ?: return null }.toSet()
                },
                enabled = fields[5].toBooleanStrictOrNull() ?: return null
            )
            if (fields.size == LEGACY_FIELD_COUNT) return base
            return base.copy(
                label = unescape(fields[6]),
                soundUri = fields[7].takeIf { it.isNotEmpty() }?.let(::unescape),
                challengeType = fields[8].takeIf { it.isNotEmpty() }?.let { stored ->
                    ChallengeType.entries.firstOrNull { it.name == stored } ?: return null
                },
                difficulty = fields[9].takeIf { it.isNotEmpty() }?.let { stored ->
                    MathChallenge.Difficulty.entries.firstOrNull { it.name == stored }
                        ?: return null
                },
                skipNext = fields[10].toBooleanStrictOrNull() ?: return null
            )
        }

        fun withoutExpiredOneShots(alarms: List<Alarm>, nowMillis: Long): List<Alarm> =
            alarms.filterNot { alarm ->
                !alarm.enabled &&
                    alarm.repeatDays.isEmpty() &&
                    alarm.triggerAtMillis < nowMillis - SPENT_ONE_SHOT_RETENTION_MILLIS
            }
    }

    private val prefs = Prefs.sharedPrefs(context)

    fun load(): List<Alarm> = synchronized(storeLock) {
        migrateLegacyAlarm()
        prefs.getStringSet(KEY_ALARMS, emptySet())!!
            .mapNotNull { decode(it) }
            .sortedBy { it.id }
    }

    fun save(alarms: List<Alarm>) {
        synchronized(storeLock) {
            prefs.edit().putStringSet(KEY_ALARMS, alarms.map { encode(it) }.toSet()).commit()
        }
    }

    fun get(id: Int): Alarm? = load().firstOrNull { it.id == id }

    fun upsert(alarm: Alarm) {
        synchronized(storeLock) {
            save(load().filter { it.id != alarm.id } + alarm)
        }
    }

    fun delete(id: Int) {
        synchronized(storeLock) {
            save(load().filter { it.id != id })
        }
    }

    fun pruneExpiredOneShots(nowMillis: Long = System.currentTimeMillis()): Boolean =
        synchronized(storeLock) {
            val alarms = load()
            val kept = withoutExpiredOneShots(alarms, nowMillis)
            if (kept.size == alarms.size) return false
            save(kept)
            true
        }

    fun nextId(): Int = synchronized(storeLock) {
        val id = prefs.getInt(KEY_NEXT_ID, FIRST_ID)
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).commit()
        id
    }

    private fun migrateLegacyAlarm() {
        if (!prefs.contains(LEGACY_KEY_WAKE_TIME)) return
        val wakeTime = prefs.getLong(LEGACY_KEY_WAKE_TIME, -1L)
        prefs.edit().remove(LEGACY_KEY_WAKE_TIME).commit()
        if (wakeTime <= System.currentTimeMillis()) return
        val local = Instant.ofEpochMilli(wakeTime).atZone(ZoneId.systemDefault())
        upsert(Alarm(nextId(), wakeTime, local.hour, local.minute, emptySet(), enabled = true))
    }
}
