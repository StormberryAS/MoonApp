package no.stormberry.moonapp.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the alarms banner reports (1.0.4, A-1), decided on the JVM from the platform's answers. */
class AlarmGapsTest {

    private val high = 4
    private val default = 3
    private val none = 0

    @Test
    fun `nothing to report on Android 7 to 11 when notifications are on`() {
        for (sdk in 24..30) {
            assertEquals("$sdk", emptyList<AlarmCapability.Gap>(), AlarmCapability.gapsFrom(sdk, true, false, false, high, high))
        }
    }

    @Test
    fun `notifications switched off count on every version, not only from Android 13`() {
        // 1.0.3 checked POST_NOTIFICATIONS on 33 and later only.
        for (sdk in listOf(24, 28, 31, 33, 36)) {
            assertEquals("$sdk", AlarmCapability.Gap.NOTIFICATIONS, AlarmCapability.gapsFrom(sdk, false, true, true).first())
        }
    }

    @Test
    fun `a ringing channel blocked or below High counts as notifications off, from Android 8`() {
        assertEquals(listOf(AlarmCapability.Gap.NOTIFICATIONS), AlarmCapability.gapsFrom(36, true, true, true, default, high))
        assertEquals(listOf(AlarmCapability.Gap.NOTIFICATIONS), AlarmCapability.gapsFrom(36, true, true, true, high, none))
        assertEquals(emptyList<AlarmCapability.Gap>(), AlarmCapability.gapsFrom(36, true, true, true, high, high))
        assertEquals("a channel not created yet", emptyList<AlarmCapability.Gap>(), AlarmCapability.gapsFrom(36, true, true, true, null, null))
        assertEquals("no channels before 8", emptyList<AlarmCapability.Gap>(), AlarmCapability.gapsFrom(25, true, true, true, default, default))
    }

    @Test
    fun `exact alarms from 12, full-screen from 14, in order of severity`() {
        assertEquals(listOf(AlarmCapability.Gap.EXACT_ALARMS), AlarmCapability.gapsFrom(31, true, false, false))
        assertEquals(
            listOf(AlarmCapability.Gap.NOTIFICATIONS, AlarmCapability.Gap.EXACT_ALARMS, AlarmCapability.Gap.FULL_SCREEN),
            AlarmCapability.gapsFrom(34, false, false, false),
        )
    }

    @Test
    fun `the fix opens the one turned-down channel's page, else the app's`() {
        assertEquals(AlarmService.CHANNEL_ID, AlarmCapability.channelToFix(36, true, default, high))
        assertEquals(AlarmService.CHANNEL_BACKUP, AlarmCapability.channelToFix(36, true, high, default))
        assertNull(AlarmCapability.channelToFix(36, true, default, default))
        assertNull(AlarmCapability.channelToFix(36, false, default, high))
    }
}
