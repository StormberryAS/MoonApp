package no.stormberry.moonapp.alarm

import no.stormberry.moonapp.alarm.model.MoonAlarmRule
import java.time.Instant

/** One alarm the planner wants armed: the rule, the instant, and the request code it lives under. */
data class PlannedAlarm(val rule: MoonAlarmRule, val fireAt: Instant, val requestCode: Int)

/**
 * The seam between the planner, which is pure and tested on the JVM, and `AlarmManager`, which
 * is neither (1.0.4). Ported from SunApp's AlarmSink.
 *
 * Until 1.0.4 AlarmPlanner called `AlarmManager` directly, so nothing about which alarms were
 * armed, cancelled or backed up could be tested off a device. Below this interface there is one
 * implementation, [AndroidAlarmSink], that only translates; every decision is above it.
 *
 * Implementations must be tolerant rather than strict: none of these may throw, because an
 * exception escaping a boot receiver leaves every other alarm unarmed.
 */
interface AlarmSink {

    /** Arm [planned], replacing whatever is armed under its request code. */
    fun arm(planned: PlannedAlarm): ArmResult

    /** Cancel whatever is armed under [requestCode]; a code never armed is a no-op. */
    fun cancel(requestCode: Int)

    /**
     * The backstop for the nearest alarm, or none when [next] is null (A-2). The planner passes
     * null on every version but Android 12 and 12L, and null cancels any backstop left over.
     */
    fun guard(next: PlannedAlarm?)
}
