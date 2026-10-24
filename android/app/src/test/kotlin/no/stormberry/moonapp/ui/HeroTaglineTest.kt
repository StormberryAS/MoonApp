package no.stormberry.moonapp.ui

import java.io.File
import no.stormberry.moonapp.ui.components.HERO_TAGLINE
import org.junit.Assert.assertEquals
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
}
