package no.stormberry.moonapp.alarm

import no.stormberry.moonapp.alarm.model.MoonAlarmRule
import no.stormberry.moonapp.lunar.LunarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The alarm planner on the JVM, through the [AlarmSink] seam (1.0.4). Until 1.0.4 the planner
 * called AlarmManager directly and had no test at all.
 */
class AlarmPlannerTest {

    private val now = Instant.parse("2026-10-23T08:00:00Z")

    private fun rule(id: String, event: LunarEvent = LunarEvent.MOONRISE, enabled: Boolean = true, offset: Int = 0) =
        MoonAlarmRule(id = id, label = id, event = event, offsetMinutes = offset, cityName = "Bergen",
            latDeg = 60.3913, lonDeg = 5.3221, enabled = enabled, vibrate = false)

    @Test
    fun `every rule gets its own alarm under its own request code`() {
        // The 2026-09-04 regression: one shared request code meant only one alarm could exist.
        val rules = listOf(rule("a"), rule("b", LunarEvent.MOONSET), rule("c", LunarEvent.LUNAR_TRANSIT))
        val sink = FakeAlarmSink()
        AlarmPlanner.apply(rules, AlarmPlanner.plan(rules, now), sink, sdkInt = 36)
        assertEquals(listOf("a", "b", "c"), sink.armed.map { it.rule.id })
        assertEquals(3, sink.armed.map { it.requestCode }.toSet().size)
        sink.armed.forEach { assertEquals(AlarmPlanner.requestCodeFor(it.rule.id), it.requestCode) }
        assertTrue(sink.armed.all { it.fireAt.isAfter(now) })
    }

    @Test
    fun `a switched-off rule is cancelled, not armed`() {
        val rules = listOf(rule("on"), rule("off", enabled = false))
        val sink = FakeAlarmSink()
        AlarmPlanner.apply(rules, AlarmPlanner.plan(rules, now), sink, sdkInt = 36)
        assertEquals(listOf("on"), sink.armed.map { it.rule.id })
        assertEquals(listOf(AlarmPlanner.requestCodeFor("off")), sink.cancelled)
    }

    @Test
    fun `one rule that cannot be armed does not stop the others`() {
        // The SecurityException loop case: 1.0.3 caught it per rule, 1.0.4 keeps that through the seam.
        val rules = listOf(rule("first"), rule("broken"), rule("last"))
        val sink = FakeAlarmSink(throwFor = setOf("broken"))
        AlarmPlanner.apply(rules, AlarmPlanner.plan(rules, now), sink, sdkInt = 36)
        assertEquals(listOf("first", "last"), sink.armed.map { it.rule.id })
    }

    @Test
    fun `with exact alarms off every alarm is still armed, inexactly (A-2)`() {
        // 1.0.3: setAlarmClock threw SecurityException and the catch only logged it, so nothing
        // was armed at all on Android 12 and 12L with exact alarms switched off.
        val rules = listOf(rule("a"), rule("b", LunarEvent.MOONSET))
        val sink = FakeAlarmSink(refuseExact = true)
        AlarmPlanner.apply(rules, AlarmPlanner.plan(rules, now), sink, sdkInt = 31)
        assertEquals(2, sink.armed.size)
        assertEquals(listOf(ArmResult.INEXACT, ArmResult.INEXACT), sink.results)
    }

    @Test
    fun `the backstop is the nearest alarm, on Android 12 and 12L only`() {
        val rules = listOf(rule("rise"), rule("set", LunarEvent.MOONSET), rule("peak", LunarEvent.LUNAR_TRANSIT))
        val plan = AlarmPlanner.plan(rules, now)
        for (sdk in listOf(31, 32)) {
            val sink = FakeAlarmSink()
            AlarmPlanner.apply(rules, plan, sink, sdk)
            assertEquals("$sdk", listOf(plan.minByOrNull { it.fireAt }), sink.guards)
            // Arms first, then the backstop: never a moment with the backstop and no alarm.
            assertTrue(sink.calls.last() is FakeAlarmSink.Call.Guard)
        }
        for (sdk in listOf(24, 30, 33, 36)) {
            val sink = FakeAlarmSink()
            AlarmPlanner.apply(rules, plan, sink, sdk)
            assertEquals("$sdk: null cancels any backstop left over", listOf<PlannedAlarm?>(null), sink.guards)
        }
        val none = FakeAlarmSink()
        AlarmPlanner.apply(emptyList(), emptyList(), none, 31)
        assertEquals(listOf<PlannedAlarm?>(null), none.guards)
    }

    @Test
    fun `re-planning after a ring never arms the occurrence that rang, even with the clock set back`() {
        // The A-8 related case: the alarm rang at T, then the clock was set back 30 minutes.
        val r = rule("rise")
        val first = AlarmPlanner.plan(listOf(r), now).single()
        val rungAt = first.fireAt.toEpochMilli()
        val setBack = first.fireAt.minus(Duration.ofMinutes(30))
        val again = AlarmPlanner.plan(listOf(r), setBack, mapOf("rise" to rungAt)).single()
        assertTrue("re-armed ${again.fireAt}, at or before the ring ${first.fireAt}", again.fireAt.isAfter(first.fireAt))
        // Without the record (1.0.3) the same occurrence was armed again and rang twice.
        assertEquals(first.fireAt, AlarmPlanner.plan(listOf(r), setBack).single().fireAt)
        // Set back further than the record can be trusted: it rings again rather than being skipped.
        val farBack = first.fireAt.minus(Duration.ofHours(3))
        assertEquals(first.fireAt, AlarmPlanner.plan(listOf(r), farBack, mapOf("rise" to rungAt)).single().fireAt)
    }

    @Test
    fun `a transit rule arms one alarm per transit across days without one (M-2)`() {
        // Sydney, around 2026-10-28: 1.0.3 armed 00:00 UTC (the edge of the 27th) and 00:33 UTC (the
        // real transit). Walk the plan forwards as the receiver would, ring after ring.
        val r = MoonAlarmRule(id = "peak", label = "peak", event = LunarEvent.LUNAR_TRANSIT,
            cityName = "Sydney", latDeg = -33.8688, lonDeg = 151.2093, vibrate = false)
        var t = Instant.parse("2026-10-20T00:00:00Z")
        val rings = mutableListOf<Instant>()
        repeat(30) {
            val p = AlarmPlanner.plan(listOf(r), t, rings.lastOrNull()?.let { mapOf("peak" to it.toEpochMilli()) } ?: emptyMap()).singleOrNull()
                ?: return@repeat
            rings += p.fireAt
            t = p.fireAt
        }
        val gaps = rings.zipWithNext { a, b -> Duration.between(a, b).toMinutes() }
        assertTrue("transits $rings", gaps.all { it >= 20 * 60 })
        assertNull("no ring at the very start of a UTC day", rings.firstOrNull { it.toString().endsWith("T00:00:00Z") })
    }
}
