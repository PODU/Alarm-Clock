package com.sleepalarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryExportCodecTest {

    private val ring = HistoryEvent(1_784_000_000_000L, HistoryEvent.Type.RING)
    private val snooze = HistoryEvent(1_784_000_060_000L, HistoryEvent.Type.SNOOZE)
    private val autoSnooze = HistoryEvent(1_784_000_120_000L, HistoryEvent.Type.AUTO_SNOOZE)
    private val dismiss = HistoryEvent(1_784_000_180_000L, HistoryEvent.Type.DISMISS)

    @Test
    fun everyEventTypeRoundTrips() {
        val events = listOf(ring, snooze, autoSnooze, dismiss)
        assertEquals(events, HistoryStore.decodeJson(HistoryStore.encodeJson(events)))
    }

    @Test
    fun emptyLogRoundTrips() {
        assertEquals("[]", HistoryStore.encodeJson(emptyList()))
        assertEquals(emptyList<HistoryEvent>(), HistoryStore.decodeJson("[]"))
    }

    @Test
    fun encodedFormIsAJsonArrayOfFlatObjects() {
        assertEquals(
            """[{"at":1784000000000,"type":"RING"},{"at":1784000060000,"type":"SNOOZE"}]""",
            HistoryStore.encodeJson(listOf(ring, snooze))
        )
    }

    @Test
    fun decodeToleratesWhitespace() {
        val json = """
            [ { "at" : 1784000000000 , "type" : "RING" },
              { "at" : 1784000180000 , "type" : "DISMISS" } ]
        """.trimIndent()
        assertEquals(listOf(ring, dismiss), HistoryStore.decodeJson(json))
    }

    @Test
    fun decodeRejectsNonArrayInput() {
        assertNull(HistoryStore.decodeJson(""))
        assertNull(HistoryStore.decodeJson("garbage"))
        assertNull(HistoryStore.decodeJson("""{"at":1,"type":"RING"}"""))
        assertNull(HistoryStore.decodeJson("[1,2,3"))
    }

    @Test
    fun decodeDropsMalformedEntries() {
        val json = """[{"at":1784000000000,"type":"RING"},""" +
            """{"at":1,"type":"NAP"},""" +
            """{"type":"DISMISS"},""" +
            """{"at":1784000180000,"type":"DISMISS"}]"""
        assertEquals(listOf(ring, dismiss), HistoryStore.decodeJson(json))
    }

    @Test
    fun decodeSortsAscendingLikeThePipeCodec() {
        val json = HistoryStore.encodeJson(listOf(dismiss, ring, snooze))
        assertEquals(listOf(ring, snooze, dismiss), HistoryStore.decodeJson(json))
    }

    @Test
    fun duplicateEventsSurviveTheRoundTrip() {
        val events = listOf(ring, ring)
        assertEquals(events, HistoryStore.decodeJson(HistoryStore.encodeJson(events)))
    }
}
