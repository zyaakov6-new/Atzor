package app.atzor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreCodecTest {

    @Test
    fun lockHistory_roundTrip() {
        val map = mapOf(10L to 60_000L, 11L to 120_000L)
        val raw = encodeLockHistory(map)
        assertEquals(map, decodeLockHistory(raw))
    }

    @Test
    fun btLabels_roundTrip() {
        val map = mapOf("AA:BB" to "Car", "CC:DD" to "Watch")
        assertEquals(map, decodeBtLabels(encodeBtLabels(map)))
    }

    @Test
    fun schedules_roundTrip() {
        val list = listOf(
            Schedule(days = setOf(1, 2, 3), startMin = 9 * 60, endMin = 17 * 60, enabled = true),
        )
        val decoded = decodeSchedules(encodeSchedules(list))
        assertEquals(1, decoded.size)
        assertEquals(setOf(1, 2, 3), decoded[0].days)
        assertEquals(9 * 60, decoded[0].startMin)
        assertTrue(decoded[0].enabled)
    }

    @Test
    fun gentleOverrides_roundTrip() {
        val map = mapOf("com.instagram.android" to true, "com.foo" to false)
        assertEquals(map, decodeGentleOverrides(encodeGentleOverrides(map)))
    }
}
