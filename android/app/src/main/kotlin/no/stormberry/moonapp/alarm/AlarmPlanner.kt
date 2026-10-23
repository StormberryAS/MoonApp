package no.stormberry.moonapp.alarm

import android.content.Context
import android.os.Build
import android.util.Log
import no.stormberry.moonapp.alarm.model.MoonAlarmRule
import java.time.Instant

/**
 * Decides which alarms are armed, and arms them through an [AlarmSink].
 *
 * 1.0.4 splits it in two, as SunApp's: [plan] and [apply] are pure and tested on the JVM against
 * a fake sink (AlarmPlannerTest), and [replan] is the one entry point every caller uses, reading
 * the rules and the last rings and handing the result to [AndroidAlarmSink]. Until 1.0.4 each
 * caller armed one rule straight into `AlarmManager`, and when `setAlarmClock` threw (Android 12
 * and 12L with exact alarms off) the catch only logged it: nothing was armed at all (A-2).
 */
object AlarmPlanner {

    private const val TAG = "MoonAlarmPlanner"
    private val lock = Any()

    // Every rule needs its OWN request code. A single shared constant meant every
    // PendingIntent compared equal, so arming a second alarm silently replaced the first
    // and only one alarm could ever exist. PendingIntent equality ignores extras, so the
    // request code is the only thing that separates them. Derived from the rule id, which
    // is stable across a reboot, unlike a list index. Fixed 2026-09-04.
    fun requestCodeFor(ruleId: String): Int = ruleId.hashCode()

    /**
     * The next occurrence of every enabled rule after [now], or after the rule's last ring when
     * that is later and still counts ([FireRules.planAfter]), so an occurrence that has rung is
     * never armed again (the A-8 related double ring after the clock is set back).
     */
    fun plan(rules: List<MoonAlarmRule>, now: Instant, lastRung: Map<String, Long> = emptyMap()): List<PlannedAlarm> =
        rules.filter { it.enabled }.mapNotNull { rule ->
            OccurrenceEngine.findNextOccurrence(rule, FireRules.planAfter(now, lastRung[rule.id]))
                ?.let { PlannedAlarm(rule, it, requestCodeFor(rule.id)) }
        }

    /**
     * Cancels every rule that has no planned alarm (switched off, or no occurrence within the
     * search), arms every planned one under its own request code, and sets the Android 12 and 12L
     * backstop for the nearest ([FireRules.backstopWanted]; null elsewhere, which cancels it).
     * One rule failing never stops the others.
     */
    fun apply(rules: List<MoonAlarmRule>, plan: List<PlannedAlarm>, sink: AlarmSink, sdkInt: Int) {
        val planned = plan.map { it.rule.id }.toSet()
        for (rule in rules) {
            if (rule.id !in planned) runCatching { sink.cancel(requestCodeFor(rule.id)) }
        }
        for (p in plan) {
            try {
                sink.arm(p)
            } catch (e: RuntimeException) {
                // Inside runCatching: on the JVM (AlarmPlannerTest) android.util.Log is a stub that throws.
                runCatching { Log.e(TAG, "arming ${p.rule.id} failed", e) }
            }
        }
        val next = if (FireRules.backstopWanted(sdkInt)) plan.minByOrNull { it.fireAt } else null
        runCatching { sink.guard(next) }
    }

    /**
     * Re-plans every rule from storage and arms the result. Called on every change: a rule saved
     * or deleted, an alarm firing, a reboot or clock change, and the app coming to the front
     * (1.0.4, A-2: on Android 12 and 12L, switching exact alarms off cancels every exact alarm with
     * no broadcast to say so, and until 1.0.4 nothing re-armed them before the next reboot).
     */
    fun replan(context: Context, sink: AlarmSink = AndroidAlarmSink(context), now: Instant = Instant.now()): List<PlannedAlarm> =
        synchronized(lock) {
            val rules = AlarmCapability.loadRules(context)
            runCatching { AlarmCapability.pruneRung(context, now.toEpochMilli(), rules.map { it.id }.toSet()) }
            val lastRung = runCatching { AlarmCapability.lastRungAll(context) }.getOrDefault(emptyMap())
            val plan = plan(rules, now, lastRung)
            apply(rules, plan, sink, Build.VERSION.SDK_INT)
            plan
        }

    /** Cancels the alarm of one rule (a deleted rule, which the re-plan can no longer see). */
    fun cancelAlarm(context: Context, ruleId: String) {
        AndroidAlarmSink(context).cancel(requestCodeFor(ruleId))
    }
}
