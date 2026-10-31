package no.stormberry.moonapp.ui

import no.stormberry.moonapp.ui.components.FooterText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The Google Play build's footer. It has no alarms, so the two lines that used to speak of them are
 * reworded and nothing else in the footer differs.
 */
class PlayFooterTest {

    @Test fun thePlayLinesDoNotMentionAlarms() {
        for (line in listOf(FooterText.OFFLINE_PLAY, FooterText.PERMISSIONS_PLAY)) {
            assertFalse(line, line.contains("alarm", ignoreCase = true))
        }
    }

    @Test fun thePlayLinesAreTheApprovedLinesWithTheAlarmsLeftOut() {
        assertEquals("Your places never leave this device. Disconnect and try it.", FooterText.OFFLINE_PLAY)
        assertEquals("No permissions. No internet. No tracking.", FooterText.PERMISSIONS_PLAY)
        assertNotEquals(FooterText.OFFLINE, FooterText.OFFLINE_PLAY)
        assertNotEquals(FooterText.PERMISSIONS, FooterText.PERMISSIONS_PLAY)
    }

    @Test fun thePlayFooterCopyHasNoEmDashAndNoDoubleHyphen() {
        for (c in listOf(FooterText.OFFLINE_PLAY, FooterText.PERMISSIONS_PLAY)) {
            assertFalse(c, c.contains('\u2014'))
            assertFalse(c, c.contains("--"))
        }
    }
}
