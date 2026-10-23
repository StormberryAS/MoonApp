package no.stormberry.moonapp.alarm

import java.time.Instant

/** What the receiver does with an alarm that has arrived (1.0.4). */
enum class FireDecision {
    /** Ring it. */
    RING,

    /** It arrived more than two seconds early: the clock moved rather than time passing. */
    EARLY,

    /** Its occurrence has rung already (a backstop and the exact alarm both arriving, or a re-arm after the clock was set back). */
    ALREADY_RUNG,
}

/** What became of one arm request (1.0.4, A-2). */
enum class ArmResult { EXACT, INEXACT, FAILED }

/**
 * The pure decisions of the alarm path, with no `android.*` in them, so the JVM tests reach every
 * one (1.0.4). Ported from SunApp 1.1.4's FireRules (released `da8571d`), without its snooze and
 * Missed-notice parts: MoonApp has neither.
 *
 * Three kinds of decision live here:
 *  - **When an arrival rings.** [decide]: an arrival more than two seconds early is a clock jump
 *    and does not ring; an occurrence at or before the rule's last ring has rung already and does
 *    not ring again; everything else rings, however late. When in doubt, ring: an unreadable
 *    record is null, and null rings.
 *  - **What counts as a ring.** [shouldRecordRing] and [rungCounts]: only an on-time arrival is
 *    recorded, and a record more than an hour ahead of the clock no longer counts, so a large
 *    clock correction cannot silence a real occurrence.
 *  - **How to arm.** [armWithFallback] (exact, else inexact, never throws), [backstopWanted] (the
 *    Android 12 and 12L backstop) and [planAfter] (re-arming never arms an occurrence at or
 *    before the last ring: the A-8 related double ring after the clock is set back).
 */
object FireRules {

    /** An alarm arriving more than this early means the clock moved. */
    const val CLOCK_JUMP_TOLERANCE_MS: Long = 2_000L

    /**
     * How far ahead of the clock a rule's last ring may lie and still count. Further ahead means
     * the clock was set back a long way after the ring, and then nobody can tell whether the ring
     * or the clock is wrong: the record is dropped and the occurrence rings again rather than
     * being skipped. An hour covers what the record is for: an arrival up to two seconds early, a
     * small clock correction, and the backstop's second delivery.
     */
    const val LAST_RUNG_HORIZON_MS: Long = 60L * 60_000L

    /** How late an exact alarm may arrive and still be recorded as rung: later means the clock jumped forward past it. */
    const val RECORD_LATE_LIMIT_EXACT_MS: Long = 2L * 60_000L

    /** The same for an inexact alarm or a backstop, which Android may hold back for up to an hour, with five minutes of margin. */
    const val RECORD_LATE_LIMIT_INEXACT_MS: Long = 65L * 60_000L

    /**
     * What to do with an alarm armed for [armedAt] (0 when unknown, as for an alarm armed by 1.0.3)
     * arriving at [now], when its rule last rang at [lastRung] (null when it never rang, or the
     * record cannot be read). In this order: early, already rung, ring.
     */
    fun decide(armedAt: Long, now: Long, lastRung: Long?): FireDecision = when {
        armedAt > 0L && now < armedAt - CLOCK_JUMP_TOLERANCE_MS -> FireDecision.EARLY
        armedAt > 0L && lastRung != null && rungCounts(lastRung, now) && armedAt <= lastRung -> FireDecision.ALREADY_RUNG
        else -> FireDecision.RING
    }

    /**
     * Whether a ring of the occurrence armed for [armedAt] arriving at [now] is recorded as its
     * rule's last ring. Only an on-time arrival is: a late one rang at the wrong real time (the
     * clock jumped forward past it), and recording it would make a later correction of the clock
     * skip the real occurrence. [inexact] is true for a backstop, and for any alarm while exact
     * alarms are not allowed, which Android may legitimately hold back.
     */
    fun shouldRecordRing(armedAt: Long, now: Long, inexact: Boolean): Boolean =
        armedAt > 0L && now - armedAt <= if (inexact) RECORD_LATE_LIMIT_INEXACT_MS else RECORD_LATE_LIMIT_EXACT_MS

    /** Whether a last ring recorded for [lastRung] still counts at [now]: at most [LAST_RUNG_HORIZON_MS] ahead of the clock. */
    fun rungCounts(lastRung: Long, now: Long): Boolean = lastRung - now <= LAST_RUNG_HORIZON_MS

    /**
     * The instant a rule's next occurrence is planned after: [now], or its last ring when that is
     * later and still counts. The occurrence engine looks strictly after it, so an occurrence that
     * has rung is not armed again after the clock is set back a little; after a large correction
     * it is, and rings.
     */
    fun planAfter(now: Instant, lastRung: Long?): Instant {
        val nowMillis = now.toEpochMilli()
        return if (lastRung != null && lastRung > nowMillis && rungCounts(lastRung, nowMillis)) Instant.ofEpochMilli(lastRung) else now
    }

    /**
     * Whether the backstop for the nearest alarm is armed on this version (A-2). Only Android 12
     * and 12L let the person switch exact alarms off (SCHEDULE_EXACT_ALARM is capped at 32 and
     * USE_EXACT_ALARM cannot be revoked from 33), and switching them off cancels every exact alarm
     * the app holds with no broadcast to say so.
     */
    fun backstopWanted(sdkInt: Int): Boolean = sdkInt == 31 || sdkInt == 32

    /**
     * Arms with [exact]; if that throws anything, reports it to [onExactFailure] and arms with
     * [inexact]; if that throws too, reports FAILED. Never throws itself.
     */
    fun armWithFallback(
        exact: () -> Unit,
        inexact: () -> Unit,
        onExactFailure: (RuntimeException) -> Unit = {},
    ): ArmResult =
        try {
            exact()
            ArmResult.EXACT
        } catch (e: RuntimeException) {
            runCatching { onExactFailure(e) }
            try {
                inexact()
                ArmResult.INEXACT
            } catch (_: RuntimeException) {
                ArmResult.FAILED
            }
        }
}
