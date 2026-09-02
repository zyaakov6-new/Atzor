package app.atzor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wall clock is user-writable, so a timed lock must be measured against
 * elapsedRealtime instead. (The emergency release is a held gesture now, not a
 * timed wait, so it has no clock to tamper with.) Every case here passes both clocks explicitly, so
 * nothing touches the real android.os.SystemClock (unavailable in plain JUnit).
 */
class ClockTamperTest {

    private val hour = 60L * 60L * 1000L
    private val wallStart = 1_700_000_000_000L
    private val elapsedStart = 5_000_000L

    /** A one-hour manual session, started at the reference instants above. */
    private fun oneHourSession() = AtzorState(
        sessionEndAt = wallStart + hour,
        sessionStartElapsed = elapsedStart,
        sessionEndElapsed = elapsedStart + hour,
    )

    @Test
    fun forwardClockJump_doesNotEndLockEarly() {
        val s = oneHourSession()
        // One minute of real time has passed, but the user set the date a day ahead.
        val nowWall = wallStart + 24L * hour
        val nowElapsed = elapsedStart + 60_000L

        assertTrue(s.lockedNow(nowWall, nowElapsed))
        assertEquals(hour - 60_000L, s.sessionRemainingMs(nowWall, nowElapsed))
    }

    @Test
    fun backwardClockJump_doesNotExtendLock() {
        val s = oneHourSession()
        // The user wound the clock back a day; the hour is still up in real time.
        val nowWall = wallStart - 24L * hour
        val nowElapsed = elapsedStart + hour + 1000L

        assertFalse(s.lockedNow(nowWall, nowElapsed))
        assertEquals(0L, s.sessionRemainingMs(nowWall, nowElapsed))
    }

    @Test
    fun untamperedSession_endsWhenTheHourIsUp() {
        val s = oneHourSession()
        assertTrue(s.lockedNow(wallStart + hour - 1000L, elapsedStart + hour - 1000L))
        assertFalse(s.lockedNow(wallStart + hour, elapsedStart + hour))
    }

    @Test
    fun reboot_isNotTreatedAsTampering_andLockSurvivesOnWallClock() {
        val s = oneHourSession()
        // elapsedRealtime restarted near zero: that is a reboot, not a clock change.
        val nowElapsed = 30_000L
        val nowWall = wallStart + 10L * 60_000L

        assertTrue(s.rebootedSinceSession(nowElapsed))
        // Falls back to the wall clock, so the lock keeps running.
        assertTrue(s.lockedNow(nowWall, nowElapsed))
        assertEquals(hour - 10L * 60_000L, s.sessionRemainingMs(nowWall, nowElapsed))
    }

    @Test
    fun reboot_afterWallClockDeadline_endsTheLock() {
        val s = oneHourSession()
        assertFalse(s.lockedNow(wallStart + 2L * hour, 30_000L))
    }

    @Test
    fun keyOnlySession_neverExpiresOnEitherClock() {
        val s = AtzorState(
            sessionEndAt = Long.MAX_VALUE,
            sessionStartElapsed = elapsedStart,
            sessionEndElapsed = 0L,
        )
        assertEquals(Long.MAX_VALUE, s.sessionRemainingMs(wallStart + 999L * hour, elapsedStart + 999L * hour))
        assertTrue(s.lockedNow(wallStart + 999L * hour, elapsedStart + 999L * hour))
    }

    @Test
    fun noSession_isNotLocked() {
        assertEquals(0L, AtzorState().sessionRemainingMs(wallStart, elapsedStart))
        assertFalse(AtzorState().lockedNow(wallStart, elapsedStart))
    }





    @Test
    fun lockEndAt_projectsMonotonicRemainderOntoTheWallClock() {
        val s = oneHourSession()
        // Clock moved a day forward; the displayed end must still be ~59 minutes out.
        val nowWall = wallStart + 24L * hour
        val nowElapsed = elapsedStart + 60_000L
        assertEquals(nowWall + hour - 60_000L, s.lockEndAt(nowWall, nowElapsed))
    }
}
