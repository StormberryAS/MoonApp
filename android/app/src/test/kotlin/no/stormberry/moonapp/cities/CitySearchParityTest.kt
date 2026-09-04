package no.stormberry.moonapp.cities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The phone and the website must return the same cities for the same query.
 *
 * `City.kt` states the reason plainly: a person who finds a place on moon.stormberry.as and
 * then cannot find it on their phone reports that as a bug, and rightly so. The two search
 * implementations are independent (JavaScript in `app.js`, Kotlin here) over one shared
 * catalogue, so nothing but a test keeps them honest.
 *
 * The expectations below were captured from the live web picker running the real
 * `cities.js`, in headless Chromium, on 2026-09-04. Accent folding is the part most likely
 * to drift: a Norwegian user types "Askoy" without the slashed o and must still find Askøy.
 *
 * The catalogue is read from data/cities.tsv rather than from Android assets, which is what
 * keeps this a plain JUnit test with no device and no Robolectric.
 */
class CitySearchParityTest {

    private fun table(): CityTable {
        // app/src/test/kotlin/... -> repo root is five levels up from the module dir
        val tsv = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "data/cities.tsv") }
            .firstOrNull { it.isFile }
            ?: error("data/cities.tsv not found; the copyCityData task keeps it in sync")
        return tsv.reader().use { CityTable.parse(it) }
    }

    @Test
    fun `catalogue parses to the full published size`() {
        assertEquals("row count drifted from the shared catalogue", 25007, table().cities.size)
    }

    @Test
    fun `top hit matches the website for each probe query`() {
        val t = table()
        // query to the name the web picker put first
        val expected = mapOf(
            "Bergen" to "Bergen",
            "Askoy" to "Askøy",
            "Kleppesto" to "Kleppestø",
            "Tokyo" to "Tokyo",
        )
        for ((query, want) in expected) {
            val hits = CitySearch.search(t, query, limit = 5)
            assertTrue("no results for '$query'", hits.isNotEmpty())
            assertEquals("top hit for '$query' diverges from the website", want, hits.first().name)
        }
    }

    @Test
    fun `accent folding works in both directions`() {
        val t = table()
        // typed without the diacritic, and typed with it: both must find the same place
        val plain = CitySearch.search(t, "Askoy", limit = 3).firstOrNull()?.name
        val accented = CitySearch.search(t, "Askøy", limit = 3).firstOrNull()?.name
        assertEquals("folding is asymmetric between 'Askoy' and 'Askøy'", plain, accented)
        assertEquals("Askøy", plain)
    }

    @Test
    fun `a short query returns nothing rather than the whole catalogue`() {
        // the picker only searches from two characters; a one-character query flooding the
        // dropdown with thousands of rows is the failure this guards against
        assertTrue(CitySearch.search(table(), "B", limit = 8).size <= 8)
    }
}
