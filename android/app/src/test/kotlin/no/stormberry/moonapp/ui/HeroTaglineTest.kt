package no.stormberry.moonapp.ui

import java.io.File
import no.stormberry.moonapp.ui.components.FooterText
import no.stormberry.moonapp.ui.components.HERO_TAGLINE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's hero tagline is the web hero's, word for word. Marcos, 2026-10-23: "keep short
 * tagline, consistent with web". The page is the source of truth, so this reads index.html and
 * fails the moment either side is edited alone.
 */
class HeroTaglineTest {

    private fun indexHtml(): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "index.html") }
            .firstOrNull { it.exists() }

    @Test fun theHeroTaglineIsTheWebHeroTaglineWordForWord() {
        val html = indexHtml()
        assertTrue("index.html not found above the working directory", html != null)
        val match = Regex("""<p class="tagline">(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
            .find(html!!.readText())
        assertTrue("no <p class=\"tagline\"> in index.html", match != null)
        val fromPage = match!!.groupValues[1].replace(Regex("""<br\s*/?>"""), "\n").trim()
        assertEquals(fromPage, HERO_TAGLINE)
    }

    private fun footerLine(cls: String): String {
        val html = indexHtml()
        assertTrue("index.html not found above the working directory", html != null)
        val match = Regex("""<p class="$cls">(.*?)</p>""", RegexOption.DOT_MATCHES_ALL).find(html!!.readText())
        assertTrue("no <p class=\"$cls\"> in index.html", match != null)
        return match!!.groupValues[1].replace(Regex("""<[^>]+>"""), "").replace("&nbsp;", " ").trim()
    }

    @Test fun theSharedFooterLinesAreTheWebFooterLinesWordForWord() {
        assertEquals(FooterText.TAGLINE, footerLine("footer-tagline"))
        assertEquals(FooterText.PRIVACY_FIRST, footerLine("footer-privacy"))
        assertEquals(FooterText.CITY_LIST_DATED, footerLine("footer-data"))
        assertEquals(FooterText.AI, footerLine("footer-ai"))
    }

    // Marcos, 2026-10-25 (option B): two lines are true differently on the web, which has no alarms,
    // no permissions and no device to disconnect. The app keeps its own wording; the page has its own.
    @Test fun theTwoWebFooterLinesAreTheApprovedWebWordingAndTheAppKeepsItsOwn() {
        assertEquals("Your places never leave this browser.", footerLine("footer-offline"))
        assertEquals(
            "No cookies. No tracking. Every calculation runs in your browser.",
            footerLine("footer-permissions"),
        )
        assertNotEquals(FooterText.OFFLINE, footerLine("footer-offline"))
        assertNotEquals(FooterText.PERMISSIONS, footerLine("footer-permissions"))
        assertEquals("Your places and alarms never leave this device. Disconnect and try it.", FooterText.OFFLINE)
        assertEquals("Alarm permissions only. No internet. No tracking.", FooterText.PERMISSIONS)
    }
}
