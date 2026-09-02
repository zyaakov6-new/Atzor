package app.atzor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bucketing is the privacy boundary: raw durations and counts must never
 * reach Firebase, so these boundaries are worth pinning down.
 */
class AnalyticsBucketTest {

    @Test
    fun durationBucket_untimedForNullAndKeyOnlySentinel() {
        assertEquals("untimed", Analytics.durationBucket(null))
        assertEquals("untimed", Analytics.durationBucket(Long.MAX_VALUE))
    }

    @Test
    fun durationBucket_boundaries() {
        assertEquals("under_15m", Analytics.durationBucket(0L))
        assertEquals("under_15m", Analytics.durationBucket(14L * 60_000L))
        assertEquals("15_60m", Analytics.durationBucket(15L * 60_000L))
        assertEquals("15_60m", Analytics.durationBucket(59L * 60_000L))
        assertEquals("1_4h", Analytics.durationBucket(60L * 60_000L))
        assertEquals("1_4h", Analytics.durationBucket(3L * 60L * 60_000L))
        assertEquals("4h_plus", Analytics.durationBucket(4L * 60L * 60_000L))
        assertEquals("4h_plus", Analytics.durationBucket(24L * 60L * 60_000L))
    }

    @Test
    fun countBucket_boundaries() {
        assertEquals("0", Analytics.countBucket(0))
        assertEquals("1_3", Analytics.countBucket(1))
        assertEquals("1_3", Analytics.countBucket(3))
        assertEquals("4_10", Analytics.countBucket(4))
        assertEquals("4_10", Analytics.countBucket(10))
        assertEquals("10_plus", Analytics.countBucket(11))
    }

    @Test
    fun analyticsCallsAreSafeWithoutFirebase() {
        // Analytics.init is never called in a unit test, so every logger must
        // no-op instead of throwing. This mirrors CrashReporter's contract.
        Analytics.logLockStarted("manual_timed", 60L * 60_000L)
        Analytics.logLockEnded("key", 60L * 60_000L)
        Analytics.logEmergencyRequested()
        Analytics.logEmergencyCompleted()
        Analytics.logGentlePassUsed()
        Analytics.logKeyRegistered("nfc")
        Analytics.logBlocklistConfigured("allowlist", 5)
        Analytics.logOnboardingCompleted(2)
    }
}
