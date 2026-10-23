package no.stormberry.moonapp.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The pure decisions of the 1.0.4 alarm path (ported from SunApp 1.1.4's FireRulesTest, without snooze). */
class FireRulesTest {

    private val t = 1_800_000_000_000L
    private val min = 60_000L

    @Test
    fun `an on-time alarm rings, and so does one with no record or an unreadable one`() {
        assertEquals(FireDecision.RING, FireRules.decide(t, t, null))
        assertEquals(FireDecision.RING, FireRules.decide(t, t + 5 * min, t - 25 * 60 * min))
    }

    @Test
    fun `an alarm armed by 1_0_3 has no instant and always rings`() {
        assertEquals(FireDecision.RING, FireRules.decide(0L, t, t + min))
    }

    @Test
    fun `more than two seconds early is a clock jump and does not ring, up to two is on time`() {
        assertEquals(FireDecision.EARLY, FireRules.decide(t, t - 2_001L, null))
        assertEquals(FireDecision.RING, FireRules.decide(t, t - 2_000L, null))
    }

    @Test
    fun `an occurrence at or before the last ring has rung already`() {
        // The Android 12 backstop and the exact alarm both arriving for the same occurrence.
        assertEquals(FireDecision.ALREADY_RUNG, FireRules.decide(t, t + 30_000L, t))
        // The next occurrence is not.
        assertEquals(FireDecision.RING, FireRules.decide(t + 24 * 60 * min, t + 24 * 60 * min, t))
    }

    @Test
    fun `a record more than an hour ahead of the clock no longer counts`() {
        // The clock was set back two hours after the ring: nobody can tell which is wrong, so the
        // re-plan drops the record (AlarmCapability.pruneRung) and the occurrence rings again.
        assertTrue(FireRules.rungCounts(t + 60 * min, t))
        assertFalse(FireRules.rungCounts(t + 60 * min + 1, t))
    }

    @Test
    fun `only an on-time arrival is recorded as a ring`() {
        assertTrue(FireRules.shouldRecordRing(t, t + 2 * min, inexact = false))
        assertFalse(FireRules.shouldRecordRing(t, t + 2 * min + 1, inexact = false))
        assertTrue(FireRules.shouldRecordRing(t, t + 65 * min, inexact = true))
        assertFalse(FireRules.shouldRecordRing(t, t + 65 * min + 1, inexact = true))
        assertFalse("no instant, nothing to record", FireRules.shouldRecordRing(0L, t, inexact = false))
    }

    @Test
    fun `planning starts after the last ring when it is ahead and still counts (the clock set back)`() {
        val now = Instant.ofEpochMilli(t)
        assertEquals(now, FireRules.planAfter(now, null))
        assertEquals(now, FireRules.planAfter(now, t - min))
        assertEquals(Instant.ofEpochMilli(t + 30 * min), FireRules.planAfter(now, t + 30 * min))
        assertEquals("too far ahead to trust", now, FireRules.planAfter(now, t + 61 * min))
    }

    @Test
    fun `the backstop is for Android 12 and 12L only`() {
        for (sdk in listOf(24, 26, 28, 30, 33, 34, 36)) assertFalse("$sdk", FireRules.backstopWanted(sdk))
        assertTrue(FireRules.backstopWanted(31))
        assertTrue(FireRules.backstopWanted(32))
    }

    @Test
    fun `arming falls back to inexact when exact throws, and never throws itself`() {
        assertEquals(ArmResult.EXACT, FireRules.armWithFallback(exact = {}, inexact = { error("not called") }))
        var reported: RuntimeException? = null
        assertEquals(
            ArmResult.INEXACT,
            FireRules.armWithFallback(exact = { throw SecurityException("off") }, inexact = {}, onExactFailure = { reported = it }),
        )
        assertTrue(reported is SecurityException)
        assertEquals(
            ArmResult.FAILED,
            FireRules.armWithFallback(exact = { throw SecurityException("off") }, inexact = { throw IllegalStateException("limit") }),
        )
    }
}
