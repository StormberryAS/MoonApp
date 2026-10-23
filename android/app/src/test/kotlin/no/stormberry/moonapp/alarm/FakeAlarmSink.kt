package no.stormberry.moonapp.alarm

/**
 * An [AlarmSink] that records what it was asked to do instead of talking to `AlarmManager`
 * (1.0.4, as SunApp's). An ordered log, because some of what the planner promises is about
 * sequence. [refuseExact] makes every arm report INEXACT (exact alarms off), [throwFor] makes
 * the arm of those rule ids throw, to prove one failure does not stop the others.
 */
internal class FakeAlarmSink(
    private val refuseExact: Boolean = false,
    private val throwFor: Set<String> = emptySet(),
) : AlarmSink {

    sealed interface Call {
        data class Arm(val planned: PlannedAlarm) : Call
        data class Cancel(val requestCode: Int) : Call
        data class Guard(val next: PlannedAlarm?) : Call
    }

    private val log = mutableListOf<Call>()
    val calls: List<Call> get() = log.toList()
    val armed: List<PlannedAlarm> get() = log.filterIsInstance<Call.Arm>().map { it.planned }
    val cancelled: List<Int> get() = log.filterIsInstance<Call.Cancel>().map { it.requestCode }
    val guards: List<PlannedAlarm?> get() = log.filterIsInstance<Call.Guard>().map { it.next }
    val results = mutableListOf<ArmResult>()

    fun reset() = log.clear()

    override fun arm(planned: PlannedAlarm): ArmResult {
        if (planned.rule.id in throwFor) throw IllegalStateException("test: arming ${planned.rule.id} throws")
        log += Call.Arm(planned)
        val r = if (refuseExact) {
            FireRules.armWithFallback(exact = { throw SecurityException("exact alarms are off") }, inexact = {})
        } else {
            ArmResult.EXACT
        }
        results += r
        return r
    }

    override fun cancel(requestCode: Int) {
        log += Call.Cancel(requestCode)
    }

    override fun guard(next: PlannedAlarm?) {
        log += Call.Guard(next)
    }
}
