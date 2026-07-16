package com.sleepalarm

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

data class HistoryEvent(val epochMillis: Long, val type: Type) {
    enum class Type { RING, SNOOZE, DISMISS, AUTO_SNOOZE }
}

class HistoryStore(context: Context) {

    companion object {
        private const val KEY_HISTORY = "alarm_history"
        private const val EVENT_SEPARATOR = ";"
        const val MAX_EVENTS = 200

        fun encode(event: HistoryEvent): String =
            "${event.epochMillis}|${event.type.name}"

        fun decode(encoded: String): HistoryEvent? {
            val fields = encoded.split("|")
            if (fields.size != 2) return null
            return HistoryEvent(
                epochMillis = fields[0].toLongOrNull() ?: return null,
                type = HistoryEvent.Type.entries.firstOrNull { it.name == fields[1] }
                    ?: return null
            )
        }

        fun encodeAll(events: List<HistoryEvent>): String =
            events.joinToString(EVENT_SEPARATOR, transform = ::encode)

        fun decodeAll(encoded: String): List<HistoryEvent> =
            if (encoded.isEmpty()) {
                emptyList()
            } else {
                encoded.split(EVENT_SEPARATOR)
                    .mapNotNull(::decode)
                    .sortedBy { it.epochMillis }
            }

        fun appended(events: List<HistoryEvent>, event: HistoryEvent): List<HistoryEvent> =
            (events + event).sortedBy { it.epochMillis }.takeLast(MAX_EVENTS)

        private val historyLock = Any()


        fun encodeJson(events: List<HistoryEvent>): String =
            events.joinToString(",", "[", "]") {
                """{"at":${it.epochMillis},"type":"${it.type.name}"}"""
            }

        private val JSON_EVENT = Regex(
            """\{\s*"at"\s*:\s*(-?\d+)\s*,\s*"type"\s*:\s*"([A-Z_]+)"\s*\}"""
        )

        fun decodeJson(json: String): List<HistoryEvent>? {
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null
            return JSON_EVENT.findAll(trimmed).mapNotNull { match ->
                HistoryEvent(
                    epochMillis = match.groupValues[1].toLongOrNull()
                        ?: return@mapNotNull null,
                    type = HistoryEvent.Type.entries
                        .firstOrNull { it.name == match.groupValues[2] }
                        ?: return@mapNotNull null
                )
            }.sortedBy { it.epochMillis }.toList()
        }
    }

    private val prefs = Prefs.sharedPrefs(context)

    fun append(type: HistoryEvent.Type, atMillis: Long = System.currentTimeMillis()) {
        synchronized(historyLock) {
            prefs.edit()
                .putString(
                    KEY_HISTORY,
                    encodeAll(appended(events(), HistoryEvent(atMillis, type)))
                )
                .apply()
        }
    }

    fun events(): List<HistoryEvent> = decodeAll(prefs.getString(KEY_HISTORY, "") ?: "")

    fun replaceAll(events: List<HistoryEvent>) {
        synchronized(historyLock) {
            prefs.edit()
                .putString(
                    KEY_HISTORY,
                    encodeAll(events.sortedBy { it.epochMillis }.takeLast(MAX_EVENTS))
                )
                .commit()
        }
    }

    fun clear() {
        synchronized(historyLock) {
            prefs.edit().remove(KEY_HISTORY).apply()
        }
    }
}

object HistoryStats {

    const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    data class Summary(
        val ringsLastWeek: Int,
        val snoozesLastWeek: Int,
        val dismissesLastWeek: Int,
        val averageDismissMillis: Long?,
        val longestSnoozeStreak: Int,
        val wakeConsistencyMillis: Long?,
        val noSnoozeStreak: Int
    )

    fun summarize(
        events: List<HistoryEvent>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Summary {
        val weekAgo = nowMillis - WEEK_MILLIS
        val recent = events.filter { it.epochMillis >= weekAgo }
        return Summary(
            ringsLastWeek = recent.count { it.type == HistoryEvent.Type.RING },
            snoozesLastWeek = recent.count { isSnooze(it.type) },
            dismissesLastWeek = recent.count { it.type == HistoryEvent.Type.DISMISS },
            averageDismissMillis = averageDismissMillis(events),
            longestSnoozeStreak = longestSnoozeStreak(events),
            wakeConsistencyMillis = wakeConsistencyMillis(events, nowMillis, zone),
            noSnoozeStreak = currentNoSnoozeStreak(events)
        )
    }

    fun wakeConsistencyMillis(
        events: List<HistoryEvent>,
        nowMillis: Long,
        zone: ZoneId
    ): Long? {
        val weekAgo = nowMillis - WEEK_MILLIS
        val ringTimes = events
            .filter { it.type == HistoryEvent.Type.RING && it.epochMillis >= weekAgo }
            .map {
                Instant.ofEpochMilli(it.epochMillis).atZone(zone)
                    .toLocalTime().toNanoOfDay() / 1_000_000
            }
        if (ringTimes.size < 2) return null
        val mean = ringTimes.average()
        return ringTimes.map { abs(it - mean) }.average().toLong()
    }

    fun currentNoSnoozeStreak(events: List<HistoryEvent>): Int {
        val sessions = mutableListOf<Boolean>()
        var snoozed = false
        for (event in events) {
            when {
                isSnooze(event.type) -> snoozed = true
                event.type == HistoryEvent.Type.DISMISS -> {
                    sessions += !snoozed
                    snoozed = false
                }
            }
        }
        return sessions.asReversed().takeWhile { it }.count()
    }

    fun averageDismissMillis(events: List<HistoryEvent>): Long? {
        var openRingAt: Long? = null
        var totalMillis = 0L
        var pairs = 0
        for (event in events) {
            when (event.type) {
                HistoryEvent.Type.RING ->
                    if (openRingAt == null) openRingAt = event.epochMillis
                HistoryEvent.Type.DISMISS -> {
                    val start = openRingAt
                    if (start != null) {
                        totalMillis += event.epochMillis - start
                        pairs++
                        openRingAt = null
                    }
                }
                else -> Unit
            }
        }
        return if (pairs == 0) null else totalMillis / pairs
    }

    fun longestSnoozeStreak(events: List<HistoryEvent>): Int {
        var current = 0
        var longest = 0
        for (event in events) {
            when {
                isSnooze(event.type) -> {
                    current++
                    if (current > longest) longest = current
                }
                event.type == HistoryEvent.Type.DISMISS -> current = 0
            }
        }
        return longest
    }

    data class WakeSession(
        val wakeMillis: Long,
        val snoozes: Int,
        val autoSnoozes: Int
    )

    fun sessions(events: List<HistoryEvent>): List<WakeSession> {
        val sessions = mutableListOf<WakeSession>()
        var snoozes = 0
        var autoSnoozes = 0
        for (event in events) {
            when (event.type) {
                HistoryEvent.Type.SNOOZE -> snoozes++
                HistoryEvent.Type.AUTO_SNOOZE -> {
                    snoozes++
                    autoSnoozes++
                }
                HistoryEvent.Type.DISMISS -> {
                    sessions += WakeSession(event.epochMillis, snoozes, autoSnoozes)
                    snoozes = 0
                    autoSnoozes = 0
                }
                HistoryEvent.Type.RING -> Unit
            }
        }
        return sessions
    }

    fun formatMinutesSeconds(millis: Long): String {
        val totalSeconds = millis / 1000
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun isSnooze(type: HistoryEvent.Type): Boolean =
        type == HistoryEvent.Type.SNOOZE || type == HistoryEvent.Type.AUTO_SNOOZE
}
